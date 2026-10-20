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
 * "VoltNet" -- a well-behaved mock provider. Fast, reliable, reports
 * per-kWh pricing. Used as the baseline-happy-path provider in dev/test.
 */
class MockProviderA : ChargingProviderAdapter {
    override val providerId = "mock_a"
    override val displayName = "VoltNet"
    override val capabilities = setOf(
        ProviderCapability.STATION_DISCOVERY,
        ProviderCapability.STATION_DETAILS,
        ProviderCapability.AVAILABILITY,
        ProviderCapability.PRICING
    )

    override suspend fun findNearbyStations(
        center: GeoPoint,
        radiusMeters: Int
    ): ProviderResult<List<ChargingStation>> {
        delay(300)
        val stations = (1..4).map { i ->
            ChargingStation(
                id = "",
                providerId = providerId,
                providerStationId = "A-$i",
                name = "VoltNet Station $i",
                location = GeoPoint(
                    center.latitude + 0.002 * i,
                    center.longitude + 0.0015 * i
                ),
                address = "$i VoltNet Ave",
                connectors = listOf(
                    Connector("A-$i-1", ConnectorType.CCS, 50.0, AvailabilityStatus.AVAILABLE),
                    Connector("A-$i-2", ConnectorType.TYPE_2, 22.0, AvailabilityStatus.OCCUPIED)
                ),
                pricing = PricingInfo(pricePerKwh = 0.35, currency = "USD"),
                lastUpdated = System.currentTimeMillis(),
                source = displayName
            )
        }
        return ProviderResult.Success(stations)
    }

    override suspend fun getStationDetails(providerStationId: String): ProviderResult<ChargingStation> {
        delay(150)
        return ProviderResult.Failure(
            ProviderError("NOT_IMPLEMENTED", "Detail lookup not implemented in mock", retryable = false)
        )
    }
}
