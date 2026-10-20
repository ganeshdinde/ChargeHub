package com.chargehub.app.di

import com.chargehub.app.BuildConfig
import com.chargehub.app.data.provider.ChargingProviderAdapter
import com.chargehub.app.data.provider.ProviderRegistry
import com.chargehub.app.data.provider.openchargemap.OpenChargeMapAdapter
import com.chargehub.app.data.payment.ChargeHubPaymentRepository
import com.chargehub.app.data.payment.PaymentRepository
import com.chargehub.app.data.repository.StationRepository
import com.chargehub.app.domain.usecase.GetNearbyStationsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.jvm.JvmSuppressWildcards
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Wires provider adapters into the app. The UI and repository stay unaware of
 * the concrete station-data provider.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideProviderAdapters(client: OkHttpClient): List<ChargingProviderAdapter> =
        listOf(
            OpenChargeMapAdapter(
                client = client,
                apiKey = BuildConfig.OCM_API_KEY
            )
        )

    @Provides
    @Singleton
    fun provideProviderRegistry(
        adapters: List<@JvmSuppressWildcards ChargingProviderAdapter>
    ): ProviderRegistry =
        ProviderRegistry(adapters)

    @Provides
    @Singleton
    fun provideStationRepository(
        registry: ProviderRegistry
    ): StationRepository =
        StationRepository(registry)

    @Provides
    @Singleton
    fun providePaymentRepository(client: OkHttpClient): PaymentRepository =
        ChargeHubPaymentRepository(client, BuildConfig.CHARGEHUB_BACKEND_URL)

    @Provides
    fun provideGetNearbyStationsUseCase(
        repository: StationRepository
    ): GetNearbyStationsUseCase =
        GetNearbyStationsUseCase(repository)
}
