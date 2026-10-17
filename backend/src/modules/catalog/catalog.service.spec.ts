import { ConfigService } from '@nestjs/config';
import { Test, TestingModule } from '@nestjs/testing';

import { PrismaService } from '../../prisma/prisma.service';
import { CacheService } from '../cache/cache.service';
import { CatalogCacheService } from './catalog-cache.service';
import { CatalogService } from './catalog.service';

describe('CatalogService', () => {
  let service: CatalogService;
  let cache: CacheService;
  let prisma: {
    client: {
      track: {
        findMany: jest.Mock;
        count: jest.Mock;
      };
    };
  };

  const tracks = [
    {
      id: 't1',
      title: 'Song',
      playCount: 5,
      artist: { id: 'a1', name: 'Artist' },
    },
  ];

  beforeEach(async () => {
    prisma = {
      client: {
        track: {
          findMany: jest.fn().mockResolvedValue(tracks),
          count: jest.fn().mockResolvedValue(1),
        },
      },
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        CatalogService,
        CacheService,
        CatalogCacheService,
        {
          provide: ConfigService,
          useValue: { get: jest.fn() },
        },
        {
          provide: PrismaService,
          useValue: prisma,
        },
      ],
    }).compile();

    service = module.get(CatalogService);
    cache = module.get(CacheService);
  });

  it('reads the catalog from DB on first call and from cache on second', async () => {
    const query = { sort: 'popular' as const, page: 1, limit: 20 };

    const first = await service.getCatalog(query);
    const second = await service.getCatalog(query);

    expect(first.items).toEqual(tracks);
    expect(second.items).toEqual(tracks);

    // Другий виклик не ходить у базу — беремо з кешу.
    expect(prisma.client.track.findMany).toHaveBeenCalledTimes(1);
    expect(prisma.client.track.count).toHaveBeenCalledTimes(1);
  });

  it('bypasses the cache when useCache=false', async () => {
    const query = { sort: 'popular' as const, page: 1, limit: 20 };

    await service.getCatalog(query, false);
    await service.getCatalog(query, false);

    // Кеш не використовується — кожен раз читаємо з бази.
    expect(prisma.client.track.findMany).toHaveBeenCalledTimes(2);
    expect(prisma.client.track.count).toHaveBeenCalledTimes(2);
  });

  it('falls back to the DB when the cache is unavailable', async () => {
    // Ламаємо кеш, щоб перевірити fallback на базу.
    jest.spyOn(cache, 'getOrLoad').mockRejectedValue(new Error('cache down'));

    const query = { sort: 'popular' as const, page: 1, limit: 20 };

    const result = await service.getCatalog(query);

    // Відповідь є, помилки немає — взяли з бази.
    expect(result.items).toEqual(tracks);
    expect(prisma.client.track.findMany).toHaveBeenCalledTimes(1);
  });

  it('uses a different cache key for a different page', async () => {
    const query1 = { sort: 'popular' as const, page: 1, limit: 20 };
    const query2 = { sort: 'popular' as const, page: 2, limit: 20 };

    await service.getCatalog(query1);
    await service.getCatalog(query2);
    await service.getCatalog(query1);
    await service.getCatalog(query2);

    // Дві різні сторінки = дві різні відповіді з бази, потім кеш.
    expect(prisma.client.track.findMany).toHaveBeenCalledTimes(2);
    expect(prisma.client.track.count).toHaveBeenCalledTimes(2);
  });

  it('caches popular and new releases separately', async () => {
    await service.getPopular(5);
    await service.getPopular(5);
    await service.getNewReleases(5);
    await service.getNewReleases(5);

    // По одному запиту в базу на кожну стрічку, далі кеш.
    expect(prisma.client.track.findMany).toHaveBeenCalledTimes(2);
  });

  it('caches the discovery feed', async () => {
    await service.getDiscoveryFeed();
    await service.getDiscoveryFeed();

    // Стрічка = popular + newReleases, читаємо з бази лише один раз.
    expect(prisma.client.track.findMany).toHaveBeenCalledTimes(2);
  });

  it('does not cache when the discovery feed bypasses cache', async () => {
    await service.getDiscoveryFeed(false);
    await service.getDiscoveryFeed(false);

    expect(prisma.client.track.findMany).toHaveBeenCalledTimes(4);
  });

  it('records cache hits and misses for the catalog namespace', async () => {
    const query = { sort: 'popular' as const, page: 1, limit: 20 };

    await service.getCatalog(query); // miss
    await service.getCatalog(query); // hit
    await service.getCatalog(query); // hit

    const stats = cache.getStats().find((s) => s.namespace === 'catalog');

    expect(stats).toEqual({
      namespace: 'catalog',
      hits: 2,
      misses: 1,
      hitRate: 0.67,
    });
  });
});
