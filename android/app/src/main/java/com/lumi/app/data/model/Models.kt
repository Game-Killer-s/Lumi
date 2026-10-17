package com.lumi.app.data.model

import com.google.gson.annotations.SerializedName

// ==================== AUTH MODELS ====================

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class RegisterRequest(
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class RefreshTokenRequest(
    @SerializedName("refreshToken") val refreshToken: String
)

data class AuthResponse(
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("user") val user: UserProfile
)

// ==================== USER MODELS ====================

data class UserProfile(
    @SerializedName("id") val id: String,
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("role") val role: String = "listener",
    @SerializedName("createdAt") val createdAt: String? = null
)

data class UpdateProfileRequest(
    @SerializedName("username") val username: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null
)

// ==================== TRACK MODELS ====================

data class Track(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: Artist?,
    @SerializedName("artistName") val artistName: String? = null,
    @SerializedName("album") val album: String? = null,
    @SerializedName("albumCoverUrl") val albumCoverUrl: String? = null,
    @SerializedName("coverUrl") val coverUrl: String? = null,
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("audioUrl") val audioUrl: String? = null,
    @SerializedName("playCount") val playCount: Int = 0,
    @SerializedName("likesCount") val likesCount: Int = 0,
    @SerializedName("isLiked") val isLiked: Boolean = false
)

data class TrackListResponse(
    @SerializedName("tracks") val tracks: List<Track>,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("limit") val limit: Int = 20
)

// ==================== ARTIST MODELS ====================

data class Artist(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("monthlyListeners") val monthlyListeners: Int = 0,
    @SerializedName("isFollowed") val isFollowed: Boolean = false
)

data class ArtistListResponse(
    @SerializedName("artists") val artists: List<Artist>,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("limit") val limit: Int = 20
)

// ==================== PLAYLIST MODELS ====================

data class Playlist(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("coverUrl") val coverUrl: String? = null,
    @SerializedName("owner") val owner: UserProfile? = null,
    @SerializedName("ownerName") val ownerName: String? = null,
    @SerializedName("tracks") val tracks: List<Track>? = null,
    @SerializedName("tracksCount") val tracksCount: Int = 0,
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("isPublic") val isPublic: Boolean = true,
    @SerializedName("createdAt") val createdAt: String? = null
)

data class PlaylistListResponse(
    @SerializedName("playlists") val playlists: List<Playlist>,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("limit") val limit: Int = 20
)

data class CreatePlaylistRequest(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("isPublic") val isPublic: Boolean = true
)

data class UpdatePlaylistRequest(
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("isPublic") val isPublic: Boolean? = null
)

data class AddTrackRequest(
    @SerializedName("trackId") val trackId: String
)

// ==================== GENRE MODELS ====================

data class Genre(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("color") val color: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null
)

data class GenreListResponse(
    @SerializedName("genres") val genres: List<Genre>
)

// ==================== HISTORY MODELS ====================

data class AddHistoryRequest(
    @SerializedName("trackId") val trackId: String
)

// ==================== ALBUM MODELS ====================
// NOTE: backend does not expose a dedicated /albums resource yet.
// This mirrors the Playlist contract 1:1 so the UI can be built now;
// TODO(backend): replace AlbumRepository's playlist-endpoint fallback
// once real /albums endpoints exist (see AlbumRepository.kt).

data class Album(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: Artist?,
    @SerializedName("artistName") val artistName: String? = null,
    @SerializedName("coverUrl") val coverUrl: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("tracks") val tracks: List<Track>? = null,
    @SerializedName("tracksCount") val tracksCount: Int = 0,
    @SerializedName("duration") val duration: Int = 0
)

data class AlbumListResponse(
    @SerializedName("albums") val albums: List<Album>,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("limit") val limit: Int = 20
)

// ==================== REPORT MODELS ====================
// TODO(backend): no /report endpoint exists yet. Kept here so the UI
// (reason picker in the track context menu) has a stable contract to
// build against; ReportRepository currently just logs the request.

enum class ReportReason(val label: String) {
    EXPLICIT_CONTENT("Неприйнятний контент"),
    COPYRIGHT("Порушення авторських прав"),
    WRONG_METADATA("Некоректні дані про трек"),
    SPAM("Спам"),
    OTHER("Інше")
}

data class ReportTrackRequest(
    @SerializedName("trackId") val trackId: String,
    @SerializedName("reason") val reason: String,
    @SerializedName("comment") val comment: String? = null
)
