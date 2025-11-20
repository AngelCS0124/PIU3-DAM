package upv_dap.sep_dic_25.itiid_76129.piu3.coronado_sanchez

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.telephony.SmsManager
import android.widget.Toast
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

        // Validar número de teléfono
        val numeroLimpio = numeroTelefono.replace(Regex("[\\s\\-()]+"), "")
        if (numeroLimpio.length < 10) {
            Toast.makeText(contexto, "❌ Número de teléfono inválido", Toast.LENGTH_LONG).show()
            return
        }

        // Registrar receptor de SMS
        try {
            registrarReceptorSMS(contexto)
        } catch (e: Exception) {
            android.util.Log.e("SMS_ERROR", "Error al registrar receptor: ${e.message}")
        }

        _estadoInterfaz.value = _estadoInterfaz.value.copy(
            estaRastreando = true,
            numeroTelefono = numeroTelefono
        )

        trabajoRastreo = viewModelScope.launch {
            while (_estadoInterfaz.value.estaRastreando) {
                _estadoInterfaz.value.ubicacionActual?.let { ubicacion ->
                    enviarUbicacionPorSMS(numeroLimpio, ubicacion)
                }
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

        // Se desregistra el receptor de SMS
        try {
            contexto?.unregisterReceiver(receptorEnvioSMS)
        } catch (e: Exception) {
            android.util.Log.e("SMS_ERROR", "Error al desregistrar receptor: ${e.message}")
        }
    }

    /**
     * Aquí se registra el receptor para confirmar el estado del envío de SMS
     */
    private fun registrarReceptorSMS(contexto: Context) {
        val filtroEnvio = IntentFilter("SMS_ENVIADO")
        val filtroEntrega = IntentFilter("SMS_ENTREGADO")

        contexto.registerReceiver(receptorEnvioSMS, filtroEnvio, Context.RECEIVER_NOT_EXPORTED)
        contexto.registerReceiver(receptorEntregaSMS, filtroEntrega, Context.RECEIVER_NOT_EXPORTED)
    }

    /**
     * Receptor que confirma si el SMS fue enviado exitosamente
     */
    private val receptorEnvioSMS = object : BroadcastReceiver() {
        override fun onReceive(contexto: Context?, intent: Intent?) {
            when (resultCode) {
                android.app.Activity.RESULT_OK -> {
                    android.util.Log.d("SMS_CONFIRMADO", "✅ SMS enviado exitosamente")
                    contexto?.let {
                        Toast.makeText(it, "✅ SMS enviado y confirmado", Toast.LENGTH_SHORT).show()
                    }
                }
                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                    val error = "❌ Error genérico al enviar SMS"
                    android.util.Log.e("SMS_ERROR", error)
                    contexto?.let {
                        Toast.makeText(it, error, Toast.LENGTH_LONG).show()
                    }
                }
                SmsManager.RESULT_ERROR_NO_SERVICE -> {
                    val error = "❌ Sin servicio de red celular"
                    android.util.Log.e("SMS_ERROR", error)
                    contexto?.let {
                        Toast.makeText(it, error, Toast.LENGTH_LONG).show()
                    }
                }
                SmsManager.RESULT_ERROR_NULL_PDU -> {
                    val error = "❌ Error PDU nulo"
                    android.util.Log.e("SMS_ERROR", error)
                    contexto?.let {
                        Toast.makeText(it, error, Toast.LENGTH_LONG).show()
                    }
                }
                SmsManager.RESULT_ERROR_RADIO_OFF -> {
                    val error = "❌ Radio del teléfono apagada (Modo avión?)"
                    android.util.Log.e("SMS_ERROR", error)
                    contexto?.let {
                        Toast.makeText(it, error, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private val receptorEntregaSMS = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (resultCode) {
                android.app.Activity.RESULT_OK -> {
                    android.util.Log.d("SMS_ENTREGADO", "✅ SMS entregado al destinatario")
                    contexto?.let {
                        Toast.makeText(it, "✅ SMS entregado", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    android.util.Log.e("SMS_ENTREGADO", "❌ SMS no entregado")
                }
            }
        }
    }

    /**
     * Aquí se lleva a cabo el envío de la ubicación GPS mediante SMS
     */
    private fun enviarUbicacionPorSMS(numeroTelefono: String, ubicacion: DatosUbicacion) {
        try {
            // Validaciones adicionales
            if (contexto == null) {
                android.util.Log.e("SMS_ERROR", "Contexto nulo")
                return
            }

            // Verificar si hay servicio de SMS disponible
            val gestorSms = SmsManager.getDefault()
            val subsInfo = gestorSms.subscriptionId
            if (subsInfo == -1) {
                val error = "❌ No hay SIM card disponible"
                android.util.Log.e("SMS_ERROR", error)
                contexto?.let {
                    Toast.makeText(it, error, Toast.LENGTH_LONG).show()
                }
                return
            }

            // Construir mensaje más simple para pruebas
            val mensaje = "Localizame App - Ubicacion: ${ubicacion.latitud}, ${ubicacion.longitud}" +
                    ", Mapa: https://maps.google.com/?q=${ubicacion.latitud},${ubicacion.longitud}"

            android.util.Log.d("SMS_DEBUG", "Enviando a: $numeroTelefono")
            android.util.Log.d("SMS_DEBUG", "Mensaje: $mensaje")

            // Intent para confirmación de envío
            val intentEnvio = Intent("SMS_ENVIADO")
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                contexto!!,
                0,
                intentEnvio,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Intent para confirmación de entrega (opcional)
            val intentEntrega = Intent("SMS_ENTREGADO")
            val pendingIntentEntrega = android.app.PendingIntent.getBroadcast(
                contexto!!,
                0,
                intentEntrega,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Enviar SMS
            gestorSms.sendTextMessage(
                numeroTelefono,
                null,
                mensaje,
                pendingIntent,
                pendingIntentEntrega
            )

            android.util.Log.d("SMS_DEBUG", "SMS enviado exitosamente")

            // Actualizar estado
            _estadoInterfaz.value = _estadoInterfaz.value.copy(
                mensajesEnviados = _estadoInterfaz.value.mensajesEnviados + 1,
                ultimaActualizacion = formatoFecha.format(Date())
            )

        } catch (e: SecurityException) {
            val error = "❌ Permiso SMS denegado: ${e.message}"
            android.util.Log.e("SMS_ERROR", error, e)
            contexto?.let {
                Toast.makeText(it, error, Toast.LENGTH_LONG).show()
            }
        } catch (e: IllegalArgumentException) {
            val error = "❌ Número inválido: ${e.message}"
            android.util.Log.e("SMS_ERROR", error, e)
            contexto?.let {
                Toast.makeText(it, error, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            val error = "❌ Error: ${e.javaClass.simpleName} - ${e.message}"
            android.util.Log.e("SMS_ERROR", error, e)
            contexto?.let {
                Toast.makeText(it, error, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Se limpia el ViewModel cuando ya no es necesario
     */
    override fun onCleared() {
        super.onCleared()
        detenerRastreo()
    }

    /**
     * Guarda el SMS enviado en la base de datos del sistema para que aparezca en la app de mensajes
     */
    private fun guardarSMSEnviado(numeroTelefono: String, mensaje: String) {
        try {
            val valores = ContentValues().apply {
                put("address", numeroTelefono)
                put("body", mensaje)
                put("date", System.currentTimeMillis())
                put("type", 2) // 2 = SMS enviado (SENT)
                put("read", 1)
            }

            contexto?.contentResolver?.insert(
                Uri.parse("content://sms/sent"),
                valores
            )

            android.util.Log.d("SMS_DEBUG", "✅ SMS guardado en la app de mensajes")
        } catch (e: Exception) {
            android.util.Log.e("SMS_ERROR", "Error al guardar SMS en app de mensajes: ${e.message}", e)
        }
    }
}