package com.chargehub.app

import android.app.Application
import com.razorpay.Checkout
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ChargeHubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Checkout.preload(applicationContext)
    }
}
