package upv_dap.sep_dic_25.itiid_76129.piu3.coronado_sanchez

import android.content.Context
import android.telephony.SmsManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class LocationData(
    val latitude: Double,
    val longitude: Double
)

data class UiState(
    val currentLocation: LocationData? = null,
    val isTracking: Boolean = false,
    val lastUpdate: String = "Sin actualizar",
    val messagesSent: Int = 0,
    val phoneNumber: String = "",
    val updateInterval: Int = 30
)

class LocationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var trackingJob: Job? = null
    private var context: Context? = null
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun updateLocation(latitude: Double, longitude: Double) {
        _uiState.value = _uiState.value.copy(
            currentLocation = LocationData(latitude, longitude),
            lastUpdate = dateFormat.format(Date())
        )
    }

    fun updatePhoneNumber(phoneNumber: String) {
        _uiState.value = _uiState.value.copy(phoneNumber = phoneNumber)
    }

    fun updateInterval(interval: Int) {
        _uiState.value = _uiState.value.copy(updateInterval = interval)
    }

    fun startTracking(phoneNumber: String, context: Context) {
        this.context = context
        _uiState.value = _uiState.value.copy(
            isTracking = true,
            phoneNumber = phoneNumber
        )

        trackingJob = viewModelScope.launch {
            while (_uiState.value.isTracking) {
                _uiState.value.currentLocation?.let { location ->
                    sendLocationSMS(phoneNumber, location)
                }
                delay(_uiState.value.updateInterval * 1000L)
            }
        }
    }

    fun stopTracking() {
        trackingJob?.cancel()
        _uiState.value = _uiState.value.copy(isTracking = false)
    }

    private fun sendLocationSMS(phoneNumber: String, location: LocationData) {
        try {
            val smsManager = SmsManager.getDefault()
            val message = "Ubicación GPS:\nLat: ${location.latitude}\nLng: ${location.longitude}\n" +
                    "Ver en mapa: https://maps.google.com/?q=${location.latitude},${location.longitude}"

            smsManager.sendTextMessage(
                phoneNumber,
                null,
                message,
                null,
                null
            )

            _uiState.value = _uiState.value.copy(
                messagesSent = _uiState.value.messagesSent + 1,
                lastUpdate = dateFormat.format(Date())
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTracking()
    }
}