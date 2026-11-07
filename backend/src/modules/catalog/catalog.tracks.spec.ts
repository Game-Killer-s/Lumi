import { NotFoundException } from '@nestjs/common';
import { TrackStatus } from '@prisma/client';

import { PrismaService } from '../../prisma/prisma.service';
import { CacheService } from '../cache/cache.service';
import { CatalogCacheService } from './catalog-cache.service';
import { CatalogService } from './catalog.service';

/**
 * Тести тієї частини CatalogService, яка прийшла з main (PR #5, content-admin):
 * публічні /tracks endpoints. Слухач бачить лише PUBLISHED контент.
 *
 * Друга частина сервісу (каталог/discovery з shared-кешем) перевіряється
 * у catalog.service.spec.ts.
 */

/** Мок PrismaService, типізований через jest.Mock */
type PrismaMock = {
  track: {
    findMany: jest.Mock;
    findFirst: jest.Mock;
  };
};

/** Мок CatalogCacheService */
type CacheMock = {
  get: jest.Mock;
  set: jest.Mock;
  invalidateCatalog: jest.Mock;
};

describe('CatalogService (public /tracks part from main)', () => {
  let service: CatalogService;
  let prisma: PrismaMock;
  let cache: CacheMock;

  const publishedTrack = {
    id: 'track-1',
    title: 'Published Song',
    status: TrackStatus.PUBLISHED,
    audioUrl: 'https://cdn.lumi.app/a.mp3',
  };

  beforeEach(() => {
    prisma = {
      track: {
        findMany: jest.fn(),
        findFirst: jest.fn(),
      },
    };

    cache = {
      get: jest.fn().mockReturnValue(undefined),
      set: jest.fn(),
      invalidateCatalog: jest.fn(),
    };

    service = new CatalogService(
      prisma as unknown as PrismaService,
      cache as unknown as CacheService,
      cache as unknown as CatalogCacheService,
    );
  });

  describe('listener sees only published/available content', () => {
    it('filters catalog by PUBLISHED status', async () => {
      prisma.track.findMany.mockResolvedValue([publishedTrack]);

      const result = await service.findAll();

      expect(prisma.track.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            status: TrackStatus.PUBLISHED,
          }),
        }),
      );

      expect(result).toEqual([publishedTrack]);
    });

    it('filters search results by PUBLISHED status', async () => {
      prisma.track.findMany.mockResolvedValue([publishedTrack]);

      await service.search('song');

      expect(prisma.track.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            status: TrackStatus.PUBLISHED,
          }),
        }),
      );
    });

    it('does not return a blocked track', async () => {
      prisma.track.findFirst.mockResolvedValue(null);

      await expect(service.findOne('blocked-id')).rejects.toThrow(
        NotFoundException,
      );
    });

    it('does not stream a blocked track', async () => {
      prisma.track.findFirst.mockResolvedValue(null);

      await expect(service.getStream('blocked-id')).rejects.toThrow(
        NotFoundException,
      );
    });

    it('uses cache and stores catalog result', async () => {
      prisma.track.findMany.mockResolvedValue([publishedTrack]);

      await service.findAll();

      expect(cache.get).toHaveBeenCalledWith('catalog:all');
      expect(cache.set).toHaveBeenCalledWith('catalog:all', [publishedTrack]);
    });
  });

  // Стрічки, які читає Android-клієнт (TracksApi.getPopularTracks/getNewTracks).
  // Контракт: TrackListResponse { tracks, total, page, limit }, а поля
  // genre/album — пласкі рядки (див. data/model/Models.kt у клієнті).
  describe('мобільні стрічки /tracks/popular та /tracks/new', () => {
    const feedTrack = {
      id: 'track-1',
      title: 'Neon Overflow',
      duration: 16,
      coverUrl: '/media/cover/demo/neon-nights.png',
      audioUrl: '/media/audio/demo/neon-overflow.wav',
      playCount: 1280,
      likeCount: 84,
      status: TrackStatus.PUBLISHED,
      artist: {
        id: 'artist-1',
        name: 'Neon Sky',
        photoUrl: null,
        description: 'Синтвейв і ретро-електроніка',
      },
      genre: { id: 'genre-1', name: 'Electronic', color: '#40C4FF' },
      album: {
        id: 'album-1',
        title: 'Neon Nights',
        coverUrl: '/media/cover/demo/neon-nights.png',
      },
    };

    it('returns popular tracks wrapped into TrackListResponse', async () => {
      prisma.track.findMany.mockResolvedValue([feedTrack]);

      const feed = await service.getPopularFeed(4);

      expect(prisma.track.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({ status: TrackStatus.PUBLISHED }),
          orderBy: { playCount: 'desc' },
          take: 4,
        }),
      );

      expect(feed).toEqual({
        tracks: [
          {
            id: 'track-1',
            title: 'Neon Overflow',
            artist: {
              id: 'artist-1',
              name: 'Neon Sky',
              imageUrl: null,
              bio: 'Синтвейв і ретро-електроніка',
            },
            artistName: 'Neon Sky',
            album: 'Neon Nights',
            albumId: 'album-1',
            albumCoverUrl: '/media/cover/demo/neon-nights.png',
            coverUrl: '/media/cover/demo/neon-nights.png',
            duration: 16,
            genre: 'Electronic',
            audioUrl: '/media/audio/demo/neon-overflow.wav',
            playCount: 1280,
            likesCount: 84,
            isLiked: false,
          },
        ],
        total: 1,
        page: 1,
        limit: 4,
      });
    });

    it('returns new releases ordered by createdAt desc', async () => {
      prisma.track.findMany.mockResolvedValue([]);

      const feed = await service.getNewFeed(10);

      expect(prisma.track.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({ status: TrackStatus.PUBLISHED }),
          orderBy: { createdAt: 'desc' },
          take: 10,
        }),
      );
      expect(feed).toEqual({ tracks: [], total: 0, page: 1, limit: 10 });
    });

    it('keeps nulls when artist/album/genre are missing', async () => {
      prisma.track.findMany.mockResolvedValue([
        {
          ...feedTrack,
          artist: null,
          genre: null,
          album: null,
          coverUrl: null,
          audioUrl: null,
        },
      ]);

      const feed = await service.getPopularFeed(1);

      expect(feed.tracks[0]).toMatchObject({
        artist: null,
        artistName: null,
        album: null,
        albumId: null,
        albumCoverUrl: null,
        genre: null,
        audioUrl: null,
      });
    });
  });
});
