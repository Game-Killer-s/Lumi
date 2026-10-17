package com.lumi.app

import android.app.Application
import com.lumi.app.network.TokenManager
import com.lumi.app.player.DownloadManagerHolder
import com.lumi.app.player.PlayerController

/**
 * Lumi Application class.
 * Initializes app-wide dependencies and configurations.
 */
class LumiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        DownloadManagerHolder.init(this)
        PlayerController.init(this)

        // TODO(auth): no login screen is wired up yet (see backlog decision
        // to ship Home/Playlist/Album/Artist first with a mock session).
        // Replace with a real AuthRepository.login() flow + persisted tokens
        // (TokenManager currently only holds tokens in memory) before shipping.
        if (!TokenManager.isLoggedIn()) {
            TokenManager.saveTokens(access = "mock-access-token", refresh = "mock-refresh-token")
        }
    }

    companion object {
        lateinit var instance: LumiApplication
            private set
    }
}
