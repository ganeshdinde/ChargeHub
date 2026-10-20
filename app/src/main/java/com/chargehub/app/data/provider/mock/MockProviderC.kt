package com.chargehub.app.data.provider.mock

import com.chargehub.app.data.model.AvailabilityStatus
import com.chargehub.app.data.model.ChargingStation
import com.chargehub.app.data.model.Connector
import com.chargehub.app.data.model.ConnectorType
import com.chargehub.app.data.model.GeoPoint
import com.chargehub.app.data.model.PricingInfo
import com.chargehub.app.data.model.ProviderCapability
import com.chargehub.app.data.provider.ChargingProviderAdapter
import com.chargehub.app.data.provider.ProviderError
import com.chargehub.app.data.provider.ProviderResult
import kotlinx.coroutines.delay

/**
 * "GridFast" -- a high-power mock provider that simulates an intermittent
 * outage (every 4th call fails). Exists specifically to exercise graceful
 * degradation: one provider failing must never take down the whole
 * aggregated result.
 */
class MockProviderC : ChargingProviderAdapter {
    override val providerId = "mock_c"
    override val displayName = "GridFast"
    override val capabilities = setOf(
        ProviderCapability.STATION_DISCOVERY,
        ProviderCapability.STATION_DETAILS,
        ProviderCapability.AVAILABILITY,
        ProviderCapability.PRICING,
        ProviderCapability.START_SESSION,
        ProviderCapability.STOP_SESSION
    )

    private var callCount = 0

    override suspend fun findNearbyStations(
        center: GeoPoint,
        radiusMeters: Int
    ): ProviderResult<List<ChargingStation>> {
        delay(400)
        callCount++
        if (callCount % 4 == 0) {
            return ProviderResult.Failure(
                ProviderError("PROVIDER_UNAVAILABLE", "GridFast is temporarily unreachable", retryable = true)
            )
        }
        val stations = (1..5).map { i ->
            ChargingStation(
                id = "",
                providerId = providerId,
                providerStationId = "C-$i",
                name = "GridFast Charger $i",
                location = GeoPoint(
                    center.latitude + 0.0009 * i,
                    center.longitude - 0.0017 * i
                ),
                address = "$i GridFast Way",
                connectors = listOf(
                    Connector("C-$i-1", ConnectorType.TESLA_NACS, 150.0, AvailabilityStatus.AVAILABLE),
                    Connector("C-$i-2", ConnectorType.CCS, 150.0, AvailabilityStatus.AVAILABLE)
                ),
                pricing = PricingInfo(
                    pricePerKwh = 0.42,
                    currency = "USD",
                    description = "Peak pricing may apply"
                ),
                lastUpdated = System.currentTimeMillis(),
                source = displayName
            )
        }
        return ProviderResult.Success(stations)
    }

    override suspend fun getStationDetails(providerStationId: String): ProviderResult<ChargingStation> {
        delay(150)
        return ProviderResult.Failure(
            ProviderError("NOT_IMPLEMENTED", "Not supported by this provider", retryable = false)
        )
    }
}
