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

/**
 * Clase de datos que almacena las coordenadas GPS
 */
data class DatosUbicacion(
    val latitud: Double,
    val longitud: Double
)

/**
 * Estado de la interfaz que contiene toda la información del rastreo
 */
data class EstadoInterfaz(
    val ubicacionActual: DatosUbicacion? = null,
    val estaRastreando: Boolean = false,
    val ultimaActualizacion: String = "Sin actualizar",
    val mensajesEnviados: Int = 0,
    val numeroTelefono: String = "",
    val intervaloActualizacion: Int = 30
)

/**
 * ViewModel que maneja la lógica de negocio del rastreo GPS y envío de SMS
 */
class ModeloVistaUbicacion : ViewModel() {
    // Se inicializa el estado mutable de la interfaz
    private val _estadoInterfaz = MutableStateFlow(EstadoInterfaz())

    // Se expone el estado como inmutable para observación
    val estadoInterfaz: StateFlow<EstadoInterfaz> = _estadoInterfaz.asStateFlow()

    // Variable que mantiene el trabajo de rastreo activo
    private var trabajoRastreo: Job? = null

    // Se almacena el contexto para el envío de SMS
    private var contexto: Context? = null

    // Se inicializa el formato de fecha para las actualizaciones
    private val formatoFecha = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /**
     * Aquí se lleva a cabo la actualización de la ubicación GPS actual
     */
    fun actualizarUbicacion(latitud: Double, longitud: Double) {
        _estadoInterfaz.value = _estadoInterfaz.value.copy(
            ubicacionActual = DatosUbicacion(latitud, longitud),
            ultimaActualizacion = formatoFecha.format(Date())
        )
    }

    /**
     * Se actualiza el número de teléfono destino para el envío de SMS
     */
    fun actualizarNumeroTelefono(numeroTelefono: String) {
        _estadoInterfaz.value = _estadoInterfaz.value.copy(numeroTelefono = numeroTelefono)
    }

    /**
     * Se actualiza el intervalo de tiempo entre envíos de SMS
     */
    fun actualizarIntervalo(intervalo: Int) {
        _estadoInterfaz.value = _estadoInterfaz.value.copy(intervaloActualizacion = intervalo)
    }

    /**
     * Aquí se lleva a cabo el inicio del rastreo GPS y envío automático de SMS
     */
    fun iniciarRastreo(numeroTelefono: String, contexto: Context) {
        this.contexto = contexto

        // Se actualiza el estado para indicar que el rastreo está activo
        _estadoInterfaz.value = _estadoInterfaz.value.copy(
            estaRastreando = true,
            numeroTelefono = numeroTelefono
        )

        // Se inicia una corrutina que enviará SMS periódicamente
        trabajoRastreo = viewModelScope.launch {
            while (_estadoInterfaz.value.estaRastreando) {
                _estadoInterfaz.value.ubicacionActual?.let { ubicacion ->
                    // Se envía la ubicación actual por SMS
                    enviarUbicacionPorSMS(numeroTelefono, ubicacion)
                }
                // Se espera el intervalo configurado antes del siguiente envío
                delay(_estadoInterfaz.value.intervaloActualizacion * 1000L)
            }
        }
    }

    /**
     * Se detiene el rastreo GPS y el envío de mensajes
     */
    fun detenerRastreo() {
        trabajoRastreo?.cancel()
        _estadoInterfaz.value = _estadoInterfaz.value.copy(estaRastreando = false)
    }

    /**
     * Aquí se lleva a cabo el envío de la ubicación GPS mediante SMS
     */
    private fun enviarUbicacionPorSMS(numeroTelefono: String, ubicacion: DatosUbicacion) {
        try {
            // Se obtiene el gestor de SMS del sistema
            val gestorSms = SmsManager.getDefault()

            // Se construye el mensaje con las coordenadas y enlace de Google Maps
            val mensaje = "Ubicación GPS:\nLat: ${ubicacion.latitud}\nLng: ${ubicacion.longitud}\n" +
                    "Ver en mapa: https://maps.google.com/?q=${ubicacion.latitud},${ubicacion.longitud}"

            // Se envía el mensaje de texto
            gestorSms.sendTextMessage(
                numeroTelefono,
                null,
                mensaje,
                null,
                null
            )

            // Se actualiza el contador de mensajes enviados
            _estadoInterfaz.value = _estadoInterfaz.value.copy(
                mensajesEnviados = _estadoInterfaz.value.mensajesEnviados + 1,
                ultimaActualizacion = formatoFecha.format(Date())
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Se limpia el ViewModel cuando ya no es necesario
     */
    override fun onCleared() {
        super.onCleared()
        detenerRastreo()
    }
}