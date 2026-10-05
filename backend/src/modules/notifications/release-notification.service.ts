import { Injectable, Logger } from '@nestjs/common';

import { PrismaService } from '../../prisma/prisma.service';

import {
  DEFAULT_NOTIFICATION_PREFERENCES,
  isNotificationTypeEnabled,
} from './notification-preferences';
import { PushService } from './push.service';
import { ReleaseNotificationPayload } from './release.event';

const RETRY_ATTEMPTS = 3;
const RETRY_DELAY_MS = 50;

export interface ReleaseNotificationResult {
  releaseId: string;
  recipients: number;
  notifications: number;
}

// Генерація сповіщень про новий реліз для підписників артиста.
@Injectable()
export class ReleaseNotificationService {
  private readonly logger = new Logger(ReleaseNotificationService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly push: PushService,
  ) {}

  // Джоб: якщо генерація впала — пробуємо ще раз.
  async generateForRelease(
    releaseId: string,
  ): Promise<ReleaseNotificationResult> {
    for (let attempt = 1; attempt <= RETRY_ATTEMPTS; attempt += 1) {
      try {
        return await this.createNotifications(releaseId);
      } catch (error) {
        this.logger.warn(`release ${releaseId}: спроба ${attempt} не вдалась`);

        if (attempt === RETRY_ATTEMPTS) {
          throw error;
        }

        await sleep(RETRY_DELAY_MS * attempt);
      }
    }

    return { releaseId, recipients: 0, notifications: 0 };
  }

  private async createNotifications(
    releaseId: string,
  ): Promise<ReleaseNotificationResult> {
    const release = await this.prisma.client.track.findUnique({
      where: { id: releaseId },
      include: { artist: true, album: true },
    });

    // Чернетки, треки на модерації та заблоковані нікому не показуємо.
    if (!release || release.status !== 'PUBLISHED') {
      this.logger.log(`release ${releaseId}: пропущено, реліз не published`);
      return { releaseId, recipients: 0, notifications: 0 };
    }

    const followers = await this.prisma.client.subscription.findMany({
      where: { artistId: release.artistId },
      select: { userId: true },
    });

    const recipients = await this.filterByPreferences(
      followers.map((follower) => follower.userId),
    );

    if (recipients.length === 0) {
      this.logger.log(`release ${releaseId}: recipients 0, notifications 0`);
      return { releaseId, recipients: 0, notifications: 0 };
    }

    // Кого вже повідомляли про цей реліз — тому вдруге не пишемо і не шлемо push.
    const alreadyNotified = await this.prisma.client.userNotification.findMany({
      where: { releaseId: release.id, userId: { in: recipients } },
      select: { userId: true },
    });

    const notifiedIds = new Set(alreadyNotified.map((item) => item.userId));
    const newRecipients = recipients.filter(
      (userId) => !notifiedIds.has(userId),
    );

    const title = `Новий реліз: ${release.artist.name}`;
    const body = release.album
      ? `Трек «${release.title}» з альбому «${release.album.title}»`
      : `Трек «${release.title}» уже доступний`;

    const payload = this.buildPayload(release);

    if (newRecipients.length === 0) {
      this.logger.log(
        `release ${release.id}: recipients ${recipients.length}, notifications 0`,
      );
      return { releaseId, recipients: recipients.length, notifications: 0 };
    }

    const created = await this.prisma.client.userNotification.createMany({
      data: newRecipients.map((userId) => ({
        userId,
        type: 'new_release',
        title,
        body,
        releaseId: release.id,
        payload,
      })),
      // Захист від гонки: унікальність [userId, releaseId] тримає база.
      skipDuplicates: true,
    });

    for (const userId of newRecipients) {
      await this.push.send(userId, title, body);
    }

    this.logger.log(
      `release ${release.id}: recipients ${recipients.length}, notifications ${created.count}`,
    );

    return {
      releaseId,
      recipients: recipients.length,
      notifications: created.count,
    };
  }

  // Підписників без рядка налаштувань вважаємо за дефолт.
  private async filterByPreferences(userIds: string[]): Promise<string[]> {
    if (userIds.length === 0) {
      return [];
    }

    const settings = await this.prisma.client.notificationSettings.findMany({
      where: { userId: { in: userIds } },
    });

    const byUser = new Map(settings.map((row) => [row.userId, row]));

    return userIds.filter((userId) => {
      const row = byUser.get(userId);

      return row
        ? isNotificationTypeEnabled(row, 'new_release')
        : DEFAULT_NOTIFICATION_PREFERENCES.new_release;
    });
  }

  private buildPayload(release: {
    id: string;
    title: string;
    coverUrl: string | null;
    artist: { id: string; name: string };
    album: { id: string; title: string } | null;
  }): ReleaseNotificationPayload {
    return {
      releaseId: release.id,
      title: release.title,
      coverUrl: release.coverUrl,
      artistId: release.artist.id,
      artistName: release.artist.name,
      albumId: release.album?.id ?? null,
      albumTitle: release.album?.title ?? null,
    };
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
