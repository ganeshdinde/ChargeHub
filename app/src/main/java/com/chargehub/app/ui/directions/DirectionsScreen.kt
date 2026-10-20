package com.chargehub.app.ui.directions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chargehub.app.data.model.ChargingStation
import com.chargehub.app.data.model.GeoPoint
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * In-app navigation surface. It never launches another navigation app.
 * A backend road-routing feed can replace the direct preview line with a
 * road geometry without changing the surrounding user experience.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectionsScreen(
    station: ChargingStation,
    origin: GeoPoint?,
    onBack: () -> Unit
) {
    val destination = LatLng(station.location.latitude, station.location.longitude)
    val originLatLng = origin?.let { LatLng(it.latitude, it.longitude) }
    val cameraPosition = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(originLatLng ?: destination, if (originLatLng == null) 14f else 12f)
    }
    val distanceKm = remember(origin, station.id) { origin?.distanceTo(station.location)?.div(1_000) }
    val bearing = remember(origin, station.id) { origin?.bearingTo(station.location) }

    Scaffold(topBar = { TopAppBar(title = { Text("Directions") }, navigationIcon = { Button(onClick = onBack) { Text("Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GoogleMap(
                modifier = Modifier.fillMaxWidth().height(360.dp),
                cameraPositionState = cameraPosition,
                uiSettings = MapUiSettings(zoomControlsEnabled = false, compassEnabled = true)
            ) {
                originLatLng?.let { Marker(MarkerState(it), title = "Your location") }
                Marker(MarkerState(destination), title = station.name)
                if (originLatLng != null) {
                    Polyline(points = listOf(originLatLng, destination), color = MaterialTheme.colorScheme.primary, width = 12f)
                }
            }
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(station.name, style = MaterialTheme.typography.titleLarge)
                    Text(station.address, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (distanceKm == null) {
                        Text("Turn on precise location to preview directions.")
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("${"%.1f".format(distanceKm)} km away")
                            Text("Head ${bearing?.toCompassDirection().orEmpty()}")
                        }
                        Text(
                            "Route preview is shown in ChargeHub. Road-by-road guidance will appear when the secure routing service is connected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun GeoPoint.distanceTo(other: GeoPoint): Double {
    val radius = 6_371_000.0
    val lat = Math.toRadians(other.latitude - latitude)
    val lng = Math.toRadians(other.longitude - longitude)
    val a = sin(lat / 2) * sin(lat / 2) + cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) * sin(lng / 2) * sin(lng / 2)
    return 2 * radius * atan2(sqrt(a), sqrt(1 - a))
}

private fun GeoPoint.bearingTo(other: GeoPoint): Double {
    val deltaLongitude = Math.toRadians(other.longitude - longitude)
    val startLatitude = Math.toRadians(latitude)
    val endLatitude = Math.toRadians(other.latitude)
    val y = sin(deltaLongitude) * cos(endLatitude)
    val x = cos(startLatitude) * sin(endLatitude) - sin(startLatitude) * cos(endLatitude) * cos(deltaLongitude)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

private fun Double.toCompassDirection(): String = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    [((this + 22.5) / 45).toInt() % 8]
