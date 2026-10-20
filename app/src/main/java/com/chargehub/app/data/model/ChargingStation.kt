package com.chargehub.app.data.model

/**
 * Canonical, provider-agnostic geographic point.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

enum class ConnectorType {
    TYPE_2,
    CCS,
    CHADEMO,
    TESLA_NACS,
    UNKNOWN
}

/**
 * Normalized availability. Providers report their own states; adapters
 * are responsible for mapping into this set. Never surface a raw
 * provider status string directly to the UI.
 */
enum class AvailabilityStatus {
    AVAILABLE,
    OCCUPIED,
    OUT_OF_SERVICE,
    UNKNOWN,
    OFFLINE
}

data class Connector(
    val id: String,
    val type: ConnectorType,
    val powerKw: Double,
    val status: AvailabilityStatus
)

data class PricingInfo(
    val pricePerKwh: Double?,
    val currency: String,
    val description: String? = null
)

/**
 * Canonical ChargeHub station model. This is what every provider adapter
 * must normalize its data into before it reaches the repository layer.
 *
 * NOTE: [id] is currently a simple "<providerId>:<providerStationId>" composite
 * assigned by the repository. This is a placeholder for the real cross-provider
 * deduplication/aggregation algorithm (geo-proximity + name/address similarity),
 * which is a separate, later increment — see the Aggregation + Deduplication
 * prompt in the playbook. Do not treat today's [id] as a stable dedup key.
 */
data class ChargingStation(
    val id: String,
    val providerId: String,
    val providerStationId: String,
    val name: String,
    val location: GeoPoint,
    val address: String,
    val connectors: List<Connector>,
    val pricing: PricingInfo?,
    val amenities: List<String> = emptyList(),
    val lastUpdated: Long,
    val source: String
)
