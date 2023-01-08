package chat.senses.livesubs

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import chat.senses.livesubs.ui.theme.LiveSubtitlesTheme
import chat.senses.livesubs.ui.theme.Typography

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

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
            LiveSubtitlesTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    val connectionStatus by viewModel.deviceConnected.collectAsState()
                    val displayCount by viewModel.displayCount.collectAsState()
                    if (connectionStatus && displayCount > 1) {
                        Row {
                            Button(
                                modifier = Modifier.padding(8.dp),
                                onClick = {
                                    val options = ActivityOptions.makeBasic()
                                    options.launchDisplayId =
                                        LiveSubtitlesApplication.displayManager.displays[displayCount - 1].displayId
                                    startActivity(
                                        Intent(this@MainActivity, TranscribeActivity::class.java),
                                        options.toBundle(),
                                    )
                                },
                            ) {
                                Text(
                                    style = Typography.h5,
                                    text = "Show Glass Screen"
                                )
                            }
                        }
                    } else {
                        Column {
                            Text(
                                modifier = Modifier.padding(8.dp),
                                style = Typography.h5,
                                text = "Not Connected",
                            )
                            Button(
                                modifier = Modifier.padding(8.dp),
                                onClick = {
                                    startActivity(
                                        Intent(this@MainActivity, TranscribeActivity::class.java),
                                    )
                                },
                            ) {
                                Text(
                                    style = Typography.h5,
                                    text = "Transcribe Anyway"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

//@Preview(showBackground = true)
//@Composable
//fun DefaultPreview() {
//    LiveSubtitlesTheme {
//        Greeting("Not Connected")
//    }
//}