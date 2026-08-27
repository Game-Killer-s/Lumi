import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { Prisma, Track } from '@prisma/client';

import { PrismaService } from '../../prisma/prisma.service';
import { CatalogCacheService } from '../catalog/catalog-cache.service';
import { AdminTrackUpdateDto } from './dto/admin-track-update.dto';

/**
 * Сервіс редагування метаданих треку адміністратором.
 *
 * Перед збереженням перевіряє, що всі relation (виконавець,
 * альбом, жанр) існують, і записує diff змін в audit log.
 */
@Injectable()
export class AdminTracksService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly cache: CatalogCacheService,
  ) {}

  async updateMetadata(
    trackId: string,
    dto: AdminTrackUpdateDto,
    actorId: string,
  ) {
    const track = await this.getTrack(trackId);

    const data = await this.buildUpdateData(track, dto);

    const updated = await this.prisma.track.update({
      where: {
        id: trackId,
      },
      data,
      include: {
        artist: true,
        genre: true,
        album: true,
      },
    });

    const diff = this.buildDiff(track, updated);

    if (Object.keys(diff).length > 0) {
      await this.prisma.trackAuditLog.create({
        data: {
          trackId,
          actorId,
          action: 'METADATA_UPDATED',
          details: JSON.stringify(diff),
        },
      });
    }

    // Каталог міг кешувати стару назву/обкладинку
    this.cache.invalidateCatalog();

    return updated;
  }

  async replaceCover(trackId: string, coverUrl: string, actorId: string) {
    const track = await this.getTrack(trackId);

    const updated = await this.prisma.track.update({
      where: {
        id: trackId,
      },
      data: {
        coverUrl,
      },
    });

    await this.prisma.trackAuditLog.create({
      data: {
        trackId,
        actorId,
        action: 'COVER_UPDATED',
        details: JSON.stringify({
          coverUrl: {
            from: track.coverUrl,
            to: coverUrl,
          },
        }),
      },
    });

    this.cache.invalidateCatalog();

    return updated;
  }

  private async buildUpdateData(
    track: Track,
    dto: AdminTrackUpdateDto,
  ): Promise<Prisma.TrackUpdateInput> {
    const data: Prisma.TrackUpdateInput = {};

    if (dto.title !== undefined) {
      data.title = dto.title;
    }

    if (dto.duration !== undefined) {
      data.duration = dto.duration;
    }

    if (dto.lyrics !== undefined) {
      data.lyrics = dto.lyrics;
    }

    if (dto.coverUrl !== undefined) {
      data.coverUrl = dto.coverUrl;
    }

    if (dto.artistId !== undefined) {
      // Валідація artist relation
      const artist = await this.prisma.artist.findUnique({
        where: {
          id: dto.artistId,
        },
      });

      if (!artist) {
        throw new BadRequestException('Artist not found');
      }

      data.artist = {
        connect: {
          id: dto.artistId,
        },
      };
    }

    if (dto.albumId !== undefined) {
      // Валідація album relation
      if (dto.albumId === null) {
        data.album = { disconnect: true };
      } else {
        const album = await this.prisma.album.findUnique({
          where: {
            id: dto.albumId,
          },
        });

        if (!album) {
          throw new BadRequestException('Album not found');
        }

        // Альбом має належати тому самому виконавцю, що й трек
        const targetArtistId = dto.artistId ?? track.artistId;

        if (album.artistId !== targetArtistId) {
          throw new BadRequestException(
            'Album does not belong to the track artist',
          );
        }

        data.album = {
          connect: {
            id: dto.albumId,
          },
        };
      }
    }

    if (dto.genreId !== undefined) {
      // Валідація genre relation
      if (dto.genreId === null) {
        data.genre = { disconnect: true };
      } else {
        const genre = await this.prisma.genre.findUnique({
          where: {
            id: dto.genreId,
          },
        });

        if (!genre) {
          throw new BadRequestException('Genre not found');
        }

        data.genre = {
          connect: {
            id: dto.genreId,
          },
        };
      }
    }

    return data;
  }

  private buildDiff(
    before: Track,
    after: Track,
  ): Record<string, { from: unknown; to: unknown }> {
    const fields = [
      'title',
      'duration',
      'lyrics',
      'coverUrl',
      'artistId',
      'albumId',
      'genreId',
    ] as const;

    const diff: Record<string, { from: unknown; to: unknown }> = {};

    for (const field of fields) {
      if (before[field] !== after[field]) {
        diff[field] = {
          from: before[field],
          to: after[field],
        };
      }
    }

    return diff;
  }

  private async getTrack(trackId: string): Promise<Track> {
    const track = await this.prisma.track.findUnique({
      where: {
        id: trackId,
      },
    });

    if (!track) {
      throw new NotFoundException('Track not found');
    }

    return track;
  }
}
