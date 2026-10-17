import { Injectable, NotFoundException } from '@nestjs/common';
import { Prisma, TrackStatus } from '@prisma/client';

import { PrismaService } from '../../prisma/prisma.service';
import { CacheService } from '../cache/cache.service';
import { CACHE_TTL } from '../cache/cache.constants';
import {
  catalogKey,
  discoveryKey,
  newReleasesKey,
  popularKey,
} from '../cache/cache.keys';
import { CatalogCacheService } from './catalog-cache.service';

export interface CatalogQuery {
  genreId?: string;
  sort: 'popular' | 'new' | 'title';
  page: number;
  limit: number;
}

// Сервіс каталогу і discovery стрічок.
// Схема для всіх методів однакова:
// 1. Дивимось у кеш.
// 2. Якщо є — повертаємо (cache hit).
// 3. Якщо немає — читаємо з бази і кладемо в кеш (cache miss).
// 4. Якщо кеш недоступний — fallback просто читаємо з бази.
@Injectable()
export class CatalogService {
  // Три залежності:
  // - prisma       — база;
  // - cache        — shared-кеш (каталог/discovery, TTL, статистика);
  // - catalogCache — простий in-memory кеш публічних /tracks endpoints,
  //                  який скидає модуль content після зміни статусу треку.
  constructor(
    private readonly prisma: PrismaService,
    private readonly cache: CacheService,
    private readonly catalogCache: CatalogCacheService,
  ) {}

  // Каталог: список треків з фільтрами (жанр, сортування, пагінація).
  async getCatalog(query: CatalogQuery, useCache = true) {
    const load = () => this.loadCatalog(query);

    if (!useCache) {
      return load();
    }

    try {
      return await this.cache.getOrLoad(
        'catalog',
        catalogKey(query),
        load,
        CACHE_TTL.catalog,
      );
    } catch {
      // Fallback на базу, якщо кеш недоступний.
      return load();
    }
  }

  // Популярні треки.
  async getPopular(limit: number, useCache = true) {
    const load = () => this.loadPopular(limit);

    if (!useCache) {
      return load();
    }

    try {
      return await this.cache.getOrLoad(
        'discovery',
        popularKey(limit),
        load,
        CACHE_TTL.popular,
      );
    } catch {
      return load();
    }
  }

  // Нові релізи.
  async getNewReleases(limit: number, useCache = true) {
    const load = () => this.loadNewReleases(limit);

    if (!useCache) {
      return load();
    }

    try {
      return await this.cache.getOrLoad(
        'discovery',
        newReleasesKey(limit),
        load,
        CACHE_TTL.newReleases,
      );
    } catch {
      return load();
    }
  }

  // Discovery стрічка: популярне + нові релізи в одному запиті.
  async getDiscoveryFeed(useCache = true) {
    const load = async () => ({
      popular: await this.loadPopular(5),
      newReleases: await this.loadNewReleases(5),
    });

    if (!useCache) {
      return load();
    }

    try {
      return await this.cache.getOrLoad(
        'discovery',
        discoveryKey('home'),
        load,
        CACHE_TTL.discovery,
      );
    } catch {
      return load();
    }
  }

  // ---------- Читання з бази (без кешу) ----------

  private wherePublished(genreId?: string): Prisma.TrackWhereInput {
    return {
      status: 'PUBLISHED',
      ...(genreId ? { genreId } : {}),
    };
  }

  private async loadCatalog(query: CatalogQuery) {
    const where = this.wherePublished(query.genreId);

    const orderBy: Prisma.TrackOrderByWithRelationInput =
      query.sort === 'new'
        ? { createdAt: 'desc' }
        : query.sort === 'title'
          ? { title: 'asc' }
          : { playCount: 'desc' };

    const [items, total] = await Promise.all([
      this.prisma.client.track.findMany({
        where,
        orderBy,
        skip: (query.page - 1) * query.limit,
        take: query.limit,
        include: { artist: true, genre: true },
      }),
      this.prisma.client.track.count({ where }),
    ]);

    return {
      items,
      total,
      page: query.page,
      limit: query.limit,
    };
  }

  private async loadPopular(limit: number) {
    return this.prisma.client.track.findMany({
      where: this.wherePublished(),
      orderBy: { playCount: 'desc' },
      take: limit,
      include: { artist: true, genre: true },
    });
  }

  private async loadNewReleases(limit: number) {
    return this.prisma.client.track.findMany({
      where: this.wherePublished(),
      orderBy: { createdAt: 'desc' },
      take: limit,
      include: { artist: true, genre: true },
    });
  }

  // ============================================================
  // Публічний каталог /tracks (прийшов з main, PR #5 content-admin).
  // Усі методи віддають тільки опубліковані треки (status = PUBLISHED):
  // заблоковані, неопубліковані або ще не готові сюди не потрапляють.
  // Кеш тут — CatalogCacheService, бо саме його скидає модуль content
  // після зміни статусу треку (lifecycle change, block/unblock).
  // ============================================================

  async findAll() {
    const cached = this.catalogCache.get<unknown>('catalog:all');
    if (cached) {
      return cached;
    }

    const tracks = await this.prisma.track.findMany({
      where: {
        status: TrackStatus.PUBLISHED,
      },
      include: {
        artist: true,
        genre: true,
        album: true,
      },
      orderBy: {
        createdAt: 'desc',
      },
    });

    this.catalogCache.set('catalog:all', tracks);
    return tracks;
  }

  async search(query: string) {
    if (!query?.trim()) {
      return this.findAll();
    }

    return this.prisma.track.findMany({
      where: {
        status: TrackStatus.PUBLISHED,
        title: {
          contains: query.trim(),
          mode: 'insensitive',
        },
      },
      include: {
        artist: true,
        genre: true,
        album: true,
      },
    });
  }

  async findOne(id: string) {
    const cached = this.catalogCache.get<unknown>(`catalog:track:${id}`);
    if (cached) {
      return cached;
    }

    const track = await this.prisma.track.findFirst({
      where: {
        id,
        status: TrackStatus.PUBLISHED,
      },
      include: {
        artist: true,
        genre: true,
        album: true,
      },
    });

    if (!track) {
      throw new NotFoundException('Track not found or not published');
    }

    this.catalogCache.set(`catalog:track:${id}`, track);
    return track;
  }

  async getStream(id: string) {
    const track = await this.prisma.track.findFirst({
      where: {
        id,
        status: TrackStatus.PUBLISHED,
      },
      select: {
        id: true,
        title: true,
        audioUrl: true,
        artist: {
          select: {
            name: true,
          },
        },
      },
    });

    if (!track) {
      throw new NotFoundException('Track not found or not published');
    }

    if (!track.audioUrl) {
      throw new NotFoundException('Audio is not ready yet');
    }

    return track;
  }
}
