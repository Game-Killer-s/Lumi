package com.lumi.app.data.repository

import com.lumi.app.data.local.HistoryEntry
import com.lumi.app.data.local.LibraryLocalStore
import com.lumi.app.data.local.LibrarySnapshot
import com.lumi.app.data.model.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * Історія прослуховувань зберігається локально (backend не повертає час
 * прослуховування). Один трек в історії не дублюється — повторний запуск
 * піднімає його на початок.
 */
class HistoryRepository {

    fun observe(): StateFlow<LibrarySnapshot> = LibraryLocalStore.state

    fun entries(): List<HistoryEntry> = LibraryLocalStore.history()

    fun record(track: Track, playedAt: Long = System.currentTimeMillis()) {
        LibraryLocalStore.recordHistory(track, playedAt)
    }

    fun clear() {
        LibraryLocalStore.clearHistory()
    }
}

