package chat.senses.livesubs

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.annotation.SuppressLint
import android.media.*
import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.gson.annotations.SerializedName
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.pow

data class TranscribeInput(
    val audio: String,
)

data class TranscriptionResult(
    @SerializedName("transcription_id") val transcriptionId: String,
    val transcription: String?,
)

@OptIn(DelicateCoroutinesApi::class)
class TranscribeViewModel : ViewModel() {
    private val SAMPLE_RATE = 16000
    private val BUFFER_BEFORE_AFTER = 10
    private val GAIN_DB = 10f.pow(8f / 20f)

    private val minBufferSizeIn = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_FLOAT,
    )

    private val minBufferSizeOut = AudioTrack.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_FLOAT,
    )

    @SuppressLint("MissingPermission")
    val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_FLOAT,
        minBufferSizeIn,
    )

    val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(minBufferSizeOut)
        .build()

    private var ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null

    val speakingProbability = MutableStateFlow(0f)

    private val audioFlow: Flow<ByteArray> = flow {
        audioRecord.startRecording()
//        audioTrack.play()
//        audioTrack.setVolume(20f)
        var consecutiveSilence = 0
        var hasVoice = false
        val buffers = ArrayList<FloatBuffer>()
        while (true) {
            val size = (SAMPLE_RATE / 1000 * 50) // 50ms
            val data = FloatArray(size)
            var floatsRead = audioRecord.read(data, 0, size, AudioRecord.READ_NON_BLOCKING)
            while (floatsRead < size) {
                floatsRead += audioRecord.read(data, floatsRead, size - floatsRead, AudioRecord.READ_NON_BLOCKING)
            }
//            audioTrack.write(data, 0, floatsRead, AudioTrack.WRITE_NON_BLOCKING)
            // add a gain
            val buffer = FloatBuffer.wrap(data.map { it ->
                if (kotlin.math.abs(it * GAIN_DB) > 1f) {
                    1f * (it / kotlin.math.abs(it))
                } else {
                    it * GAIN_DB
                }
            }.toFloatArray())

            val prob = runVAD(buffer, size)
            speakingProbability.value = prob

            if (prob > 0.15f) {
                hasVoice = true
                consecutiveSilence = 0
                buffers.add(buffer)
            } else {
                if (hasVoice) {
                    consecutiveSilence += 1
                    buffers.add(buffer)
                } else {
                    buffers.add(buffer)
                    if (buffers.size > BUFFER_BEFORE_AFTER) {
                        buffers.removeAt(0)
                    }
                }
                if (consecutiveSilence > BUFFER_BEFORE_AFTER && buffers.size > BUFFER_BEFORE_AFTER * 2) {
                    val byteBuffer = ByteBuffer.allocate(
                        buffers.fold(0) { acc, buf ->
                            acc + buf.capacity() * 4
                        }
                    )
                    val byteBufferFloatView = byteBuffer.asFloatBuffer()
                    for (buf in buffers) {
                        byteBufferFloatView.put(buf)
                    }
                    emit(byteBuffer.array())
                    consecutiveSilence = 0
                    hasVoice = false
                    buffers.clear()
                }
            }
        }
    }

    val transcription = MutableStateFlow("speak now")
    val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            gson()
        }
        install(HttpTimeout) {
            socketTimeoutMillis = 600 * 1000
            connectTimeoutMillis = 600 * 1000
            requestTimeoutMillis = 600 * 1000 // 10 mins
        }
    }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            val resources = LiveSubtitlesApplication.INSTANCE.resources
            val modelId = R.raw.silero_vad
            val modelBytes = resources.openRawResource(modelId).readBytes()
            ortSession = ortEnv.createSession(modelBytes)
        }

        audioTrack.play()

        CoroutineScope(newSingleThreadContext("VADThread")).launch {
            audioFlow.collect { audio ->
                Log.i("TranscribeViewModel", "${audio.size}")
                getTranscription(audio)
            }
        }
    }

    private fun getTranscription(audio: ByteArray) {
        val audioBase64 = String(Base64.getEncoder().encode(audio))
        CoroutineScope(newFixedThreadPoolContext(10, "RequestThread")).launch {
            val transcribeResponse = client.request("http://192.168.31.171:6666/transcribe") {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(TranscribeInput(audioBase64))
            }
            val responseData: TranscriptionResult = transcribeResponse.body()
            val transcriptionResponse = client.request("http://192.168.31.171:6666/transcription/${responseData.transcriptionId}") {
                method = HttpMethod.Get
            }
            val transcriptionData: TranscriptionResult = transcriptionResponse.body()
            transcription.value = transcriptionData.transcription!!
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
        val prob = (output?.get(0)?.value as Array<FloatArray>)[0][0]

        return prob
    }
}