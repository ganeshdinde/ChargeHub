package com.chargehub.app.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chargehub.app.data.model.GeoPoint
import com.chargehub.app.domain.usecase.GetNearbyStationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NearbyStationsViewModel @Inject constructor(
    private val getNearbyStations: GetNearbyStationsUseCase
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<NearbyStationsUiState>(NearbyStationsUiState.PermissionRequired)
    val uiState: StateFlow<NearbyStationsUiState> = _uiState.asStateFlow()
    private var lastSearchLocation: GeoPoint? = null
    private var lastSearchRadiusMeters: Int? = null
    private var nearbyLoadJob: Job? = null

    fun onLocationAvailable(
        location: GeoPoint,
        radiusMeters: Int = DEFAULT_RADIUS_METERS,
        forceRefresh: Boolean = false
    ) {
        val locationChangedEnough = lastSearchLocation
            ?.distanceMetersTo(location)
            ?.let { it >= MINIMUM_SEARCH_MOVEMENT_METERS }
            ?: true
        val radiusChanged = lastSearchRadiusMeters != radiusMeters

        if (!forceRefresh && !locationChangedEnough && !radiusChanged) return

        lastSearchLocation = location
        lastSearchRadiusMeters = radiusMeters
        nearbyLoadJob?.cancel()
        nearbyLoadJob = viewModelScope.launch {
            _uiState.value = NearbyStationsUiState.Loading
            try {
                val result = getNearbyStations(location, radiusMeters)
                _uiState.value = if (result.stations.isEmpty() && result.failedProviders.isNotEmpty()) {
                    NearbyStationsUiState.Error(
                        result.failures.joinToString("\n") { failure ->
                            "${failure.providerName}: ${failure.error.message}"
                        }
                    )
                } else {
                    NearbyStationsUiState.Success(
                        stations = result.stations,
                        degradedProviders = result.failedProviders
                    )
                }
            } catch (e: Exception) {
                _uiState.value = NearbyStationsUiState.Error(e.message ?: "Something went wrong")
            }
        }
    }

    fun onPermissionDenied() {
        _uiState.value = NearbyStationsUiState.PermissionRequired
    }

    fun onLocationUnavailable() {
        _uiState.value = NearbyStationsUiState.Error(
            "We couldn't get your current location. Turn on location services and try again."
        )
    }

    private companion object {
        const val DEFAULT_RADIUS_METERS = 10_000
        const val MINIMUM_SEARCH_MOVEMENT_METERS = 1_000.0
    }
}

private fun GeoPoint.distanceMetersTo(other: GeoPoint): Double {
    val earthRadiusMeters = 6_371_000.0
    val latitudeDelta = Math.toRadians(other.latitude - latitude)
    val longitudeDelta = Math.toRadians(other.longitude - longitude)
    val a = kotlin.math.sin(latitudeDelta / 2) * kotlin.math.sin(latitudeDelta / 2) +
        kotlin.math.cos(Math.toRadians(latitude)) * kotlin.math.cos(Math.toRadians(other.latitude)) *
        kotlin.math.sin(longitudeDelta / 2) * kotlin.math.sin(longitudeDelta / 2)
    return earthRadiusMeters * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}
