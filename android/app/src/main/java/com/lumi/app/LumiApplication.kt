package com.lumi.app

import android.app.Application

/**
 * Lumi Application class.
 * Initializes app-wide dependencies and configurations.
 */
class LumiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: LumiApplication
            private set
    }
}
