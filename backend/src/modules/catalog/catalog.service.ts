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

  // Текст пісні для Android-клієнта (LyricsApi: GET /tracks/:id/lyrics).
  // Якщо тексту ще не додали, повертаємо lyrics: null — клієнт покаже
  // дружню заглушку «текст ще не додали», а не помилку.
  async getLyrics(id: string) {
    const track = await this.prisma.track.findFirst({
      where: {
        id,
        status: TrackStatus.PUBLISHED,
      },
      select: {
        id: true,
        lyrics: true,
      },
    });

    if (!track) {
      throw new NotFoundException('Track not found or not published');
    }

    return { trackId: track.id, lyrics: track.lyrics };
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

  // ============================================================
  // Стрічки для мобільного клієнта: /tracks/popular та /tracks/new.
  //
  // Android-модель Track (android/.../data/model/Models.kt) очікує
  // пласку форму: genre і album — рядки, плюс artistName/albumCoverUrl/
  // likesCount. Тому віддаємо не сирі Prisma-рядки, а DTO, загорнутий
  // у TrackListResponse { tracks, total, page, limit }.
  // ============================================================

  async getPopularFeed(limit: number) {
    return this.loadClientFeed(limit, { playCount: 'desc' });
  }

  async getNewFeed(limit: number) {
    return this.loadClientFeed(limit, { createdAt: 'desc' });
  }

  private async loadClientFeed(
    limit: number,
    orderBy: Prisma.TrackOrderByWithRelationInput,
  ) {
    const tracks = await this.prisma.track.findMany({
      where: this.wherePublished(),
      orderBy,
      take: limit,
      include: { artist: true, genre: true, album: true },
    });

    return {
      tracks: tracks.map((track) => this.toClientTrack(track)),
      total: tracks.length,
      page: 1,
      limit,
    };
  }

  /** Перетворює Prisma-трек у форму, яку читає Android-клієнт. */
  private toClientTrack(
    track: Prisma.TrackGetPayload<{
      include: { artist: true; genre: true; album: true };
    }>,
  ) {
    return {
      id: track.id,
      title: track.title,
      artist: track.artist
        ? {
            id: track.artist.id,
            name: track.artist.name,
            imageUrl: track.artist.photoUrl,
            bio: track.artist.description,
          }
        : null,
      artistName: track.artist?.name ?? null,
      album: track.album?.title ?? null,
      albumId: track.album?.id ?? null,
      albumCoverUrl: track.album?.coverUrl ?? null,
      coverUrl: track.coverUrl,
      duration: track.duration,
      genre: track.genre?.name ?? null,
      audioUrl: track.audioUrl,
      playCount: track.playCount,
      likesCount: track.likeCount,
      isLiked: false,
    };
  }

  /**
   * GET /api/albums — список альбомів для бібліотеки.
   * Повертає лише опубліковані треки всередині кожного альбому.
   */
  async getAlbums(limit: number) {
    const albums = await this.prisma.album.findMany({
      orderBy: { createdAt: 'desc' },
      take: limit,
      include: {
        artist: true,
        tracks: {
          where: this.wherePublished(),
          orderBy: { createdAt: 'desc' },
          include: { artist: true, genre: true, album: true },
        },
      },
    });

    return {
      albums: albums.map((album) => this.toClientAlbum(album)),
      total: albums.length,
      page: 1,
      limit,
    };
  }

  /** GET /api/albums/:id — один альбом з треками. */
  async findAlbum(id: string) {
    const album = await this.prisma.album.findUnique({
      where: { id },
      include: {
        artist: true,
        tracks: {
          where: this.wherePublished(),
          orderBy: { createdAt: 'desc' },
          include: { artist: true, genre: true, album: true },
        },
      },
    });

    if (!album) {
      throw new NotFoundException('Album not found');
    }

    return this.toClientAlbum(album);
  }

  /** Prisma-альбом → формат Android-моделі Album. */
  private toClientAlbum(
    album: Prisma.AlbumGetPayload<{
      include: {
        artist: true;
        tracks: {
          include: { artist: true; genre: true; album: true };
        };
      };
    }>,
  ) {
    return {
      id: album.id,
      title: album.title,
      artist: {
        id: album.artist.id,
        name: album.artist.name,
        imageUrl: album.artist.photoUrl,
        bio: album.artist.description,
      },
      artistName: album.artist.name,
      coverUrl: album.coverUrl,
      releaseDate: album.releaseYear?.toString() ?? null,
      tracks: album.tracks.map((track) => this.toClientTrack(track)),
      tracksCount: album.tracks.length,
      duration: album.tracks.reduce((sum, track) => sum + track.duration, 0),
    };
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
