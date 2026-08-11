import { Injectable, NotFoundException } from '@nestjs/common';
import { TrackStatus } from '@prisma/client';

import { PrismaService } from '../../prisma/prisma.service';
import { CatalogCacheService } from './catalog-cache.service';

/**
 * Каталог для звичайних слухачів.
 *
 * Усі методи віддають тільки опубліковані треки (status = PUBLISHED).
 * Заблоковані, неопубліковані або ще не готові треки сюди не потрапляють.
 */
@Injectable()
export class CatalogService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly cache: CatalogCacheService,
  ) {}

  async findAll() {
    const cached = this.cache.get<unknown>('catalog:all');
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

    this.cache.set('catalog:all', tracks);
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
    const cached = this.cache.get<unknown>(`catalog:track:${id}`);
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

    this.cache.set(`catalog:track:${id}`, track);
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
