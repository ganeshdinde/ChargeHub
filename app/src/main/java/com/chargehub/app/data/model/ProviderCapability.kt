package com.chargehub.app.data.model

/**
 * A discoverable capability a provider adapter may or may not support.
 * Capabilities are optional by design -- never assume every provider
 * supports every capability.
 */
enum class ProviderCapability {
    STATION_DISCOVERY,
    STATION_DETAILS,
    AVAILABILITY,
    PRICING,
    START_SESSION,
    STOP_SESSION,
    SESSION_STATUS,
    CHARGING_HISTORY
}
