import { NotFoundException } from '@nestjs/common';
import { TrackStatus } from '@prisma/client';

import { CatalogCacheService } from './catalog-cache.service';
import { PrismaService } from '../../prisma/prisma.service';
import { CatalogService } from './catalog.service';

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

describe('CatalogService', () => {
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
});
