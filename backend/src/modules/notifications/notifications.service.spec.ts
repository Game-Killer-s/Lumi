import { NotFoundException } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';

import { PrismaService } from '../../prisma/prisma.service';
import { NotificationsService } from './notifications.service';
import { PushService } from './push.service';

describe('NotificationsService', () => {
  let service: NotificationsService;

  let prisma: {
    client: {
      notificationSettings: {
        findUnique: jest.Mock;
        create: jest.Mock;
        update: jest.Mock;
      };
      userNotification: {
        findMany: jest.Mock;
        count: jest.Mock;
        findFirst: jest.Mock;
        create: jest.Mock;
        update: jest.Mock;
        updateMany: jest.Mock;
      };
    };
  };

  let push: { send: jest.Mock };

  const defaultSettings = {
    id: 's1',
    userId: 'u1',
    newReleases: true,
    artistUpdates: true,
    platformUpdates: true,
    systemNotifications: true,
    pushEnabled: true,
    emailEnabled: false,
  };

  beforeEach(async () => {
    prisma = {
      client: {
        notificationSettings: {
          findUnique: jest.fn().mockResolvedValue(defaultSettings),
          create: jest.fn().mockResolvedValue(defaultSettings),
          update: jest.fn().mockResolvedValue(defaultSettings),
        },
        userNotification: {
          findMany: jest.fn().mockResolvedValue([]),
          count: jest.fn().mockResolvedValue(3),
          findFirst: jest.fn(),
          create: jest.fn(),
          update: jest.fn(),
          updateMany: jest.fn().mockResolvedValue({ count: 2 }),
        },
      },
    };

    push = { send: jest.fn().mockResolvedValue(undefined) };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        NotificationsService,
        { provide: PrismaService, useValue: prisma },
        { provide: PushService, useValue: push },
      ],
    }).compile();

    service = module.get(NotificationsService);
  });

  it('creates default settings on the first visit', async () => {
    prisma.client.notificationSettings.findUnique.mockResolvedValue(null);

    const settings = await service.getSettings('u1');

    expect(settings).toEqual(defaultSettings);
    expect(prisma.client.notificationSettings.create).toHaveBeenCalledWith({
      data: { userId: 'u1' },
    });
  });

  it('returns stored settings without creating new ones', async () => {
    const settings = await service.getSettings('u1');

    expect(settings).toEqual(defaultSettings);
    expect(prisma.client.notificationSettings.create).not.toHaveBeenCalled();
  });

  it('saves the switches from the settings screen', async () => {
    await service.updateSettings('u1', { newReleases: false });

    expect(prisma.client.notificationSettings.update).toHaveBeenCalledWith({
      where: { userId: 'u1' },
      data: { newReleases: false },
    });
  });

  it('returns a page of the curtain with the unread counter', async () => {
    prisma.client.userNotification.findMany.mockResolvedValue([
      { id: 'n1', title: 'Новий реліз' },
    ]);
    prisma.client.userNotification.count
      .mockResolvedValueOnce(5)
      .mockResolvedValueOnce(2);

    const result = await service.listNotifications('u1', {
      page: 2,
      limit: 10,
    });

    expect(prisma.client.userNotification.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { userId: 'u1' },
        orderBy: [{ createdAt: 'desc' }, { id: 'desc' }],
        skip: 10,
        take: 10,
      }),
    );

    expect(result.items).toHaveLength(1);
    expect(result.total).toBe(5);
    expect(result.unread).toBe(2);
    expect(result.page).toBe(2);
  });

  it('counts only the unread notifications of the current user', async () => {
    prisma.client.userNotification.count.mockResolvedValue(4);

    const result = await service.getUnreadCount('u1');

    expect(result).toEqual({ unread: 4 });
    expect(prisma.client.userNotification.count).toHaveBeenCalledWith({
      where: { userId: 'u1', isRead: false },
    });
  });

  it('marks a notification as read and stamps readAt', async () => {
    prisma.client.userNotification.findFirst.mockResolvedValue({
      id: 'n1',
      userId: 'u1',
      isRead: false,
    });

    await service.markAsRead('u1', 'n1');

    expect(prisma.client.userNotification.findFirst).toHaveBeenCalledWith({
      where: { id: 'n1', userId: 'u1' },
    });

    expect(prisma.client.userNotification.update).toHaveBeenCalledWith({
      where: { id: 'n1' },
      data: { isRead: true, readAt: expect.any(Date) },
    });
  });

  it('keeps readAt when the notification is already read', async () => {
    const readAt = new Date('2026-09-21T10:00:00.000Z');

    prisma.client.userNotification.findFirst.mockResolvedValue({
      id: 'n1',
      userId: 'u1',
      isRead: true,
      readAt,
    });

    const result = await service.markAsRead('u1', 'n1');

    expect(result.readAt).toBe(readAt);
    expect(prisma.client.userNotification.update).not.toHaveBeenCalled();
  });

  it('does not allow reading a notification of another user', async () => {
    prisma.client.userNotification.findFirst.mockResolvedValue(null);

    await expect(service.markAsRead('u1', 'n1')).rejects.toThrow(
      NotFoundException,
    );

    expect(prisma.client.userNotification.update).not.toHaveBeenCalled();
  });

  it('marks every unread notification as read', async () => {
    const result = await service.markAllAsRead('u1');

    expect(result.updated).toBe(2);
    expect(prisma.client.userNotification.updateMany).toHaveBeenCalledWith({
      where: { userId: 'u1', isRead: false },
      data: { isRead: true, readAt: expect.any(Date) },
    });
  });

  it('creates preferences lazily with defaults for a legacy user', async () => {
    prisma.client.notificationSettings.findUnique.mockResolvedValue(null);

    const preferences = await service.getPreferences('u1');

    expect(prisma.client.notificationSettings.create).toHaveBeenCalledWith({
      data: { userId: 'u1' },
    });

    expect(preferences).toEqual({
      new_release: true,
      artist_update: true,
      promo: true,
      system: true,
    });
  });

  it('saves preferences by notification type keys', async () => {
    prisma.client.notificationSettings.update.mockResolvedValue({
      ...defaultSettings,
      newReleases: false,
      platformUpdates: false,
    });

    const preferences = await service.updatePreferences('u1', {
      new_release: false,
      promo: false,
    });

    expect(prisma.client.notificationSettings.update).toHaveBeenCalledWith({
      where: { userId: 'u1' },
      data: { newReleases: false, platformUpdates: false },
    });

    expect(preferences.new_release).toBe(false);
    expect(preferences.promo).toBe(false);
    expect(preferences.system).toBe(true);
  });

  it('puts a notification into the curtain and sends a push', async () => {
    prisma.client.userNotification.create.mockResolvedValue({
      id: 'n1',
      title: 'Новий реліз',
    });

    const result = await service.send({
      userId: 'u1',
      type: 'new_release',
      title: 'Новий реліз',
      body: 'Трек уже доступний',
    });

    expect(result.sent).toBe(true);
    expect(push.send).toHaveBeenCalledWith(
      'u1',
      'Новий реліз',
      'Трек уже доступний',
    );
  });

  it('skips the notification when push is switched off', async () => {
    prisma.client.notificationSettings.findUnique.mockResolvedValue({
      ...defaultSettings,
      pushEnabled: false,
    });

    const result = await service.send({
      userId: 'u1',
      type: 'new_release',
      title: 'Новий реліз',
      body: 'Трек уже доступний',
    });

    expect(result.sent).toBe(false);
    expect(prisma.client.userNotification.create).not.toHaveBeenCalled();
  });

  it('skips release notifications when that preference is off', async () => {
    prisma.client.notificationSettings.findUnique.mockResolvedValue({
      ...defaultSettings,
      newReleases: false,
    });

    const result = await service.send({
      userId: 'u1',
      type: 'new_release',
      title: 'Новий реліз',
      body: 'Трек уже доступний',
    });

    expect(result.sent).toBe(false);
  });

  it('sends system notifications while the system preference is on', async () => {
    prisma.client.userNotification.create.mockResolvedValue({
      id: 'n2',
      title: 'Підписка',
    });

    const result = await service.send({
      userId: 'u1',
      type: 'subscription',
      title: 'Підписка',
      body: 'Оновіть картку',
    });

    expect(result.sent).toBe(true);
  });

  it('skips system notifications when the system preference is off', async () => {
    prisma.client.notificationSettings.findUnique.mockResolvedValue({
      ...defaultSettings,
      systemNotifications: false,
    });

    const result = await service.send({
      userId: 'u1',
      type: 'subscription',
      title: 'Підписка',
      body: 'Оновіть картку',
    });

    expect(result.sent).toBe(false);
    expect(prisma.client.userNotification.create).not.toHaveBeenCalled();
  });
});
