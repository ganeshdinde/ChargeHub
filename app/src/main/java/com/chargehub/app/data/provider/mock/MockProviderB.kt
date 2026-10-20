package com.chargehub.app.data.provider.mock

import com.chargehub.app.data.model.AvailabilityStatus
import com.chargehub.app.data.model.ChargingStation
import com.chargehub.app.data.model.Connector
import com.chargehub.app.data.model.ConnectorType
import com.chargehub.app.data.model.GeoPoint
import com.chargehub.app.data.model.ProviderCapability
import com.chargehub.app.data.provider.ChargingProviderAdapter
import com.chargehub.app.data.provider.ProviderError
import com.chargehub.app.data.provider.ProviderResult
import kotlinx.coroutines.delay

/**
 * "Metro Hub" -- a slower, more limited mock provider. Does not support
 * pricing, reports stale data, and has higher latency. Used to exercise
 * partial-capability handling and freshness UI.
 */
class MockProviderB : ChargingProviderAdapter {
    override val providerId = "mock_b"
    override val displayName = "Metro Hub"
    override val capabilities = setOf(
        ProviderCapability.STATION_DISCOVERY,
        ProviderCapability.AVAILABILITY
    )

    override suspend fun findNearbyStations(
        center: GeoPoint,
        radiusMeters: Int
    ): ProviderResult<List<ChargingStation>> {
        delay(600)
        val stations = (1..3).map { i ->
            ChargingStation(
                id = "",
                providerId = providerId,
                providerStationId = "B-$i",
                name = "Metro Hub #$i",
                location = GeoPoint(
                    center.latitude - 0.0018 * i,
                    center.longitude + 0.0022 * i
                ),
                address = "$i Metro Plaza",
                connectors = listOf(
                    Connector("B-$i-1", ConnectorType.CHADEMO, 62.5, AvailabilityStatus.UNKNOWN)
                ),
                pricing = null,
                lastUpdated = System.currentTimeMillis() - 120_000,
                source = displayName
            )
        }
        return ProviderResult.Success(stations)
    }

    override suspend fun getStationDetails(providerStationId: String): ProviderResult<ChargingStation> {
        delay(200)
        return ProviderResult.Failure(
            ProviderError("NOT_IMPLEMENTED", "Not supported by this provider", retryable = false)
        )
    }
}
