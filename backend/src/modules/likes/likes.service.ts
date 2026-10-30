import { Injectable, NotFoundException } from '@nestjs/common';
import { Prisma, TrackStatus } from '@prisma/client';

import { PrismaService } from '../../prisma/prisma.service';

// Лайки поточного користувача. GET повертає треки у форматі TrackListResponse,
// який читає Android-клієнт (див. CatalogService.toClientTrack), POST/DELETE
// ідемпотентно перемикають стан і повертають актуальне значення liked.
@Injectable()
export class LikesService {
  constructor(private readonly prisma: PrismaService) {}

  async getLikedTracks(userId: string, page: number, limit: number) {
    const where: Prisma.LikeWhereInput = {
      userId,
      track: { status: TrackStatus.PUBLISHED },
    };

    const [likes, total] = await Promise.all([
      this.prisma.like.findMany({
        where,
        orderBy: { createdAt: 'desc' },
        skip: (page - 1) * limit,
        take: limit,
        include: {
          track: { include: { artist: true, genre: true, album: true } },
        },
      }),
      this.prisma.like.count({ where }),
    ]);

    return {
      tracks: likes.map((like) => this.toClientTrack(like.track)),
      total,
      page,
      limit,
    };
  }

  async like(userId: string, trackId: string): Promise<{ liked: boolean }> {
    await this.ensurePublishedTrack(trackId);

    try {
      await this.prisma.transaction(async (tx) => {
        await tx.like.create({ data: { userId, trackId } });
        await tx.track.update({
          where: { id: trackId },
          data: { likeCount: { increment: 1 } },
        });
      });
    } catch (error) {
      // Повторний лайк (unique userId+trackId) не є помилкою — стан уже потрібний.
      if (
        !(error instanceof Prisma.PrismaClientKnownRequestError) ||
        error.code !== 'P2002'
      ) {
        throw error;
      }
    }

    return { liked: true };
  }

  async unlike(userId: string, trackId: string): Promise<{ liked: boolean }> {
    const deleted = await this.prisma.like.deleteMany({
      where: { userId, trackId },
    });

    if (deleted.count > 0) {
      await this.prisma.track.update({
        where: { id: trackId },
        data: { likeCount: { decrement: 1 } },
      });
    }

    return { liked: false };
  }

  private async ensurePublishedTrack(trackId: string): Promise<void> {
    const track = await this.prisma.track.findFirst({
      where: { id: trackId, status: TrackStatus.PUBLISHED },
      select: { id: true },
    });

    if (!track) {
      throw new NotFoundException('Track not found or not published');
    }
  }

  /** Prisma-трек → формат, який очікує Android-модель Track. */
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
      isLiked: true,
    };
  }
}
