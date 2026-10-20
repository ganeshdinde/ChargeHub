package com.chargehub.app.data.payment

import com.chargehub.app.data.model.ChargingStation

/**
 * Returned only by the ChargeHub backend after it has calculated applicable
 * wallet credit, coupon/gift-card value and the remaining payable amount.
 */
data class ChargeOrder(
    val station: ChargingStation,
    val paymentRequired: Boolean,
    val razorpayOrderId: String?,
    val checkoutKeyId: String?,
    val amountPaise: Long,
    val currency: String,
    val receipt: String
)

data class PaymentVerification(
    val razorpayPaymentId: String,
    val razorpaySignature: String
)

sealed interface PaymentResult {
    data object Idle : PaymentResult
    data object CreatingOrder : PaymentResult
    data object AwaitingVerification : PaymentResult
    data class Complete(val receipt: String) : PaymentResult
    data class Failed(val message: String) : PaymentResult
}
