package com.chargehub.app.data.provider

/**
 * Central lookup of all registered provider adapters. Adding a new
 * charging network means writing a new ChargingProviderAdapter and
 * registering it here (via DI) -- nothing else in the app should need
 * to change.
 */
class ProviderRegistry(private val adapters: List<ChargingProviderAdapter>) {
    fun all(): List<ChargingProviderAdapter> = adapters

    fun byId(providerId: String): ChargingProviderAdapter? =
        adapters.find { it.providerId == providerId }
}
