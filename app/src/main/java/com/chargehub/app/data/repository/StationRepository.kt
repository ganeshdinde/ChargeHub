package com.chargehub.app.data.repository

import com.chargehub.app.data.model.ChargingStation
import com.chargehub.app.data.model.GeoPoint
import com.chargehub.app.data.provider.ProviderRegistry
import com.chargehub.app.data.provider.ProviderResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * [failedProviders] holds the display names of providers that failed on
 * this call, so the UI can show a partial-degradation notice rather than
 * a hard error when at least one provider succeeded.
 */
data class NearbyStationsResult(
    val stations: List<ChargingStation>,
    val failedProviders: List<String>,
    val failures: List<ProviderFailure>
)

data class ProviderFailure(
    val providerName: String,
    val error: com.chargehub.app.data.provider.ProviderError
)

/**
 * Fans a nearby-station query out to every registered provider adapter
 * concurrently and merges the results. This is the graceful-degradation
 * boundary: one provider throwing/failing never fails the whole call.
 *
 * NOTE: this does not yet deduplicate stations that represent the same
 * physical location across providers -- that's the separate Aggregation +
 * Deduplication increment. Today, results are simply concatenated.
 */
class StationRepository(
    private val registry: ProviderRegistry
) {
    suspend fun findNearbyStations(
        center: GeoPoint,
        radiusMeters: Int = 25_000
    ): NearbyStationsResult = coroutineScope {
        val deferred = registry.all().map { adapter ->
            async {
                adapter.displayName to adapter.findNearbyStations(center, radiusMeters)
            }
        }
        val results = deferred.map { it.await() }

        val stations = mutableListOf<ChargingStation>()
        val failed = mutableListOf<String>()
        val failures = mutableListOf<ProviderFailure>()

        for ((providerName, result) in results) {
            when (result) {
                is ProviderResult.Success -> stations += result.data.map { station ->
                    station.copy(id = "${station.providerId}:${station.providerStationId}")
                }
                is ProviderResult.Failure -> {
                    failed += providerName
                    failures += ProviderFailure(providerName, result.error)
                }
            }
        }

        NearbyStationsResult(
            stations = stations,
            failedProviders = failed,
            failures = failures
        )
    }
}
