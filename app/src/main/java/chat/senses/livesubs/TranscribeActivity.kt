package chat.senses.livesubs

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import chat.senses.livesubs.ui.theme.LiveSubtitlesTheme
import chat.senses.livesubs.ui.theme.Typography

class TranscribeActivity : ComponentActivity() {
    private val viewModel: TranscribeViewModel by viewModels()

    var audioRecordGranted = false

    val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                audioRecordGranted = true
                // Permission is granted. Continue the action or workflow in your
                // app.
            } else {
                // Explain to the user that the feature is unavailable because the
                // feature requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when {
            ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // You can use the API that requires the permission.
                audioRecordGranted = true
            }
            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestPermissionLauncher.launch(
                    android.Manifest.permission.RECORD_AUDIO)
            }
        }

        setContent {
            val speaking by viewModel.isSpeaking.collectAsState()
            LiveSubtitlesTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            modifier = Modifier.padding(20.dp),
                            style = Typography.h2,
                            textAlign = TextAlign.Center,
                            text = "${speaking}",
                        )
                    }
                }
            }
        }
    }
}
