package com.lumi.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.map.Mapper
import com.lumi.app.ui.i18n.AppSettingsStore
import com.lumi.app.network.ApiConstants
import com.lumi.app.player.DownloadManagerHolder
import com.lumi.app.player.PlayerController

/**
 * Lumi Application class.
 * Initializes app-wide dependencies and configurations.
 */
class LumiApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Мова й тема читаються з диска до створення першого Activity (ТЗ 6.3.7).
        AppSettingsStore.init(this)

        DownloadManagerHolder.init(this)
        PlayerController.init(this)

        // Токени відновлює TokenManager.init() під час старту UI (LumiApp),
        // щойно стає доступним Context. Фейкову сесію тут не створюємо:
        // інакше екран профілю вважав би користувача залогіненим без токена.
    }

    /**
     * Coil має вміти вантажити обкладинки за відносними шляхами з БД
     * ("/media/cover/..."), тому всі String-моделі проходимо через
     * [ApiConstants.mediaUrl].
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(MediaUrlMapper()) }
            .crossfade(true)
            .build()

    private class MediaUrlMapper : Mapper<String, String> {
        override fun map(data: String, options: coil.request.Options): String =
            ApiConstants.mediaUrl(data) ?: data
    }

    companion object {
        lateinit var instance: LumiApplication
            private set
    }
}
