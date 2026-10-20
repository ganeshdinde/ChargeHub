package com.chargehub.app.domain.usecase

import com.chargehub.app.data.model.GeoPoint
import com.chargehub.app.data.repository.NearbyStationsResult
import com.chargehub.app.data.repository.StationRepository

class GetNearbyStationsUseCase(
    private val repository: StationRepository
) {
    suspend operator fun invoke(
        center: GeoPoint,
        radiusMeters: Int = 25_000
    ): NearbyStationsResult = repository.findNearbyStations(center, radiusMeters)
}
