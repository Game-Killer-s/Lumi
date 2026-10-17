package com.lumi.app.player

import android.content.Context

/**
 * Зберігає чергу, позицію, shuffle і repeat у SharedPreferences, щоб після
 * перезапуску застосунку програвання відновилося з тієї самої позиції
 * (без автоматичного старту).
 */
class PlaybackSnapshotStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(snapshot: PlaybackSnapshot) {
        prefs.edit().putString(KEY_SNAPSHOT, PlaybackSnapshotCodec.encode(snapshot)).apply()
    }

    fun load(): PlaybackSnapshot? =
        PlaybackSnapshotCodec.decode(prefs.getString(KEY_SNAPSHOT, null))

    fun clear() {
        prefs.edit().remove(KEY_SNAPSHOT).apply()
    }

    private companion object {
        const val PREFS_NAME = "lumi_player"
        const val KEY_SNAPSHOT = "snapshot_v1"
    }
}
