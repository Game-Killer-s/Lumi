package com.lumi.app.player

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCache
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import com.lumi.app.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.Executors

/**
 * Real offline-download support for tracks, backed by Media3's
 * DownloadManager + SimpleCache. Downloaded tracks are also transparently
 * served to the player from cache via [cacheDataSourceFactory], so
 * playback works offline once a track has finished downloading.
 *
 * This is process-wide (like TokenManager/NetworkModule) — call [init]
 * once from LumiApplication.onCreate().
 */
object DownloadManagerHolder {

    private const val CACHE_MAX_BYTES = 1024L * 1024L * 1024L // 1 GB
    private const val TAG = "DownloadManagerHolder"

    private lateinit var cache: SimpleCache
    private lateinit var downloadManager: DownloadManager
    private var initialized = false

    private val _downloadedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedTrackIds: StateFlow<Set<String>> = _downloadedTrackIds

    private val _downloadingTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingTrackIds: StateFlow<Set<String>> = _downloadingTrackIds

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        val databaseProvider = StandaloneDatabaseProvider(context)
        val cacheDir = File(context.filesDir, "media_cache")
        cache = SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(CACHE_MAX_BYTES), databaseProvider)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        val downloadExecutor = Executors.newFixedThreadPool(2)

        downloadManager = DownloadManager(
            context,
            databaseProvider,
            cache,
            httpDataSourceFactory,
            downloadExecutor
        ).apply {
            maxParallelDownloads = 2
            addListener(object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?
                ) {
                    val id = download.request.id
                    when (download.state) {
                        Download.STATE_COMPLETED -> {
                            _downloadedTrackIds.update { it + id }
                            _downloadingTrackIds.update { it - id }
                        }
                        Download.STATE_DOWNLOADING, Download.STATE_QUEUED -> {
                            _downloadingTrackIds.update { it + id }
                        }
                        Download.STATE_REMOVING, Download.STATE_FAILED, Download.STATE_STOPPED -> {
                            _downloadingTrackIds.update { it - id }
                            if (download.state != Download.STATE_STOPPED) {
                                _downloadedTrackIds.update { it - id }
                            }
                            if (finalException != null) {
                                Log.w(TAG, "Download failed for $id", finalException)
                            }
                        }
                    }
                }

                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                    val id = download.request.id
                    _downloadedTrackIds.update { it - id }
                    _downloadingTrackIds.update { it - id }
                }
            })
        }

        // Seed current state from persisted download index (survives process death).
        try {
            val cursor = downloadManager.downloadIndex.getDownloads()
            val completed = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                if (cursor.download.state == Download.STATE_COMPLETED) {
                    completed += cursor.download.request.id
                }
            }
            cursor.close()
            _downloadedTrackIds.value = completed
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read persisted download index", e)
        }
    }

    fun isDownloaded(trackId: String): Boolean = trackId in _downloadedTrackIds.value

    fun downloadTrack(track: Track) {
        val url = track.audioUrl ?: return
        if (!initialized) return
        val request = DownloadRequest.Builder(track.id, android.net.Uri.parse(url)).build()
        downloadManager.addDownload(request)
    }

    fun removeDownload(trackId: String) {
        if (!initialized) return
        downloadManager.removeDownload(trackId)
    }

    /** DataSource factory the player should use so cached/downloaded audio is served locally. */
    fun cacheDataSourceFactory(): CacheDataSource.Factory {
        val upstream = DefaultHttpDataSource.Factory()
        return CacheDataSource.Factory()
            .setCache(if (initialized) cache else NoOpCache())
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun mediaItemFor(track: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.audioUrl)
            .build()
}
