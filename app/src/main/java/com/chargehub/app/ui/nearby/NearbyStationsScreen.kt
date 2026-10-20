package com.chargehub.app.ui.nearby
import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.chargehub.app.data.model.ChargingStation
import com.chargehub.app.data.model.GeoPoint
import com.chargehub.app.data.payment.PaymentResult
import com.chargehub.app.ui.payment.PaymentViewModel
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.razorpay.Checkout
import java.util.Locale
import kotlinx.coroutines.flow.collect
import kotlin.math.floor

private val DEFAULT_MAP_LOCATION = LatLng(20.5937, 78.9629)
private const val LOCATION_UPDATE_INTERVAL_MILLIS = 15_000L
private const val MINIMUM_LOCATION_UPDATE_DISTANCE_METERS = 250f

/**
 * Displays nearby EV charging stations on a Google Map and in a list.
 *
 * The existing provider/repository/ViewModel flow remains unchanged. This
 * screen only adds map presentation on top of the canonical station model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyStationsScreen(
    viewModel: NearbyStationsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    paymentViewModel: PaymentViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    onShowDirections: (ChargingStation, GeoPoint?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val paymentState by paymentViewModel.state.collectAsState()
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var selectedRadiusKm by remember { mutableStateOf(10) }
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun submitLocation(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = selectedRadiusKm,
        forceRefresh: Boolean = false
    ) {
        val point = GeoPoint(latitude, longitude)
        userLocation = point
        viewModel.onLocationAvailable(point, radiusKm * 1_000, forceRefresh)
    }

    fun fetchLocation(
        radiusKm: Int = selectedRadiusKm,
        forceRefresh: Boolean = false
    ) {
        val permissionGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        locationPermissionGranted = permissionGranted

        if (!permissionGranted) {
            viewModel.onPermissionDenied()
            return
        }

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            null
        ).addOnSuccessListener { currentLocation ->
            if (currentLocation == null) {
                viewModel.onLocationUnavailable()
            } else {
                submitLocation(
                    currentLocation.latitude,
                    currentLocation.longitude,
                    radiusKm,
                    forceRefresh
                )
            }
        }.addOnFailureListener {
            viewModel.onLocationUnavailable()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationPermissionGranted = granted
        if (granted) {
            fetchLocation()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        locationPermissionGranted = granted
        if (granted) {
            fetchLocation()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    DisposableEffect(fusedLocationClient, locationPermissionGranted, selectedRadiusKm) {
        if (!locationPermissionGranted) {
            onDispose { }
        } else {
            val request = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                LOCATION_UPDATE_INTERVAL_MILLIS
            )
                .setMinUpdateDistanceMeters(MINIMUM_LOCATION_UPDATE_DISTANCE_METERS)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        submitLocation(location.latitude, location.longitude)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            onDispose { fusedLocationClient.removeLocationUpdates(callback) }
        }
    }

    LaunchedEffect(paymentViewModel, context) {
        paymentViewModel.checkoutOrders.collect { order ->
            val activity = context as? Activity
            if (activity == null) {
                paymentViewModel.reportCheckoutLaunchFailure("Payment checkout could not be opened from this screen.")
                return@collect
            }
            runCatching {
                Checkout().apply { setKeyID(requireNotNull(order.checkoutKeyId)) }.open(
                    activity,
                    org.json.JSONObject()
                        .put("name", "ChargeHub")
                        .put("description", "Charging session")
                        .put("order_id", requireNotNull(order.razorpayOrderId))
                        .put("currency", order.currency)
                        .put("amount", order.amountPaise)
                        .put("retry", org.json.JSONObject().put("enabled", true).put("max_count", 4))
                        .put("theme", org.json.JSONObject().put("color", "#146C94"))
                )
            }.onFailure {
                paymentViewModel.reportCheckoutLaunchFailure("Payment checkout couldn't be opened. Please try again.")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Nearby Chargers") })
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when (val state = uiState) {
                is NearbyStationsUiState.PermissionRequired -> {
                    PermissionRequiredContent {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }

                is NearbyStationsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is NearbyStationsUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = { fetchLocation(forceRefresh = true) }
                    )
                }

                is NearbyStationsUiState.Success -> {
                    StationMapAndListContent(
                        stations = state.stations,
                        degradedProviders = state.degradedProviders,
                        userLocation = userLocation,
                        locationPermissionGranted = locationPermissionGranted,
                        selectedRadiusKm = selectedRadiusKm,
                        onRadiusSelected = { radiusKm ->
                            selectedRadiusKm = radiusKm
                            fetchLocation(radiusKm, forceRefresh = true)
                        },
                        paymentState = paymentState,
                        onStartCharge = paymentViewModel::beginChargePayment,
                        onClearPaymentMessage = paymentViewModel::clearMessage,
                        onShowDirections = onShowDirections
                    )
                }
            }
        }
    }
}

@Composable
private fun StationMapAndListContent(
    stations: List<ChargingStation>,
    degradedProviders: List<String>,
    userLocation: GeoPoint?,
    locationPermissionGranted: Boolean,
    selectedRadiusKm: Int,
    onRadiusSelected: (Int) -> Unit,
    paymentState: PaymentResult,
    onStartCharge: (ChargingStation) -> Unit,
    onClearPaymentMessage: () -> Unit,
    onShowDirections: (ChargingStation, GeoPoint?) -> Unit
) {
    var selectedStation by remember { mutableStateOf<ChargingStation?>(null) }
    var detailsStation by remember { mutableStateOf<ChargingStation?>(null) }
    val stationClusters = remember(stations) { clusterStations(stations) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            userLocation?.let { LatLng(it.latitude, it.longitude) } ?: DEFAULT_MAP_LOCATION,
            if (userLocation != null) 13f else 5f
        )
    }

    LaunchedEffect(userLocation) {
        userLocation?.let {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(it.latitude, it.longitude),
                13f
            )
        }
    }

    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = true,
            compassEnabled = true
        )
    }

    val mapProperties = remember(locationPermissionGranted) {
        MapProperties(isMyLocationEnabled = locationPermissionGranted)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            cameraPositionState = cameraPositionState,
            uiSettings = uiSettings,
            properties = mapProperties
        ) {
            stationClusters.forEach { cluster ->
                Marker(
                    state = MarkerState(cluster.position),
                    title = if (cluster.stations.size == 1) {
                        cluster.stations.single().name
                    } else {
                        "${cluster.stations.size} charging stations"
                    },
                    snippet = if (cluster.stations.size == 1) {
                        cluster.stations.single().let { station ->
                            "${station.source} • ${station.connectors.size} connector(s)"
                        }
                    } else {
                        "Zoom in or choose a smaller search radius"
                    },
                    onClick = {
                        if (cluster.stations.size == 1) {
                            selectedStation = cluster.stations.single()
                            true
                        } else {
                            false
                        }
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(5, 10, 25).forEach { radiusKm ->
                FilterChip(
                    selected = selectedRadiusKm == radiusKm,
                    onClick = { onRadiusSelected(radiusKm) },
                    label = { Text("$radiusKm km") }
                )
            }
        }

        selectedStation?.let { station ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(station.name, style = MaterialTheme.typography.titleMedium)
                    Text("${station.source} • ${station.connectors.size} connector(s)")
                    Text(station.address, style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { detailsStation = station },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("View station details")
                    }
                }
            }
        }

        if (degradedProviders.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Some providers are unavailable: ${degradedProviders.joinToString()}",
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        PaymentStatusContent(paymentState, onClearPaymentMessage)

        if (stations.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("No charging stations were found within $selectedRadiusKm km of your location.")
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(
                    items = stations,
                    key = { station -> station.id }
                ) { station ->
                    StationRow(station, onClick = { detailsStation = station })
                }
            }
        }
    }

    detailsStation?.let { station ->
        StationDetailsDialog(
            station = station,
            onDismiss = { detailsStation = null },
            onStartCharge = { onStartCharge(station) },
            onShowDirections = { onShowDirections(station, userLocation) }
        )
    }
}

private data class StationMarkerCluster(
    val position: LatLng,
    val stations: List<ChargingStation>
)

/** Groups stations sharing an approximately 350 m map cell to prevent overlap. */
private fun clusterStations(stations: List<ChargingStation>): List<StationMarkerCluster> =
    stations.groupBy { station ->
        val latitudeCell = floor(station.location.latitude / CLUSTER_GRID_DEGREES).toInt()
        val longitudeCell = floor(station.location.longitude / CLUSTER_GRID_DEGREES).toInt()
        latitudeCell to longitudeCell
    }.values.map { groupedStations ->
        StationMarkerCluster(
            position = LatLng(
                groupedStations.map { it.location.latitude }.average(),
                groupedStations.map { it.location.longitude }.average()
            ),
            stations = groupedStations
        )
    }

private const val CLUSTER_GRID_DEGREES = 0.0035

@Composable
private fun PermissionRequiredContent(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ChargeHub needs your location to find nearby charging stations.")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant location access")
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun StationRow(
    station: ChargingStation,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(station.name) },
        supportingContent = {
            Text(
                "${station.source} • " +
                    "${station.connectors.size} connector(s) • " +
                    station.address
            )
        }
    )
}

@Composable
private fun StationDetailsDialog(
    station: ChargingStation,
    onDismiss: () -> Unit,
    onStartCharge: () -> Unit,
    onShowDirections: () -> Unit
) {
    val connectorSummary = station.connectors
        .groupBy { it.type }
        .entries
        .joinToString("\n") { (type, connectors) ->
            "${connectors.size} × ${type.displayName()}${connectors.maxOfOrNull { it.powerKw }?.takeIf { it > 0 }?.let { " up to ${it.formatPower()}" }.orEmpty()}"
        }
        .ifBlank { "Connector details are not available for this station." }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(station.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(station.address)
                Text("Provided by ${station.source}")
                Text("Connectors", style = MaterialTheme.typography.titleSmall)
                Text(connectorSummary)
                Text(
                    "Live availability is not provided by this source.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onShowDirections
            ) {
                Text("View directions")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartCharge) { Text("Pay & start") }
                Button(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
private fun PaymentStatusContent(
    state: PaymentResult,
    onClear: () -> Unit
) {
    when (state) {
        PaymentResult.Idle -> Unit
        PaymentResult.CreatingOrder -> Surface(modifier = Modifier.fillMaxWidth()) {
            Text("Preparing secure payment…", modifier = Modifier.padding(12.dp))
        }
        PaymentResult.AwaitingVerification -> Surface(modifier = Modifier.fillMaxWidth()) {
            Text("Verifying payment…", modifier = Modifier.padding(12.dp))
        }
        is PaymentResult.Complete -> AlertDialog(
            onDismissRequest = onClear,
            title = { Text("Payment verified") },
            text = { Text("Your charging payment is confirmed. Receipt: ${state.receipt}") },
            confirmButton = { Button(onClick = onClear) { Text("Done") } }
        )
        is PaymentResult.Failed -> AlertDialog(
            onDismissRequest = onClear,
            title = { Text("Payment not completed") },
            text = { Text(state.message) },
            confirmButton = { Button(onClick = onClear) { Text("Okay") } }
        )
    }
}

private fun com.chargehub.app.data.model.ConnectorType.displayName(): String = when (this) {
    com.chargehub.app.data.model.ConnectorType.TYPE_2 -> "Type 2"
    com.chargehub.app.data.model.ConnectorType.CCS -> "CCS"
    com.chargehub.app.data.model.ConnectorType.CHADEMO -> "CHAdeMO"
    com.chargehub.app.data.model.ConnectorType.TESLA_NACS -> "NACS"
    com.chargehub.app.data.model.ConnectorType.UNKNOWN -> "Unknown connector"
}

private fun Double.formatPower(): String =
    if (this % 1.0 == 0.0) "${toInt()} kW" else "${String.format(Locale.US, "%.1f", this)} kW"
