import { Injectable, OnModuleInit } from '@nestjs/common';

import {
  AlbumUpdatedEventPayload,
  ArtistUpdatedEventPayload,
  CACHE_EVENTS,
  TrackUpdatedEventPayload,
  UserActivityEventPayload,
} from './cache.event';
import { CacheEventBus } from './cache.event-bus';
import { CacheService } from './cache.service';

@Injectable()
export class CacheInvalidationListener implements OnModuleInit {
  constructor(
    private readonly cache: CacheService,
    private readonly eventBus: CacheEventBus,
  ) {}

  onModuleInit(): void {
    this.eventBus.on(
      CACHE_EVENTS.trackUpdated,
      (payload: TrackUpdatedEventPayload) => {
        this.cache.invalidateKey('track', payload.trackId);
      },
    );

    this.eventBus.on(
      CACHE_EVENTS.artistUpdated,
      (payload: ArtistUpdatedEventPayload) => {
        this.cache.invalidateKey('artist', payload.artistId);
      },
    );

    this.eventBus.on(
      CACHE_EVENTS.albumUpdated,
      (payload: AlbumUpdatedEventPayload) => {
        this.cache.invalidateKey('album', payload.albumId);
      },
    );

    this.eventBus.on(CACHE_EVENTS.trackStatusChanged, () => {
      this.cache.invalidate('discovery');
      this.cache.invalidate('catalog');
    });

    this.eventBus.on(
      CACHE_EVENTS.userActivity,
      (payload: UserActivityEventPayload) => {
        this.cache.invalidateKey('recommendation', payload.userId);
      },
    );
  }
}
