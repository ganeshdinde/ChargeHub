package com.chargehub.app.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chargehub.app.data.model.ChargingStation
import com.chargehub.app.data.payment.ChargeOrder
import com.chargehub.app.data.payment.GatewayResult
import com.chargehub.app.data.payment.PaymentRepository
import com.chargehub.app.data.payment.PaymentResult
import com.chargehub.app.data.payment.PaymentResultRelay
import com.chargehub.app.data.payment.PaymentVerification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val repository: PaymentRepository,
    private val gatewayResults: PaymentResultRelay
) : ViewModel() {
    private val _state = MutableStateFlow<PaymentResult>(PaymentResult.Idle)
    val state = _state.asStateFlow()
    private val _checkoutOrders = MutableSharedFlow<ChargeOrder>()
    val checkoutOrders = _checkoutOrders.asSharedFlow()
    private var activeOrder: ChargeOrder? = null

    init {
        viewModelScope.launch {
            gatewayResults.results.collect(::handleGatewayResult)
        }
    }

    fun beginChargePayment(station: ChargingStation) {
        if (_state.value is PaymentResult.CreatingOrder || _state.value is PaymentResult.AwaitingVerification) return
        viewModelScope.launch {
            _state.value = PaymentResult.CreatingOrder
            runCatching { repository.createChargeOrder(station) }
                .onSuccess { order ->
                    activeOrder = order
                    if (order.paymentRequired) {
                        _checkoutOrders.emit(order)
                    } else {
                        activeOrder = null
                        _state.value = PaymentResult.Complete(order.receipt)
                    }
                }
                .onFailure { error ->
                    _state.value = PaymentResult.Failed(error.message ?: "Couldn't start payment.")
                }
        }
    }

    private fun handleGatewayResult(result: GatewayResult) {
        when (result) {
            is GatewayResult.Failure -> {
                _state.value = PaymentResult.Failed(
                    result.description.ifBlank { "Payment was cancelled or couldn't be completed." }
                )
            }
            is GatewayResult.Success -> verifyPayment(result)
        }
    }

    private fun verifyPayment(result: GatewayResult.Success) {
        val order = activeOrder ?: run {
            _state.value = PaymentResult.Failed("Payment verification could not be started. Please contact support.")
            return
        }
        if (!order.paymentRequired || order.razorpayOrderId.isNullOrBlank()) {
            _state.value = PaymentResult.Failed("This payment no longer has an active Razorpay order.")
            return
        }
        val signature = runCatching {
            JSONObject(result.paymentData).optString("razorpay_signature")
        }.getOrDefault("")
        if (signature.isBlank()) {
            _state.value = PaymentResult.Failed("Payment verification data is incomplete.")
            return
        }
        viewModelScope.launch {
            _state.value = PaymentResult.AwaitingVerification
            runCatching {
                repository.verifyChargePayment(order, PaymentVerification(result.paymentId, signature))
            }.onSuccess { receipt ->
                activeOrder = null
                _state.value = PaymentResult.Complete(receipt)
            }.onFailure { error ->
                _state.value = PaymentResult.Failed(error.message ?: "We couldn't verify the payment.")
            }
        }
    }

    fun clearMessage() {
        if (_state.value is PaymentResult.Complete || _state.value is PaymentResult.Failed) {
            _state.value = PaymentResult.Idle
        }
    }

    fun reportCheckoutLaunchFailure(message: String) {
        _state.value = PaymentResult.Failed(message)
    }
}
