package com.chargehub.app.ui.nearby

import com.chargehub.app.data.model.ChargingStation

/**
 * Explicit loading/success/error/permission states for the first vertical
 * slice: open app -> location permission -> load nearby stations -> render.
 */
sealed interface NearbyStationsUiState {
    data object PermissionRequired : NearbyStationsUiState
    data object Loading : NearbyStationsUiState
    data class Success(
        val stations: List<ChargingStation>,
        val degradedProviders: List<String>
    ) : NearbyStationsUiState
    data class Error(val message: String) : NearbyStationsUiState
}
