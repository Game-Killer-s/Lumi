import { Injectable, Logger, NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Role, TrackStatus } from '@prisma/client';

import { PrismaService } from '../../prisma/prisma.service';
import {
  CurrentListeningDto,
  CurrentListeningStatusDto,
} from './dto/current-listening.dto';
import { canViewPrivateProfile, ProfileViewer } from './profile-projection';

const TRACK_SELECT = {
  id: true,
  title: true,
  duration: true,
  coverUrl: true,
  status: true,
  artist: { select: { name: true } },
} as const;

@Injectable()
export class CurrentListeningService {
  private readonly logger = new Logger(CurrentListeningService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly configService: ConfigService,
  ) {}

  async update(
    userId: string,
    trackId: string,
  ): Promise<CurrentListeningStatusDto> {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: { isPublic: true, showCurrentListening: true },
    });

    if (!user) {
      throw new NotFoundException('Profile not found');
    }

    const track = await this.prisma.track.findUnique({
      where: { id: trackId },
      select: { id: true, status: true },
    });

    if (!track || track.status !== TrackStatus.PUBLISHED) {
      throw new NotFoundException('Track not found');
    }

    const updatedAt = new Date();
    const expiresAt = this.expiresAt(updatedAt);

    await this.prisma.currentListening.upsert({
      where: { userId },
      create: { userId, trackId, updatedAt },
      update: { trackId, updatedAt },
    });

    return {
      trackId,
      updatedAt,
      expiresAt,
      visible: user.isPublic && user.showCurrentListening,
    };
  }

  async clear(userId: string): Promise<void> {
    await this.prisma.currentListening.deleteMany({ where: { userId } });
  }

  async getForProfile(
    targetUserId: string,
    viewer?: ProfileViewer,
  ): Promise<CurrentListeningDto> {
    const user = await this.prisma.user.findUnique({
      where: { id: targetUserId },
      select: { isPublic: true, showCurrentListening: true, isBlocked: true },
    });

    if (!user || user.isBlocked) {
      if (user?.isBlocked) {
        await this.clear(targetUserId);
      }

      throw new NotFoundException('Current listening is not available');
    }

    const privileged = canViewPrivateProfile(viewer, targetUserId);

    if (!privileged && (!user.isPublic || !user.showCurrentListening)) {
      throw new NotFoundException('Current listening is not available');
    }

    if (viewer?.role === Role.ADMIN && viewer.sub !== targetUserId) {
      this.logger.log(
        `Admin current listening access: actor=${viewer.sub}, target=${targetUserId}`,
      );
    }

    const presence = await this.prisma.currentListening.findUnique({
      where: { userId: targetUserId },
      select: {
        updatedAt: true,
        track: { select: TRACK_SELECT },
      },
    });

    if (!presence || presence.track.status !== TrackStatus.PUBLISHED) {
      if (presence) {
        await this.clear(targetUserId);
      }

      throw new NotFoundException('Current listening is not available');
    }

    const expiresAt = this.expiresAt(presence.updatedAt);

    if (expiresAt.getTime() <= Date.now()) {
      await this.clear(targetUserId);

      throw new NotFoundException('Current listening is not available');
    }

    return {
      track: {
        id: presence.track.id,
        title: presence.track.title,
        duration: presence.track.duration,
        coverUrl: presence.track.coverUrl,
        artistName: presence.track.artist.name,
      },
      updatedAt: presence.updatedAt,
      expiresAt,
    };
  }

  private expiresAt(updatedAt: Date): Date {
    return new Date(updatedAt.getTime() + this.ttlSeconds * 1000);
  }

  private get ttlSeconds(): number {
    return this.configService.get<number>('presence.ttlSeconds') ?? 1800;
  }
}
