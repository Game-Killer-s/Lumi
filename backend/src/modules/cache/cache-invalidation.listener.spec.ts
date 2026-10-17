import { ConfigService } from '@nestjs/config';
import { Test, TestingModule } from '@nestjs/testing';

import { CacheEventBus } from './cache.event-bus';
import { CacheInvalidationListener } from './cache-invalidation.listener';
import { CacheService } from './cache.service';

describe('CacheInvalidationListener', () => {
  let cache: CacheService;
  let eventBus: CacheEventBus;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        CacheService,
        CacheEventBus,
        CacheInvalidationListener,
        {
          provide: ConfigService,
          useValue: { get: jest.fn() },
        },
      ],
    }).compile();

    cache = module.get(CacheService);
    eventBus = module.get(CacheEventBus);

    module.get(CacheInvalidationListener).onModuleInit();
  });

  it('invalidates the track cache when a track is updated', () => {
    cache.set('track', 't1', { title: 'Old' });
    cache.set('track', 't2', { title: 'Untouched' });

    eventBus.emitTrackUpdated({ trackId: 't1' });

    expect(cache.get('track', 't1')).toBeNull();
    expect(cache.get('track', 't2')).toEqual({ title: 'Untouched' });
  });

  it('invalidates the artist cache when an artist is updated', () => {
    cache.set('artist', 'a1', { name: 'Old' });

    eventBus.emitArtistUpdated({ artistId: 'a1' });

    expect(cache.get('artist', 'a1')).toBeNull();
  });

  it('invalidates the album cache when an album is updated', () => {
    cache.set('album', 'al1', { title: 'Old' });

    eventBus.emitAlbumUpdated({ albumId: 'al1' });

    expect(cache.get('album', 'al1')).toBeNull();
  });

  it('invalidates the discovery cache when a track is published', () => {
    cache.set('discovery', 'home', [{ id: 't1' }]);

    eventBus.emitTrackStatusChanged({ trackId: 't1', status: 'PUBLISHED' });

    expect(cache.get('discovery', 'home')).toBeNull();
  });

  it('invalidates the catalog cache when a track is published', () => {
    cache.set('catalog', 'catalog:all:popular:1:20', [{ id: 't1' }]);

    eventBus.emitTrackStatusChanged({ trackId: 't1', status: 'PUBLISHED' });

    expect(cache.get('catalog', 'catalog:all:popular:1:20')).toBeNull();
  });

  it('invalidates the discovery cache when a track is blocked', () => {
    cache.set('discovery', 'home', [{ id: 't1' }]);

    eventBus.emitTrackStatusChanged({ trackId: 't1', status: 'BLOCKED' });

    expect(cache.get('discovery', 'home')).toBeNull();
  });

  it('invalidates the recommendation cache for a user after significant activity', () => {
    cache.set('recommendation', 'u1', [{ id: 't1' }]);
    cache.set('recommendation', 'u2', [{ id: 't2' }]);

    eventBus.emitUserActivity({ userId: 'u1', type: 'like' });

    expect(cache.get('recommendation', 'u1')).toBeNull();
    expect(cache.get('recommendation', 'u2')).toEqual([{ id: 't2' }]);
  });

  it('never serves blocked content from a warmed discovery cache', async () => {
    const tracks = [
      { id: 't1', status: 'PUBLISHED' },
      { id: 't2', status: 'BLOCKED' },
    ];

    const loadPublished = () =>
      Promise.resolve(tracks.filter((track) => track.status === 'PUBLISHED'));

    const first = await cache.getOrLoad('discovery', 'home', loadPublished);

    expect(first).toEqual([{ id: 't1', status: 'PUBLISHED' }]);

    eventBus.emitTrackStatusChanged({ trackId: 't2', status: 'BLOCKED' });

    expect(cache.get('discovery', 'home')).toBeNull();

    const second = await cache.getOrLoad('discovery', 'home', loadPublished);

    expect(second).toEqual([{ id: 't1', status: 'PUBLISHED' }]);
  });
});
