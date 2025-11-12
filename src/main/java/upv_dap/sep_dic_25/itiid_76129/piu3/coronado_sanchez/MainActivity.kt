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

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationViewModel = LocationViewModel()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startLocationUpdates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar Mapbox (reemplaza con tu token)
        Mapbox.getInstance(this, "TU_MAPBOX_TOKEN_AQUI")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LocalizameApp(
                        viewModel = locationViewModel,
                        onRequestPermissions = { requestPermissions() },
                        onStartTracking = { phoneNumber ->
                            startLocationUpdates()
                            locationViewModel.startTracking(phoneNumber, this)
                        },
                        onStopTracking = { locationViewModel.stopTracking() }
                    )
                }
            }
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.SEND_SMS
            )
        )
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                10000L
            ).build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        locationViewModel.updateLocation(
                            location.latitude,
                            location.longitude
                        )
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalizameApp(
    viewModel: LocationViewModel,
    onRequestPermissions: () -> Unit,
    onStartTracking: (String) -> Unit,
    onStopTracking: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Z_U3_CORONADO_SANCHEZ_ANGEL_GABRIEL") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, "Configuración")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (uiState.currentLocation != null) {
                    MapboxMapView(
                        latitude = uiState.currentLocation!!.latitude,
                        longitude = uiState.currentLocation!!.longitude,
                        isTracking = uiState.isTracking
                    )
                } else {
                    EmptyMapPlaceholder()
                }
            }

            ControlPanel(
                isTracking = uiState.isTracking,
                lastUpdate = uiState.lastUpdate,
                messagesSent = uiState.messagesSent,
                onStartTracking = {
                    if (uiState.phoneNumber.isNotEmpty()) {
                        onRequestPermissions()
                        onStartTracking(uiState.phoneNumber)
                    }
                },
                onStopTracking = onStopTracking
            )
        }

        if (showSettings) {
            SettingsDialog(
                phoneNumber = uiState.phoneNumber,
                updateInterval = uiState.updateInterval,
                onPhoneNumberChange = { viewModel.updatePhoneNumber(it) },
                onIntervalChange = { viewModel.updateInterval(it) },
                onDismiss = { showSettings = false }
            )
        }
    }
}

@Composable
fun MapboxMapView(
    latitude: Double,
    longitude: Double,
    isTracking: Boolean
) {
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapboxMap by remember { mutableStateOf<MapboxMap?>(null) }

    LaunchedEffect(latitude, longitude) {
        mapboxMap?.let { map ->
            val position = CameraPosition.Builder()
                .target(LatLng(latitude, longitude))
                .zoom(15.0)
                .build()
            map.animateCamera(
                com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newCameraPosition(position),
                1000
            )
        }
    }

    AndroidView(
        factory = { context ->
            MapView(context).apply {
                mapView = this
                onCreate(null)
                getMapAsync { map ->
                    mapboxMap = map
                    map.setStyle(Style.MAPBOX_STREETS) { style ->
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(latitude, longitude))
                            .zoom(15.0)
                            .build()
                    }
                }
            }
        },
        update = { view ->
            view.onResume()
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun EmptyMapPlaceholder() {
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

@Composable
fun ControlPanel(
    isTracking: Boolean,
    lastUpdate: String,
    messagesSent: Int,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit
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
                        if (isTracking) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                        contentDescription = null,
                        tint = if (isTracking)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (isTracking) "Rastreando" else "Inactivo",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (isTracking) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Default.Message,
                    label = "Mensajes",
                    value = messagesSent.toString()
                )
                StatItem(
                    icon = Icons.Default.Schedule,
                    label = "Última actualización",
                    value = lastUpdate
                )
            }

            Button(
                onClick = if (isTracking) onStopTracking else onStartTracking,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTracking)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isTracking) "Detener Rastreo" else "Iniciar Rastreo")
            }
        }
    }
}

@Composable
fun StatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    phoneNumber: String,
    updateInterval: Int,
    onPhoneNumberChange: (String) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss
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

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = onPhoneNumberChange,
                    label = { Text("Número de teléfono") },
                    placeholder = { Text("+521234567890") },
                    leadingIcon = { Icon(Icons.Default.Phone, null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Text(
                        "Intervalo: $updateInterval segundos",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = updateInterval.toFloat(),
                        onValueChange = { onIntervalChange(it.toInt()) },
                        valueRange = 10f..300f,
                        steps = 28
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}