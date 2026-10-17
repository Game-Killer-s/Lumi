import { Injectable, Logger, NotFoundException } from '@nestjs/common';

import { PrismaService } from '../../prisma/prisma.service';

import { ListNotificationsQueryDto } from './dto/list-notifications-query.dto';
import { PushType } from './dto/send-push.dto';
import { UpdateNotificationPreferencesDto } from './dto/update-notification-preferences.dto';
import { UpdateNotificationSettingsDto } from './dto/update-notification-settings.dto';
import {
  isNotificationTypeEnabled,
  NotificationPreferenceKey,
  NOTIFICATION_PREFERENCE_KEYS,
  toNotificationPreferences,
  toSettingsPatch,
} from './notification-preferences';
import { PushService } from './push.service';

export interface SendPushInput {
  userId: string;
  type: PushType;
  title: string;
  body: string;
}

// Сповіщення користувача: налаштування, преференції типів, шторка та push.
@Injectable()
export class NotificationsService {
  private readonly logger = new Logger(NotificationsService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly push: PushService,
  ) {}

  // Налаштувань може ще не бути — тоді створюємо з типовими значеннями
  // (ліниве створення для старих користувачів).
  async getSettings(userId: string) {
    const settings = await this.prisma.client.notificationSettings.findUnique({
      where: { userId },
    });

    if (settings) {
      return settings;
    }

    return this.prisma.client.notificationSettings.create({
      data: { userId },
    });
  }

  async updateSettings(userId: string, dto: UpdateNotificationSettingsDto) {
    await this.getSettings(userId);

    return this.prisma.client.notificationSettings.update({
      where: { userId },
      data: { ...dto },
    });
  }

  // Преференції — це той самий рядок у базі, але ключами типів сповіщень.
  async getPreferences(userId: string) {
    const settings = await this.getSettings(userId);

    return toNotificationPreferences(settings);
  }

  async updatePreferences(
    userId: string,
    dto: UpdateNotificationPreferencesDto,
  ) {
    await this.getSettings(userId);

    const settings = await this.prisma.client.notificationSettings.update({
      where: { userId },
      data: toSettingsPatch(dto),
    });

    // Тільки id і змінені ключі — ні пошти, ні нікнейма.
    const changed = NOTIFICATION_PREFERENCE_KEYS.filter(
      (key: NotificationPreferenceKey) => dto[key] !== undefined,
    );

    this.logger.log(
      `preferences ${userId}: ${changed.join(', ') || 'без змін'}`,
    );

    return toNotificationPreferences(settings);
  }

  // Шторка сповіщень: найновіші зверху, з пагінацією.
  async listNotifications(userId: string, query: ListNotificationsQueryDto) {
    const where = { userId };

    const [items, total, unread] = await Promise.all([
      this.prisma.client.userNotification.findMany({
        where,
        orderBy: [{ createdAt: 'desc' }, { id: 'desc' }],
        skip: (query.page - 1) * query.limit,
        take: query.limit,
      }),

      this.prisma.client.userNotification.count({ where }),

      this.countUnread(userId),
    ]);

    return {
      items,
      total,
      page: query.page,
      limit: query.limit,
      unread,
    };
  }

  // Окремий запит лише на кількість — працює по індексу [userId, isRead].
  async getUnreadCount(userId: string) {
    return { unread: await this.countUnread(userId) };
  }

  async markAsRead(userId: string, id: string) {
    // userId у фільтрі — чужі сповіщення читати не можна.
    const notification = await this.prisma.client.userNotification.findFirst({
      where: { id, userId },
    });

    if (!notification) {
      throw new NotFoundException('Сповіщення не знайдено');
    }

    // Повторний mark-read нічого не змінює: readAt лишається першим.
    if (notification.isRead) {
      return notification;
    }

    return this.prisma.client.userNotification.update({
      where: { id },
      data: { isRead: true, readAt: new Date() },
    });
  }

  async markAllAsRead(userId: string) {
    const result = await this.prisma.client.userNotification.updateMany({
      where: { userId, isRead: false },
      data: { isRead: true, readAt: new Date() },
    });

    return { updated: result.count };
  }

  // Єдина точка відправки: спочатку дивимось, чи користувач узагалі
  // хоче такі сповіщення, і лише потім кладемо в шторку та шлемо push.
  async send(input: SendPushInput) {
    const settings = await this.getSettings(input.userId);

    if (!isNotificationTypeEnabled(settings, input.type)) {
      return { sent: false, notification: null };
    }

    const notification = await this.prisma.client.userNotification.create({
      data: {
        userId: input.userId,
        type: input.type,
        title: input.title,
        body: input.body,
      },
    });

    await this.push.send(input.userId, input.title, input.body);

    return { sent: true, notification };
  }

  private async countUnread(userId: string): Promise<number> {
    return this.prisma.client.userNotification.count({
      where: { userId, isRead: false },
    });
  }
}
