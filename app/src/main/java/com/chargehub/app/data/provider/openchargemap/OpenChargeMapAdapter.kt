package com.chargehub.app.data.provider.openchargemap

import com.chargehub.app.data.model.AvailabilityStatus
import com.chargehub.app.data.model.ChargingStation
import com.chargehub.app.data.model.Connector
import com.chargehub.app.data.model.ConnectorType
import com.chargehub.app.data.model.GeoPoint
import com.chargehub.app.data.model.ProviderCapability
import com.chargehub.app.data.provider.ChargingProviderAdapter
import com.chargehub.app.data.provider.ProviderError
import com.chargehub.app.data.provider.ProviderResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant

/**
 * Open Charge Map supplies location data, not authoritative live connector
 * availability. Consequently, normalized connector statuses are UNKNOWN.
 * Provider credentials should move behind the ChargeHub backend before a
 * production release; this adapter keeps that replacement isolated.
 */
class OpenChargeMapAdapter(
    private val client: OkHttpClient,
    private val apiKey: String
) : ChargingProviderAdapter {

    override val providerId = "open_charge_map"
    override val displayName = "Open Charge Map"
    override val capabilities = setOf(
        ProviderCapability.STATION_DISCOVERY,
        ProviderCapability.STATION_DETAILS
    )

    override suspend fun findNearbyStations(
        center: GeoPoint,
        radiusMeters: Int
    ): ProviderResult<List<ChargingStation>> = withContext(Dispatchers.IO) {
        if (!center.isValid()) {
            return@withContext ProviderResult.Failure(
                ProviderError("INVALID_LOCATION", "The current location is invalid.")
            )
        }
        if (radiusMeters !in 1_000..100_000) {
            return@withContext ProviderResult.Failure(
                ProviderError("INVALID_RADIUS", "Search radius must be between 1 and 100 km.")
            )
        }
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_")) {
            return@withContext ProviderResult.Failure(
                ProviderError(
                    "MISSING_API_KEY",
                    "Open Charge Map is not configured. Add OCM_API_KEY to secrets.properties."
                )
            )
        }

        val url = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("output", "json")
            .addQueryParameter("latitude", center.latitude.toString())
            .addQueryParameter("longitude", center.longitude.toString())
            .addQueryParameter("distance", (radiusMeters / 1_000.0).toString())
            .addQueryParameter("distanceunit", "KM")
            .addQueryParameter("maxresults", "100")
            .addQueryParameter("key", apiKey)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("X-API-Key", apiKey)
            .header("User-Agent", "ChargeHub-Android/0.3")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ProviderResult.Failure(response.toProviderError())
                }
                val payload = response.body?.string()
                    ?: return@withContext ProviderResult.Failure(
                        ProviderError("EMPTY_RESPONSE", "Open Charge Map returned an empty response.", true)
                    )
                return@withContext try {
                    ProviderResult.Success(parseStations(JSONArray(payload)))
                } catch (exception: Exception) {
                    ProviderResult.Failure(
                        ProviderError("PARSE_ERROR", "Open Charge Map returned an unreadable response.", true)
                    )
                }
            }
        } catch (exception: IOException) {
            ProviderResult.Failure(
                ProviderError("NETWORK_ERROR", "Couldn't reach Open Charge Map. Check your internet connection.", true)
            )
        }
    }

    override suspend fun getStationDetails(providerStationId: String): ProviderResult<ChargingStation> =
        ProviderResult.Failure(
            ProviderError("NOT_IMPLEMENTED", "Station-detail lookup is not available yet.")
        )

    private fun parseStations(items: JSONArray): List<ChargingStation> = buildList {
        for (index in 0 until items.length()) {
            val station = items.optJSONObject(index) ?: continue
            station.toChargingStationOrNull()?.let(::add)
        }
    }

    private fun JSONObject.toChargingStationOrNull(): ChargingStation? {
        val address = optJSONObject("AddressInfo") ?: return null
        val latitude = address.optFiniteDouble("Latitude") ?: return null
        val longitude = address.optFiniteDouble("Longitude") ?: return null
        if (!GeoPoint(latitude, longitude).isValid()) return null

        val stationId = opt("ID")?.toString()?.takeIf(String::isNotBlank) ?: return null
        val operator = optJSONObject("OperatorInfo")?.optString("Title").orEmpty()
        val title = address.optString("Title").ifBlank { operator.ifBlank { "Charging station" } }
        return ChargingStation(
            id = "",
            providerId = providerId,
            providerStationId = stationId,
            name = title,
            location = GeoPoint(latitude, longitude),
            address = address.displayAddress(),
            connectors = optJSONArray("Connections").toConnectors(stationId),
            pricing = null,
            lastUpdated = optString("DateLastStatusUpdate").toEpochMillis(),
            source = operator.ifBlank { displayName }
        )
    }

    private fun JSONArray?.toConnectors(stationId: String): List<Connector> = buildList {
        if (this@toConnectors == null) return@buildList
        for (index in 0 until length()) {
            val connection = optJSONObject(index) ?: continue
            val connectionId = connection.opt("ID")?.toString() ?: "$stationId-$index"
            add(
                Connector(
                    id = connectionId,
                    type = connection.optJSONObject("ConnectionType")
                        ?.optString("Title").orEmpty().toConnectorType(),
                    powerKw = connection.optFiniteDouble("PowerKW") ?: 0.0,
                    status = AvailabilityStatus.UNKNOWN
                )
            )
        }
    }

    private fun JSONObject.displayAddress(): String = listOf(
        optString("AddressLine1"),
        optString("Town"),
        optString("StateOrProvince"),
        optString("Postcode")
    ).filter(String::isNotBlank).distinct().joinToString(", ").ifBlank { "Address unavailable" }

    private fun JSONObject.optFiniteDouble(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return optDouble(name).takeIf(Double::isFinite)
    }

    private fun String.toConnectorType(): ConnectorType = when {
        contains("chademo", ignoreCase = true) -> ConnectorType.CHADEMO
        contains("ccs", ignoreCase = true) -> ConnectorType.CCS
        contains("nacs", ignoreCase = true) -> ConnectorType.TESLA_NACS
        contains("type 2", ignoreCase = true) || contains("iec 62196", ignoreCase = true) -> ConnectorType.TYPE_2
        else -> ConnectorType.UNKNOWN
    }

    private fun String.toEpochMillis(): Long = runCatching { Instant.parse(this).toEpochMilli() }
        .getOrDefault(System.currentTimeMillis())

    private fun GeoPoint.isValid(): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

    private fun okhttp3.Response.toProviderError(): ProviderError = when (code) {
        401, 403 -> ProviderError("AUTH_ERROR", "Open Charge Map rejected the API key. Check OCM_API_KEY.")
        429 -> ProviderError("RATE_LIMITED", "Open Charge Map is busy. Please try again shortly.", true)
        in 500..599 -> ProviderError("SERVICE_UNAVAILABLE", "Open Charge Map is temporarily unavailable.", true)
        else -> ProviderError("HTTP_$code", "Open Charge Map request failed (HTTP $code).", true)
    }

    private companion object {
        const val BASE_URL = "https://api.openchargemap.io/v3/poi/"
    }
}
