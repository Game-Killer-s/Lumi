import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { TrackStatus } from '@prisma/client';

import { PrismaService } from '../../prisma/prisma.service';
import { CatalogCacheService } from '../catalog/catalog-cache.service';

/**
 * Дозволені переходи життєвого циклу треку.
 *
 * Життєвий цикл: DRAFT -> UPLOADED -> PROCESSING -> ON_MODERATION -> PUBLISHED.
 * З PUBLISHED можна потрапити тільки в BLOCKED (із причиною),
 * а з BLOCKED - тільки назад у PUBLISHED (unblock за policy).
 *
 * Усі інші переходи заборонені.
 */
export const LIFECYCLE_TRANSITIONS: Record<TrackStatus, TrackStatus[]> = {
  DRAFT: [TrackStatus.UPLOADED],
  UPLOADED: [TrackStatus.PROCESSING],
  PROCESSING: [TrackStatus.ON_MODERATION],
  ON_MODERATION: [TrackStatus.PUBLISHED],
  PUBLISHED: [TrackStatus.BLOCKED],
  BLOCKED: [TrackStatus.PUBLISHED],
};

/**
 * Сервіс керування життєвим циклом треку.
 *
 * Кожна зміна статусу:
 * - перевіряється за матрицею переходів;
 * - записується в audit log (хто, коли, що і чому);
 * - скидає catalog cache, щоб слухачі бачили актуальний каталог.
 */
@Injectable()
export class ContentLifecycleService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly cache: CatalogCacheService,
  ) {}

  /**
   * upload: DRAFT -> UPLOADED.
   * Зберігає URL аудіофайлу, який завантажив контент-менеджер.
   */
  async markUploaded(trackId: string, audioUrl: string, actorId: string) {
    if (!audioUrl?.trim()) {
      throw new BadRequestException('audioUrl is required for upload');
    }

    return this.changeStatus(trackId, TrackStatus.UPLOADED, actorId, {
      audioUrl,
    });
  }

  /**
   * upload -> processing: UPLOADED -> PROCESSING.
   */
  async startProcessing(trackId: string, actorId: string) {
    return this.changeStatus(trackId, TrackStatus.PROCESSING, actorId);
  }

  /**
   * processing -> moderation: PROCESSING -> ON_MODERATION.
   *
   * Дозволено лише після готових variants -
   * тут це перевірка, що аудіофайл (audioUrl) уже завантажений.
   */
  async markReadyForModeration(trackId: string, actorId: string) {
    const track = await this.getTrack(trackId);

    if (!track.audioUrl) {
      throw new BadRequestException(
        'Track has no ready variants (audioUrl is missing)',
      );
    }

    return this.changeStatus(trackId, TrackStatus.ON_MODERATION, actorId);
  }

  /**
   * moderation -> published: ON_MODERATION -> PUBLISHED.
   */
  async publish(trackId: string, actorId: string) {
    return this.changeStatus(trackId, TrackStatus.PUBLISHED, actorId);
  }

  /**
   * published -> blocked: PUBLISHED -> BLOCKED.
   * Блокувати можна лише з причиною.
   */
  async block(trackId: string, reason: string, actorId: string) {
    if (!reason?.trim()) {
      throw new BadRequestException('reason is required for block');
    }

    return this.changeStatus(trackId, TrackStatus.BLOCKED, actorId, { reason });
  }

  /**
   * unblock: BLOCKED -> PUBLISHED.
   *
   * Policy: повертаємо попередній publication state.
   * Оскільки заблокувати можна тільки PUBLISHED трек,
   * попередній стан завжди PUBLISHED.
   */
  async unblock(trackId: string, actorId: string) {
    return this.changeStatus(trackId, TrackStatus.PUBLISHED, actorId);
  }

  /**
   * Перевірка переходу за матрицею.
   * Публічний метод - його тестує transition matrix тест.
   */
  assertValidTransition(from: TrackStatus, to: TrackStatus): void {
    const allowed = LIFECYCLE_TRANSITIONS[from] ?? [];

    if (!allowed.includes(to)) {
      throw new BadRequestException(
        `Invalid lifecycle transition: ${from} -> ${to}`,
      );
    }
  }

  private async changeStatus(
    trackId: string,
    toStatus: TrackStatus,
    actorId: string,
    extra?: {
      audioUrl?: string;
      reason?: string;
    },
  ) {
    const track = await this.getTrack(trackId);

    this.assertValidTransition(track.status, toStatus);

    const updated = await this.prisma.track.update({
      where: {
        id: trackId,
      },
      data: {
        status: toStatus,
        ...(extra?.audioUrl ? { audioUrl: extra.audioUrl } : {}),
        ...(extra?.reason ? { blockReason: extra.reason } : {}),
      },
    });

    await this.prisma.trackAuditLog.create({
      data: {
        trackId,
        actorId,
        action: `STATUS_CHANGED:${track.status}->${toStatus}`,
        reason: extra?.reason ?? null,
      },
    });

    // Після будь-якої зміни життєвого циклу каталог треба оновити
    this.cache.invalidateCatalog();

    return updated;
  }

  private async getTrack(trackId: string) {
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
