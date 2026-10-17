package com.lumi.app.util

import com.lumi.app.data.model.Track

/** Лишає лише треки жанру [genre] (без урахування регістру й крайніх пробілів). */
fun filterByGenre(tracks: List<Track>, genre: String): List<Track> {
    val wanted = genre.trim()
    return tracks.filter { it.genre?.trim().equals(wanted, ignoreCase = true) }
}
