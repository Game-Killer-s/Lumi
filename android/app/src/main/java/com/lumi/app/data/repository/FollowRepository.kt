package com.lumi.app.data.repository

import com.lumi.app.data.local.LibraryLocalStore
import com.lumi.app.data.local.LibrarySnapshot
import com.lumi.app.data.model.Artist
import com.lumi.app.network.ApiResult
import com.lumi.app.state.LibraryStateHolder
import kotlinx.coroutines.flow.StateFlow

/**
 * Підписки на виконавців. Список зберігається локально: backend поки що
 * повертає лише прапорець isFollowed у відповіді, без окремого списку.
 */
class FollowRepository(
    private val userRepository: UserRepository = UserRepository()
) {

    fun observe(): StateFlow<LibrarySnapshot> = LibraryLocalStore.state

    fun artists(): List<Artist> = LibraryLocalStore.followedArtists()

    /** Освіжає довідник виконавців і повертає локальний список підписок. */
    suspend fun refresh(): ApiResult<List<Artist>> {
        val result = userRepository.getArtists(page = 1, limit = 100)
        if (result is ApiResult.Success) {
            val followed = result.data.artists.filter { it.isFollowed }
            LibraryLocalStore.replaceFollowedFromServer(followed)
            LibraryStateHolder.seedFollowed(followed.map { it.id })
        }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(artists())
            is ApiResult.Error -> ApiResult.Error(result.error)
        }
    }

    /**
     * Оптимістично змінює підписку та підтверджує її запитом. Якщо запит
     * не вдався, стан відкочується, щоб кнопка не брехала користувачу.
     */
    suspend fun setFollowed(artist: Artist, followed: Boolean): ApiResult<Unit> {
        applyLocally(artist, followed)
        val result = if (followed) {
            userRepository.followArtist(artist.id)
        } else {
            userRepository.unfollowArtist(artist.id)
        }
        if (result is ApiResult.Error) applyLocally(artist, !followed)
        return result
    }

    fun applyLocally(artist: Artist, followed: Boolean) {
        if (followed) {
            LibraryLocalStore.followArtist(artist)
            LibraryStateHolder.setFollowed(artist.id, true)
        } else {
            LibraryLocalStore.unfollowArtist(artist.id)
            LibraryStateHolder.setFollowed(artist.id, false)
        }
    }
}
