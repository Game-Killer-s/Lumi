package com.lumi.app.player

import com.lumi.app.data.model.Track

/**
 * Pure queue-building logic, split out of [PlayerController] so it can be
 * unit-tested without an Android/ExoPlayer runtime.
 */
object QueueBuilder {

    /** Removes tracks with a duplicate id, keeping the first occurrence. */
    fun dedupeById(tracks: List<Track>): List<Track> = tracks.distinctBy { it.id }

    /**
     * De-duplicates then shuffles. For 3+ distinct tracks it retries (a
     * few times) if the shuffle happens to reproduce the original order,
     * so "Shuffle" is never a no-op play-in-order for a properly sized
     * playlist.
     */
    fun shuffledQueue(tracks: List<Track>, maxRetries: Int = 5): List<Track> {
        val deduped = dedupeById(tracks)
        if (deduped.size <= 2) return deduped.shuffled()

        var shuffled = deduped.shuffled()
        var attempts = 0
        while (shuffled == deduped && attempts < maxRetries) {
            shuffled = deduped.shuffled()
            attempts++
        }
        return shuffled
    }
}
