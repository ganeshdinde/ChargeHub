package com.chargehub.app.data.payment

import com.chargehub.app.data.model.ChargingStation

interface PaymentRepository {
    suspend fun createChargeOrder(station: ChargingStation): ChargeOrder
    suspend fun verifyChargePayment(order: ChargeOrder, verification: PaymentVerification): String
}
