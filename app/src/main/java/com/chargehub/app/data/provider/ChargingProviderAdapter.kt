package com.chargehub.app.data.provider

import com.chargehub.app.data.model.ChargingStation
import com.chargehub.app.data.model.GeoPoint
import com.chargehub.app.data.model.ProviderCapability

sealed class ProviderResult<out T> {
    data class Success<T>(val data: T) : ProviderResult<T>()
    data class Failure(val error: ProviderError) : ProviderResult<Nothing>()
}

data class ProviderError(
    val code: String,
    val message: String,
    val retryable: Boolean = false
)

/**
 * Every charging network integration -- mock or real -- implements this
 * interface. The rest of the app (repository, use cases, UI) only ever
 * talks to this contract, never to a provider's raw API shape.
 *
 * Ground rules for any real implementation of this interface:
 *  - Only call officially supported/authorized provider APIs.
 *  - Never scrape or reverse-engineer a private provider app/API.
 *  - Never hold provider credentials in the Android app; auth happens
 *    behind a backend/API gateway in the production architecture.
 *  - A single provider adapter failing must never throw out of these
 *    methods -- return ProviderResult.Failure instead, so the repository
 *    can degrade gracefully.
 */
interface ChargingProviderAdapter {
    val providerId: String
    val displayName: String
    val capabilities: Set<ProviderCapability>

    suspend fun findNearbyStations(
        center: GeoPoint,
        radiusMeters: Int
    ): ProviderResult<List<ChargingStation>>

    suspend fun getStationDetails(providerStationId: String): ProviderResult<ChargingStation>
}
