package chat.senses.livesubs

import android.app.Application
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.util.Log

class LiveSubtitlesApplication : Application() {
    val TAG = "LiveSubtitlesApplication"

    companion object {
        lateinit var INSTANCE: LiveSubtitlesApplication
        lateinit var appContext: Context
        lateinit var displayManager: DisplayManager
        lateinit var audioManager: AudioManager
    }

    /**
     * On create
     * init application
     */
    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        appContext = applicationContext
        displayManager = applicationContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.i(TAG, "${audioManager.microphones.map { 
            it.description
        }}")
    }
}
