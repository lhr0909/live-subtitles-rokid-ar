package chat.senses.livesubs

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.nio.FloatBuffer
import java.nio.LongBuffer

@OptIn(DelicateCoroutinesApi::class)
class TranscribeViewModel : ViewModel() {
    private val SAMPLE_RATE = 16000

    private val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
    )

    @SuppressLint("MissingPermission")
    val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_FLOAT,
        minBufferSize,
    )

    private var ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null

    private val audioFlow: Flow<Float> = flow {
        audioRecord.startRecording()
        while (true) {
//            val size = (SAMPLE_RATE / 1000 * 30) //30ms
            val size = 512
            val data = FloatArray(size)
            var floatsRead = audioRecord.read(data, 0, size, AudioRecord.READ_NON_BLOCKING)
            while (floatsRead < size) {
                floatsRead += audioRecord.read(data, floatsRead, size - floatsRead, AudioRecord.READ_NON_BLOCKING)
            }

            emit(runVAD(FloatBuffer.wrap(data), size))
        }
    }

    val isSpeaking = MutableStateFlow(false)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            val resources = LiveSubtitlesApplication.INSTANCE.resources
            val modelId = R.raw.silero_vad
            val modelBytes = resources.openRawResource(modelId).readBytes()
            ortSession = ortEnv.createSession(modelBytes)
        }

        CoroutineScope(newSingleThreadContext("VADThread")).launch {
            audioFlow.collect {
                isSpeaking.value = it > 0.5
            }
        }
    }

    private fun runVAD(inputData: FloatBuffer, size: Int): Float {
        val inputShape = longArrayOf(1, size.toLong()) // have to re-type numbers because long
        val inputTensor = OnnxTensor.createTensor(ortEnv, inputData, inputShape)
        val sampleRateTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(longArrayOf(SAMPLE_RATE.toLong())), longArrayOf(1))
        val hTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.allocate(2 * 1 * 64), longArrayOf(2, 1, 64))
        val cTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.allocate(2 * 1 * 64), longArrayOf(2, 1, 64))
        val input = mutableMapOf(
            "input" to inputTensor,
            "sr" to sampleRateTensor,
            "h" to hTensor,
            "c" to cTensor,
        )

        val output = ortSession?.run(input)

        // FIXME: not sure why I have to multiply by 8 here, the numbers are much smaller
        val prob = (output?.get(0)?.value as Array<FloatArray>)[0][0] * 8

        return prob
    }
}