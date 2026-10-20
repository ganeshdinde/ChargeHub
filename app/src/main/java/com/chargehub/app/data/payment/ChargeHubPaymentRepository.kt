package com.chargehub.app.data.payment

import com.chargehub.app.data.model.ChargingStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Production payment boundary. Razorpay's secret is deliberately never sent
 * to this client: the backend creates and verifies all orders.
 */
class ChargeHubPaymentRepository(
    private val client: OkHttpClient,
    private val backendUrl: String
) : PaymentRepository {

    override suspend fun createChargeOrder(station: ChargingStation): ChargeOrder = withContext(Dispatchers.IO) {
        val endpoint = endpoint("v1/charge-orders")
        val body = JSONObject()
            .put("stationId", station.id)
            .put("providerId", station.providerId)
            .put("providerStationId", station.providerStationId)
            .toString()
            .toRequestBody(JSON)
        val response = execute(
            Request.Builder().url(endpoint).post(body).build(),
            "Couldn't prepare a secure payment."
        )
        val payload = JSONObject(response)
        val paymentRequired = payload.optBoolean("paymentRequired", true)
        ChargeOrder(
            station = station,
            paymentRequired = paymentRequired,
            razorpayOrderId = payload.optionalString("razorpayOrderId").takeIf { paymentRequired }
                ?: if (paymentRequired) throw PaymentException("ChargeHub payments returned no Razorpay order.") else null,
            checkoutKeyId = payload.optionalString("checkoutKeyId").takeIf { paymentRequired }
                ?: if (paymentRequired) throw PaymentException("ChargeHub payments returned no checkout key.") else null,
            amountPaise = payload.optionalLong("amountPaise"),
            currency = payload.requiredString("currency"),
            receipt = payload.requiredString("receipt")
        )
    }

    override suspend fun verifyChargePayment(
        order: ChargeOrder,
        verification: PaymentVerification
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("razorpayOrderId", order.razorpayOrderId)
            .put("razorpayPaymentId", verification.razorpayPaymentId)
            .put("razorpaySignature", verification.razorpaySignature)
            .toString()
            .toRequestBody(JSON)
        val response = execute(
            Request.Builder()
                .url(endpoint("v1/charge-orders/${order.razorpayOrderId.orEmpty()}/verify"))
                .post(body)
                .build(),
            "We couldn't verify this payment."
        )
        JSONObject(response).requiredString("receipt")
    }

    private fun endpoint(path: String): String {
        if (backendUrl.isBlank() || backendUrl.startsWith("YOUR_")) {
            throw PaymentException("Payments are not configured yet. Add CHARGEHUB_BACKEND_URL to secrets.properties.")
        }
        return backendUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments(path)
            ?.build()
            ?.toString()
            ?: throw PaymentException("The ChargeHub payment service URL is invalid.")
    }

    private fun execute(request: Request, fallbackMessage: String): String = try {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val serverMessage = runCatching { JSONObject(body).optString("message") }.getOrDefault("")
                throw PaymentException(serverMessage.ifBlank { "$fallbackMessage Please try again." })
            }
            body.ifBlank { throw PaymentException("$fallbackMessage The server returned no details.") }
        }
    } catch (exception: PaymentException) {
        throw exception
    } catch (exception: IOException) {
        throw PaymentException("Couldn't reach ChargeHub payments. Check your connection and try again.")
    }

    private fun JSONObject.requiredString(name: String): String = optString(name).takeIf(String::isNotBlank)
        ?: throw PaymentException("ChargeHub payments returned an incomplete response.")

    private fun JSONObject.optionalString(name: String): String? = optString(name).takeIf(String::isNotBlank)

    private fun JSONObject.optionalLong(name: String): Long = optLong(name).takeIf { it >= 0 }
        ?: throw PaymentException("ChargeHub payments returned an invalid amount.")

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

class PaymentException(message: String) : Exception(message)
