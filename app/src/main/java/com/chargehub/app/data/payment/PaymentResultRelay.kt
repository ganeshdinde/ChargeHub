package com.chargehub.app.data.payment

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Bridges Razorpay's Activity callback to the payment ViewModel. */
@Singleton
class PaymentResultRelay @Inject constructor() {
    private val _results = MutableSharedFlow<GatewayResult>(extraBufferCapacity = 1)
    val results = _results.asSharedFlow()

    fun publishSuccess(paymentId: String, paymentData: String) {
        _results.tryEmit(GatewayResult.Success(paymentId, paymentData))
    }

    fun publishFailure(code: Int, description: String?) {
        _results.tryEmit(GatewayResult.Failure(code, description.orEmpty()))
    }
}

sealed interface GatewayResult {
    data class Success(val paymentId: String, val paymentData: String) : GatewayResult
    data class Failure(val code: Int, val description: String) : GatewayResult
}
