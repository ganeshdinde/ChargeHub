package com.chargehub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.chargehub.app.data.payment.PaymentResultRelay
import com.chargehub.app.ui.ChargeHubRoot
import com.chargehub.app.ui.theme.ChargeHubTheme
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity(), PaymentResultWithDataListener {
    @Inject lateinit var paymentResultRelay: PaymentResultRelay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChargeHubTheme {
                ChargeHubRoot()
            }
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        val paymentId = razorpayPaymentId.orEmpty()
        if (paymentId.isNotBlank()) {
            paymentResultRelay.publishSuccess(paymentId, paymentData?.data?.toString().orEmpty())
        } else {
            paymentResultRelay.publishFailure(-1, "Razorpay did not return a payment ID.")
        }
    }

    override fun onPaymentError(code: Int, description: String?, paymentData: PaymentData?) {
        paymentResultRelay.publishFailure(code, description)
    }
}
