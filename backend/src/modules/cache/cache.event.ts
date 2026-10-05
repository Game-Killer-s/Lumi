export const CACHE_EVENTS = {
  trackUpdated: 'cache.track.updated',
  artistUpdated: 'cache.artist.updated',
  albumUpdated: 'cache.album.updated',
  trackStatusChanged: 'cache.track.status.changed',
  userActivity: 'cache.user.activity',
} as const;

export type TrackStatus = 'DRAFT' | 'ON_MODERATION' | 'PUBLISHED' | 'BLOCKED';

export interface TrackUpdatedEventPayload {
  trackId: string;
}

export interface ArtistUpdatedEventPayload {
  artistId: string;
}

export interface AlbumUpdatedEventPayload {
  albumId: string;
}

export interface TrackStatusChangedEventPayload {
  trackId: string;
  status: TrackStatus;
}

export type UserActivityType = 'like' | 'subscription' | 'history';

export interface UserActivityEventPayload {
  userId: string;
  type: UserActivityType;
}
