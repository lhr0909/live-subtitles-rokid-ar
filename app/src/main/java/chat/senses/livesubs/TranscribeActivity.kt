package chat.senses.livesubs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import chat.senses.livesubs.ui.theme.LiveSubtitlesTheme
import chat.senses.livesubs.ui.theme.Typography

class TranscribeActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LiveSubtitlesTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Row {
                        Text(
                            modifier = Modifier.padding(20.dp),
                            style = Typography.h2,
                            textAlign = TextAlign.Center,
                            text = "ready to transcribe!",
                        )
                    }
                }
            }
        }
    }
}
