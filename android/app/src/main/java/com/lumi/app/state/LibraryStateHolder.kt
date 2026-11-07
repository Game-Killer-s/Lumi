package com.lumi.app.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide source of truth for "is this track liked" / "is this
 * artist followed", so the track context menu, Home carousels, Playlist
 * /Album/Artist screens and the mini player all agree on the same state
 * without each screen re-fetching it.
 *
 * Screens seed this from list responses (Track.isLiked / Artist.isFollowed)
 * and then mutate it optimistically on user actions; repositories are the
 * source of truth for the actual network call/result.
 */
object LibraryStateHolder {

    private val _likedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val likedTrackIds: StateFlow<Set<String>> = _likedTrackIds

    /**
     * Треки, для яких зараз виконується запит лайка/анлайка. Поки запит у
     * польоті, відповідь сервера може бути старішою за оптимістичний стан,
     * тому таку відповідь не застосовуємо.
     */
    private val _pendingLikeIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingLikeIds: StateFlow<Set<String>> = _pendingLikeIds

    /** Скільки запитів у польоті для кожного треку (подвійний тап теж обробляємо). */
    private val pendingLikeCounts = mutableMapOf<String, Int>()

    /**
     * Лічильник локальних змін лайків: дозволяє відкинути відповідь сервера,
     * яку отримали вже після того, як користувач встиг щось перемкнути.
     */
    @Volatile
    private var likeRevision = 0L

    private val _followedArtistIds = MutableStateFlow<Set<String>>(emptySet())
    val followedArtistIds: StateFlow<Set<String>> = _followedArtistIds

    /** Seed initial liked/followed ids from a freshly-loaded list, without clobbering other entries. */
    fun seedLiked(ids: Collection<String>, liked: Boolean = true) {
        if (!liked) return
        _likedTrackIds.update { it + ids }
    }

    fun seedFollowed(ids: Collection<String>, followed: Boolean = true) {
        if (!followed) return
        _followedArtistIds.update { it + ids }
    }

    fun isLiked(trackId: String): Boolean = trackId in _likedTrackIds.value

    fun isFollowed(artistId: String): Boolean = artistId in _followedArtistIds.value

    /** Поточна «версія» локального стану лайків; запити фіксують її перед стартом. */
    fun likedRevision(): Long = likeRevision

    /**
     * Повністю синхронізує набір улюблених із сервером.
     *
     * На відміну від [seedLiked] це авторитетна відповідь: треки, яких немає
     * в [ids], прибираються зі стану. Викликати лише для повного списку
     * (коли `total <= tracks.size`) і коли немає запитів у польоті.
     *
     * [revisionAtRequest] — значення [likedRevision] на момент старту запиту:
     * якщо користувач щось перемкнув, поки запит летів, відповідь застаріла.
     */
    fun applyLikedSnapshot(ids: Collection<String>, revisionAtRequest: Long? = null) {
        if (revisionAtRequest != null && revisionAtRequest != likeRevision) return
        if (_pendingLikeIds.value.isNotEmpty()) return

        val serverIds = ids.toSet()
        _likedTrackIds.update { current ->
            // Оптимістичні зміни, що ще не підтверджені сервером, зберігаємо.
            val pending = _pendingLikeIds.value
            (serverIds - pending) + current.filter { it in pending }
        }
    }

    /** Позначає, що для треку виконується запит лайка (або знімає позначку). */
    fun markLikePending(trackId: String, pending: Boolean) {
        synchronized(pendingLikeCounts) {
            val count = (pendingLikeCounts[trackId] ?: 0) + if (pending) 1 else -1
            if (count > 0) pendingLikeCounts[trackId] = count else pendingLikeCounts.remove(trackId)
            _pendingLikeIds.value = pendingLikeCounts.keys.toSet()
        }
    }

    /** Optimistic toggle — call before the API result comes back, then [revertLike] if it fails. */
    fun setLiked(trackId: String, liked: Boolean) {
        likeRevision++
        _likedTrackIds.update { if (liked) it + trackId else it - trackId }
    }

    fun revertLike(trackId: String, wasLiked: Boolean) = setLiked(trackId, wasLiked)

    fun setFollowed(artistId: String, followed: Boolean) {
        _followedArtistIds.update { if (followed) it + artistId else it - artistId }
    }

    fun revertFollow(artistId: String, wasFollowed: Boolean) = setFollowed(artistId, wasFollowed)

    /**
     * Замінює обидва набори після зміни акаунта або завантаження локальної
     * медіатеки. Використовується лише сховищем [com.lumi.app.data.local.LibraryLocalStore].
     */
    fun replaceAll(likedIds: Collection<String>, followedIds: Collection<String>) {
        likeRevision++
        _likedTrackIds.value = likedIds.toSet()
        _followedArtistIds.value = followedIds.toSet()
    }
}
