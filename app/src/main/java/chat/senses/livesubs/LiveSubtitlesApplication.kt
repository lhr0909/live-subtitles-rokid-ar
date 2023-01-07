package chat.senses.livesubs

import android.app.Application
import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log

class LiveSubtitlesApplication : Application() {
    val TAG = "LiveSubtitlesApplication"

    companion object {
        lateinit var INSTANCE: LiveSubtitlesApplication
        lateinit var appContext: Context
        lateinit var displayManager: DisplayManager
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
//        Log.i(TAG, "${displayManager.displays.size}")
    }
}
