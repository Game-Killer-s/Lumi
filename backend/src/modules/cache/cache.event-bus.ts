import { Injectable } from '@nestjs/common';
import { EventEmitter } from 'node:events';

import {
  AlbumUpdatedEventPayload,
  ArtistUpdatedEventPayload,
  CACHE_EVENTS,
  TrackStatusChangedEventPayload,
  TrackUpdatedEventPayload,
  UserActivityEventPayload,
} from './cache.event';

@Injectable()
export class CacheEventBus extends EventEmitter {
  emitTrackUpdated(payload: TrackUpdatedEventPayload): void {
    this.emit(CACHE_EVENTS.trackUpdated, payload);
  }

  emitArtistUpdated(payload: ArtistUpdatedEventPayload): void {
    this.emit(CACHE_EVENTS.artistUpdated, payload);
  }

  emitAlbumUpdated(payload: AlbumUpdatedEventPayload): void {
    this.emit(CACHE_EVENTS.albumUpdated, payload);
  }

  emitTrackStatusChanged(payload: TrackStatusChangedEventPayload): void {
    this.emit(CACHE_EVENTS.trackStatusChanged, payload);
  }

  emitUserActivity(payload: UserActivityEventPayload): void {
    this.emit(CACHE_EVENTS.userActivity, payload);
  }
}
