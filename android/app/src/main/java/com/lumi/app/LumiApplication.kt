package com.lumi.app

import android.app.Application
import com.lumi.app.ui.i18n.AppSettingsStore

/**
 * Lumi Application class.
 * Initializes app-wide dependencies and configurations.
 */
class LumiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Мова й тема читаються з диска до створення першого Activity (ТЗ 6.3.7).
        AppSettingsStore.init(this)
    }

    companion object {
        lateinit var instance: LumiApplication
            private set
    }
}
