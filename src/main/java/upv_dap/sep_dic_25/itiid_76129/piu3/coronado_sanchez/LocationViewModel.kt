package upv_dap.sep_dic_25.itiid_76129.piu3.coronado_sanchez

import android.app.PendingIntent
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

        // Se registra el receptor de confirmación de SMS
        registrarReceptorSMS(contexto)

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
        val filtro = IntentFilter("SMS_ENVIADO")
        contexto.registerReceiver(receptorEnvioSMS, filtro, Context.RECEIVER_NOT_EXPORTED)
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

    /**
     * Aquí se lleva a cabo el envío de la ubicación GPS mediante SMS
     */
    private fun enviarUbicacionPorSMS(numeroTelefono: String, ubicacion: DatosUbicacion) {
        try {
            // Se limpia el número de teléfono (quita espacios, guiones, paréntesis)
            val numeroLimpio = numeroTelefono.replace(Regex("[\\s\\-()]+"), "")

            android.util.Log.d("SMS_DEBUG", "=== INICIO ENVÍO SMS ===")
            android.util.Log.d("SMS_DEBUG", "Número original: $numeroTelefono")
            android.util.Log.d("SMS_DEBUG", "Número limpio: $numeroLimpio")
            android.util.Log.d("SMS_DEBUG", "Coordenadas: ${ubicacion.latitud}, ${ubicacion.longitud}")

            // Validar que el número limpio no esté vacío
            if (numeroLimpio.isEmpty()) {
                val error = "❌ Número de teléfono vacío después de limpieza"
                android.util.Log.e("SMS_ERROR", error)
                contexto?.let {
                    Toast.makeText(it, error, Toast.LENGTH_LONG).show()
                }
                return
            }

            // Se valida que el contexto esté disponible
            if (contexto == null) {
                val error = "Error: Contexto no disponible"
                android.util.Log.e("SMS_ERROR", error)
                contexto?.let {
                    Toast.makeText(it, error, Toast.LENGTH_LONG).show()
                }
                return
            }

            // Verificar permiso en tiempo real
            if (android.content.pm.PackageManager.PERMISSION_GRANTED !=
                androidx.core.content.ContextCompat.checkSelfPermission(contexto!!, android.Manifest.permission.SEND_SMS)) {
                val error = "❌ Permiso SEND_SMS no otorgado"
                android.util.Log.e("SMS_ERROR", error)
                contexto?.let {
                    Toast.makeText(it, error, Toast.LENGTH_LONG).show()
                }
                return
            }
            android.util.Log.d("SMS_DEBUG", "✅ Permiso SEND_SMS verificado")

            // Se construye el mensaje con las coordenadas y enlace de Google Maps
            val mensaje = "Ubicación GPS:\nLat: ${ubicacion.latitud}\nLng: ${ubicacion.longitud}\n" +
                    "Ver en mapa: https://maps.google.com/?q=${ubicacion.latitud},${ubicacion.longitud}"

            android.util.Log.d("SMS_DEBUG", "Mensaje construido: $mensaje")
            android.util.Log.d("SMS_DEBUG", "Longitud del mensaje: ${mensaje.length} caracteres")

            // Se obtiene el gestor de SMS del sistema
            val gestorSms = try {
                SmsManager.getDefault()
            } catch (e: Exception) {
                android.util.Log.e("SMS_ERROR", "Error al obtener SmsManager: ${e.message}")
                contexto?.let {
                    Toast.makeText(it, "❌ Error al obtener gestor SMS", Toast.LENGTH_LONG).show()
                }
                return
            }

            android.util.Log.d("SMS_DEBUG", "✅ SmsManager obtenido correctamente")

            // Si el mensaje es muy largo, se divide en partes
            if (mensaje.length > 160) {
                val partes = gestorSms.divideMessage(mensaje)
                android.util.Log.d("SMS_DEBUG", "Mensaje dividido en ${partes.size} partes")

                gestorSms.sendMultipartTextMessage(
                    numeroLimpio,
                    null,
                    partes,
                    null,
                    null
                )
                android.util.Log.d("SMS_DEBUG", "sendMultipartTextMessage ejecutado")
            } else {
                android.util.Log.d("SMS_DEBUG", "Enviando mensaje simple...")
                gestorSms.sendTextMessage(
                    numeroLimpio,
                    null,
                    mensaje,
                    null,
                    null
                )
                android.util.Log.d("SMS_DEBUG", "sendTextMessage ejecutado")
            }

            android.util.Log.d("SMS_DEBUG", "=== FUNCIÓN SEND COMPLETADA SIN EXCEPCIONES ===")

            contexto?.let {
                Toast.makeText(it, "📤 SMS enviado a $numeroLimpio", Toast.LENGTH_SHORT).show()
            }

            // Se actualiza el contador de mensajes enviados
            _estadoInterfaz.value = _estadoInterfaz.value.copy(
                mensajesEnviados = _estadoInterfaz.value.mensajesEnviados + 1,
                ultimaActualizacion = formatoFecha.format(Date())
            )

        } catch (e: SecurityException) {
            val error = "❌ SecurityException: Permiso SMS denegado - ${e.message}"
            android.util.Log.e("SMS_ERROR", error, e)
            contexto?.let {
                Toast.makeText(it, error, Toast.LENGTH_LONG).show()
            }
        } catch (e: IllegalArgumentException) {
            val error = "❌ IllegalArgumentException: Número inválido - ${e.message}"
            android.util.Log.e("SMS_ERROR", error, e)
            contexto?.let {
                Toast.makeText(it, error, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            val error = "❌ Exception: ${e.javaClass.simpleName} - ${e.message}"
            android.util.Log.e("SMS_ERROR", error, e)
            e.printStackTrace()
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
}