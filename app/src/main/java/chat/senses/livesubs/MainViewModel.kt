package chat.senses.livesubs

import android.hardware.usb.UsbDevice
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rokid.axr.phone.glassdevice.RKGlassDevice
import com.rokid.axr.phone.glassdevice.callback.OnGlassDeviceConnectListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    val TAG = "MainViewModel"
    var deviceType: DeviceType? = null

    //checkout deviceType when device is changed
    var device: UsbDevice? = null
        set(value) {
            field = value
            val name = value?.productName
            deviceType = if (name?.contains("air", true) == true) {//Air Glass
                if (name.contains("pro", true)) {//Air Pro Glass
                    if (name.contains("+", true)) {//Air Pro + Glass
                        DeviceType.AirProPlus
                    } else {
                        DeviceType.AirPro
                    }
                } else {
                    DeviceType.Air
                }
            } else if (name?.contains("Kenobi", true) == true) {//Rokid Glass2 glass
                DeviceType.Glass2
            } else {
                null
            }
        }

    val deviceConnected = MutableStateFlow(false)

    private val glassConnectionFlow: Flow<Boolean> = callbackFlow {
        val connectListener: OnGlassDeviceConnectListener by lazy {
            object : OnGlassDeviceConnectListener {
                override fun onGlassDeviceConnected(p0: UsbDevice?) {
                    device = p0
                    trySendBlocking(true)
                }

                override fun onGlassDeviceDisconnected() {
                    trySendBlocking(false)
                    device = null
                }
            }
        }

        RKGlassDevice.getInstance().init(connectListener)
        awaitClose { RKGlassDevice.getInstance().deInit() }
    }

    val displayCount = MutableStateFlow(LiveSubtitlesApplication.displayManager.displays.size)

    init {
        connectGlass()
    }

    private fun connectGlass() {
        viewModelScope.launch {
            glassConnectionFlow.collect {
                deviceConnected.value = it
//                Log.i(TAG, "${LiveSubtitlesApplication.displayManager.displays.size}")
                displayCount.value = LiveSubtitlesApplication.displayManager.displays.size
            }
        }
    }
}
