package com.instantgameclipper

import android.app.Application
import com.instantgameclipper.service.ServiceNotification

class InstantGameClipperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceNotification.ensureChannel(this)
    }
}
