package com.lumi.app.data.model

import com.google.gson.annotations.SerializedName

// ==================== AUTH MODELS ====================

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class RegisterRequest(
    @SerializedName("nickname") val nickname: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class ForgotPasswordRequest(
    @SerializedName("email") val email: String
)

data class ForgotPasswordResponse(
    @SerializedName("message") val message: String
)

data class GoogleAuthRequest(
    @SerializedName("idToken") val idToken: String
)

data class RefreshTokenRequest(
    @SerializedName("refreshToken") val refreshToken: String
)

data class AuthResponse(
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("user") val user: UserProfile? = null
)

// ==================== USER MODELS ====================

data class UserProfile(
    @SerializedName("id") val id: String,
    @SerializedName("nickname") val nickname: String,
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

// ==================== NOTIFICATION MODELS ====================

data class NotificationSettings(
    @SerializedName("newReleases") val newReleases: Boolean = true,
    @SerializedName("artistUpdates") val artistUpdates: Boolean = true,
    @SerializedName("platformUpdates") val platformUpdates: Boolean = true,
    @SerializedName("pushEnabled") val pushEnabled: Boolean = true,
    @SerializedName("emailEnabled") val emailEnabled: Boolean = false
)

data class UpdateNotificationSettingsRequest(
    @SerializedName("newReleases") val newReleases: Boolean? = null,
    @SerializedName("artistUpdates") val artistUpdates: Boolean? = null,
    @SerializedName("platformUpdates") val platformUpdates: Boolean? = null,
    @SerializedName("pushEnabled") val pushEnabled: Boolean? = null,
    @SerializedName("emailEnabled") val emailEnabled: Boolean? = null
)

data class NotificationItem(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String = "new_release",
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String = "",
    @SerializedName("isRead") val isRead: Boolean = false,
    @SerializedName("createdAt") val createdAt: String? = null
)

data class NotificationListResponse(
    @SerializedName("items") val items: List<NotificationItem> = emptyList(),
    @SerializedName("unread") val unread: Int = 0
)

data class UpdatedCountResponse(
    @SerializedName("updated") val updated: Int = 0
)

data class DeletedResponse(
    @SerializedName("deleted") val deleted: Boolean = true
)

// ==================== SUBSCRIPTION MODELS ====================

data class SubscriptionPlan(
    @SerializedName("id") val id: String,
    @SerializedName("code") val code: String = "",
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("priceCents") val priceCents: Int = 0,
    @SerializedName("currency") val currency: String = "UAH",
    @SerializedName("periodDays") val periodDays: Int = 30
)

// Можливості тарифу: сервер віддає їх разом зі статусом підписки.
data class EntitlementResponse(
    @SerializedName("tier") val tier: String = "FREE",
    @SerializedName("maxQuality") val maxQuality: String = "STANDARD",
    @SerializedName("canUseHq") val canUseHq: Boolean = false,
    @SerializedName("canUseLossless") val canUseLossless: Boolean = false,
    @SerializedName("canDownload") val canDownload: Boolean = false
)

data class SubscriptionStatusResponse(
    @SerializedName("hasSubscription") val hasSubscription: Boolean = false,
    @SerializedName("status") val status: String = "FREE",
    @SerializedName("entitlement") val entitlement: EntitlementResponse? = null,
    @SerializedName("title") val title: String = "",
    @SerializedName("message") val message: String = "",
    @SerializedName("plan") val plan: SubscriptionPlan? = null,
    @SerializedName("currentPeriodEnd") val currentPeriodEnd: String? = null,
    @SerializedName("gracePeriodEndsAt") val gracePeriodEndsAt: String? = null,
    @SerializedName("cancelAtPeriodEnd") val cancelAtPeriodEnd: Boolean = false,
    @SerializedName("nextChargeDate") val nextChargeDate: String? = null,
    @SerializedName("nextChargeAmountCents") val nextChargeAmountCents: Int? = null,
    @SerializedName("paymentsCount") val paymentsCount: Int = 0
)

data class PlayAccessResponse(
    @SerializedName("allowed") val allowed: Boolean = true,
    @SerializedName("quality") val quality: String = "STANDARD",
    @SerializedName("status") val status: String = "FREE",
    @SerializedName("message") val message: String = ""
)

data class PaymentHistoryItem(
    @SerializedName("id") val id: String,
    @SerializedName("amountCents") val amountCents: Int = 0,
    @SerializedName("currency") val currency: String = "UAH",
    @SerializedName("status") val status: String = "PENDING",
    @SerializedName("failureReason") val failureReason: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null
)

data class PaymentMethod(
    @SerializedName("id") val id: String,
    @SerializedName("brand") val brand: String = "CARD",
    @SerializedName("last4") val last4: String = "",
    @SerializedName("expMonth") val expMonth: Int = 1,
    @SerializedName("expYear") val expYear: Int = 2026,
    @SerializedName("holderName") val holderName: String? = null,
    @SerializedName("isDefault") val isDefault: Boolean = false
)

data class CheckoutRequest(
    @SerializedName("planId") val planId: String,
    @SerializedName("provider") val provider: String = "CARD",
    @SerializedName("cardNumber") val cardNumber: String? = null,
    @SerializedName("holderName") val holderName: String? = null,
    @SerializedName("expMonth") val expMonth: Int? = null,
    @SerializedName("expYear") val expYear: Int? = null,
    @SerializedName("cvc") val cvc: String? = null,
    @SerializedName("paymentToken") val paymentToken: String? = null
)

data class AddPaymentMethodRequest(
    @SerializedName("cardNumber") val cardNumber: String,
    @SerializedName("holderName") val holderName: String? = null,
    @SerializedName("expMonth") val expMonth: Int,
    @SerializedName("expYear") val expYear: Int,
    @SerializedName("cvc") val cvc: String? = null
)

data class UpdatePaymentMethodRequest(
    @SerializedName("holderName") val holderName: String? = null,
    @SerializedName("expMonth") val expMonth: Int? = null,
    @SerializedName("expYear") val expYear: Int? = null,
    @SerializedName("isDefault") val isDefault: Boolean? = null
)

data class SubscriptionRecord(
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: String = "PENDING",
    @SerializedName("currentPeriodEnd") val currentPeriodEnd: String? = null,
    @SerializedName("cancelAtPeriodEnd") val cancelAtPeriodEnd: Boolean = false
)

data class PaymentInfo(
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: String = "PENDING",
    @SerializedName("failureReason") val failureReason: String? = null
)

data class CheckoutResponse(
    @SerializedName("subscription") val subscription: SubscriptionRecord? = null,
    @SerializedName("payment") val payment: PaymentInfo? = null
)

data class CancelSubscriptionResponse(
    @SerializedName("subscription") val subscription: SubscriptionRecord? = null,
    @SerializedName("cancelAtPeriodEnd") val cancelAtPeriodEnd: Boolean = true
)
