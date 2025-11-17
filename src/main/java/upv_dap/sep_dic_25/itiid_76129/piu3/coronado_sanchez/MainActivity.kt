package upv_dap.sep_dic_25.itiid_76129.piu3.coronado_sanchez

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions
import upv_dap.sep_dic_25.itiid_76129.piu3.coronado_sanchez.ui.theme.AppTheme
import com.google.android.gms.location.*

/**
 * Actividad principal donde se inicializa la aplicación de rastreo GPS
 */
class MainActivity : ComponentActivity() {
    // Se inicializa el cliente de ubicación fusionado para obtener coordenadas GPS
    private lateinit var clienteUbicacionFusionado: FusedLocationProviderClient

    // Se crea el ViewModel que manejará el estado de la ubicación
    private val modeloVistaUbicacion = ModeloVistaUbicacion()

    // Aquí se lleva a cabo el registro del lanzador de permisos
    private val lanzadorPermisos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permisos ->
        val todosOtorgados = permisos.values.all { it }
        if (todosOtorgados) {
            // Se inician las actualizaciones de ubicación cuando los permisos son otorgados
            iniciarActualizacionesUbicacion()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Se inicializa Mapbox con el token de acceso público
        Mapbox.getInstance(this, "pk.eyJ1IjoiYW5nZWxjb3JvbmFkbyIsImEiOiJjbTNydnRsbTIwYW9pMmxxdTA3cDhqeDNsIn0.8vOXQF3aZLZOCN-kfFVVcw")

        // Se obtiene la instancia del cliente de ubicación
        clienteUbicacionFusionado = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Aquí se lleva a cabo la construcción de la interfaz principal
                    AplicacionLocalizame(
                        modeloVista = modeloVistaUbicacion,
                        alSolicitarPermisos = { solicitarPermisos() },
                        alIniciarRastreo = { numeroTelefono ->
                            iniciarActualizacionesUbicacion()
                            modeloVistaUbicacion.iniciarRastreo(numeroTelefono, this)
                        },
                        alDetenerRastreo = { modeloVistaUbicacion.detenerRastreo() }
                    )
                }
            }
        }
    }

    /**
     * Se solicitan los permisos necesarios para ubicación y SMS
     */
    private fun solicitarPermisos() {
        lanzadorPermisos.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_PHONE_STATE
            )
        )
    }

    /**
     * Aquí se lleva a cabo el inicio de las actualizaciones continuas de ubicación GPS
     */
    private fun iniciarActualizacionesUbicacion() {
        // Se verifica que el permiso de ubicación esté otorgado
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Se configura la solicitud de ubicación con alta precisión cada 10 segundos
            val solicitudUbicacion = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                10000L
            ).build()

            // Se define el callback que recibe las actualizaciones de ubicación
            val llamadaRetornoUbicacion = object : LocationCallback() {
                override fun onLocationResult(resultado: LocationResult) {
                    resultado.lastLocation?.let { ubicacion ->
                        // Se actualiza la ubicación en el ViewModel
                        modeloVistaUbicacion.actualizarUbicacion(
                            ubicacion.latitude,
                            ubicacion.longitude
                        )
                    }
                }
            }

            // Se inician las actualizaciones de ubicación
            clienteUbicacionFusionado.requestLocationUpdates(
                solicitudUbicacion,
                llamadaRetornoUbicacion,
                mainLooper
            )
        }
    }
}

/**
 * Composable principal que construye la interfaz de la aplicación
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AplicacionLocalizame(
    modeloVista: ModeloVistaUbicacion,
    alSolicitarPermisos: () -> Unit,
    alIniciarRastreo: (String) -> Unit,
    alDetenerRastreo: () -> Unit
) {
    // Se observa el estado de la interfaz desde el ViewModel
    val estadoInterfaz by modeloVista.estadoInterfaz.collectAsState()
    var mostrarConfiguracion by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Z_U3_CORONADO_SANCHEZ_ANGEL_GABRIEL") },
                actions = {
                    IconButton(onClick = { mostrarConfiguracion = true }) {
                        Icon(Icons.Default.Settings, "Configuración")
                    }
                }
            )
        }
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
        ) {
            // Aquí se muestra el mapa de Mapbox o un marcador de espera
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (estadoInterfaz.ubicacionActual != null) {
                    VistaMapaMapbox(
                        latitud = estadoInterfaz.ubicacionActual!!.latitud,
                        longitud = estadoInterfaz.ubicacionActual!!.longitud,
                        estaRastreando = estadoInterfaz.estaRastreando
                    )
                } else {
                    MarcadorMapaVacio()
                }
            }

            // Se muestra el panel de control en la parte inferior
            PanelControl(
                estaRastreando = estadoInterfaz.estaRastreando,
                ultimaActualizacion = estadoInterfaz.ultimaActualizacion,
                mensajesEnviados = estadoInterfaz.mensajesEnviados,
                alIniciarRastreo = {
                    if (estadoInterfaz.numeroTelefono.isNotEmpty()) {
                        alSolicitarPermisos()
                        alIniciarRastreo(estadoInterfaz.numeroTelefono)
                    }
                },
                alDetenerRastreo = alDetenerRastreo
            )
        }

        // Aquí se muestra el diálogo de configuración cuando es solicitado
        if (mostrarConfiguracion) {
            DialogoConfiguracion(
                numeroTelefono = estadoInterfaz.numeroTelefono,
                intervaloActualizacion = estadoInterfaz.intervaloActualizacion,
                alCambiarNumeroTelefono = { modeloVista.actualizarNumeroTelefono(it) },
                alCambiarIntervalo = { modeloVista.actualizarIntervalo(it) },
                alCerrar = { mostrarConfiguracion = false }
            )
        }
    }
}

/**
 * Composable que renderiza el mapa de Mapbox con la ubicación actual
 */
@Composable
fun VistaMapaMapbox(
    latitud: Double,
    longitud: Double,
    estaRastreando: Boolean
) {
    // Se mantiene la referencia del MapView y MapboxMap
    var vistaMapa by remember { mutableStateOf<MapView?>(null) }
    var mapaMapbox by remember { mutableStateOf<MapboxMap?>(null) }

    // Aquí se lleva a cabo la animación de la cámara cuando cambia la ubicación
    LaunchedEffect(latitud, longitud) {
        mapaMapbox?.let { mapa ->
            val posicion = CameraPosition.Builder()
                .target(LatLng(latitud, longitud))
                .zoom(15.0)
                .build()
            mapa.animateCamera(
                com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newCameraPosition(posicion),
                1000
            )
        }
    }

    // Se inicializa el MapView usando AndroidView
    AndroidView(
        factory = { contexto ->
            MapView(contexto).apply {
                vistaMapa = this
                onCreate(null)
                getMapAsync { mapa ->
                    mapaMapbox = mapa
                    // Se configura el estilo del mapa
                    mapa.setStyle(Style.MAPBOX_STREETS) { estilo ->
                        mapa.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(latitud, longitud))
                            .zoom(15.0)
                            .build()
                    }
                }
            }
        },
        update = { vista ->
            vista.onResume()
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Se muestra un marcador cuando no hay ubicación disponible
 */
@Composable
fun MarcadorMapaVacio() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.LocationOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                "Esperando ubicación...",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Activa el rastreo para comenzar",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Panel de control donde se muestra el estado del rastreo y se controla el inicio/detención
 */
@Composable
fun PanelControl(
    estaRastreando: Boolean,
    ultimaActualizacion: String,
    mensajesEnviados: Int,
    alIniciarRastreo: () -> Unit,
    alDetenerRastreo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Aquí se muestra el estado actual del rastreo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (estaRastreando) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                        contentDescription = null,
                        tint = if (estaRastreando)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (estaRastreando) "Rastreando" else "Inactivo",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (estaRastreando) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "ACTIVO",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            HorizontalDivider()

            // Se muestran las estadísticas de mensajes enviados y última actualización
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ElementoEstadistica(
                    icono = Icons.Default.Message,
                    etiqueta = "Mensajes",
                    valor = mensajesEnviados.toString()
                )
                ElementoEstadistica(
                    icono = Icons.Default.Schedule,
                    etiqueta = "Última actualización",
                    valor = ultimaActualizacion
                )
            }

            // Botón para iniciar o detener el rastreo
            Button(
                onClick = if (estaRastreando) alDetenerRastreo else alIniciarRastreo,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (estaRastreando)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (estaRastreando) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (estaRastreando) "Detener Rastreo" else "Iniciar Rastreo")
            }
        }
    }
}

/**
 * Elemento individual de estadística que muestra un icono, valor y etiqueta
 */
@Composable
fun ElementoEstadistica(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    etiqueta: String,
    valor: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icono,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            valor,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Diálogo de configuración donde se ingresa el número de teléfono y el intervalo de actualización
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoConfiguracion(
    numeroTelefono: String,
    intervaloActualizacion: Int,
    alCambiarNumeroTelefono: (String) -> Unit,
    alCambiarIntervalo: (Int) -> Unit,
    alCerrar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = alCerrar
    ) {
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Configuración",
                    style = MaterialTheme.typography.headlineSmall
                )

                // Campo de texto para ingresar el número de teléfono destino
                OutlinedTextField(
                    value = numeroTelefono,
                    onValueChange = alCambiarNumeroTelefono,
                    label = { Text("Número de teléfono") },
                    placeholder = { Text("+521234567890") },
                    leadingIcon = { Icon(Icons.Default.Phone, null) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Slider para ajustar el intervalo de envío de mensajes
                Column {
                    Text(
                        "Intervalo: $intervaloActualizacion segundos",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = intervaloActualizacion.toFloat(),
                        onValueChange = { alCambiarIntervalo(it.toInt()) },
                        valueRange = 10f..300f,
                        steps = 28
                    )
                }

                // Botón para guardar la configuración
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = alCerrar) {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}