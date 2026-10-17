import { Test, TestingModule } from '@nestjs/testing';

import { PrismaService } from '../../prisma/prisma.service';
import { PushService } from './push.service';
import { ReleaseNotificationService } from './release-notification.service';

describe('ReleaseNotificationService', () => {
  let service: ReleaseNotificationService;

  let prisma: {
    client: {
      track: { findUnique: jest.Mock };
      subscription: { findMany: jest.Mock };
      notificationSettings: { findMany: jest.Mock };
      userNotification: { findMany: jest.Mock; createMany: jest.Mock };
    };
  };

  let push: { send: jest.Mock };

  const release = {
    id: 'r1',
    title: 'Midnight Drive',
    coverUrl: 'https://cdn.lumi/cover.jpg',
    status: 'PUBLISHED',
    artistId: 'a1',
    artist: { id: 'a1', name: 'Luna Waves' },
    album: { id: 'al1', title: 'Night Roads' },
  };

  const settingsRow = (userId: string, newReleases = true) => ({
    id: `s-${userId}`,
    userId,
    newReleases,
    artistUpdates: true,
    platformUpdates: true,
    systemNotifications: true,
    pushEnabled: true,
    emailEnabled: false,
  });

  beforeEach(async () => {
    prisma = {
      client: {
        track: { findUnique: jest.fn().mockResolvedValue(release) },
        subscription: {
          findMany: jest
            .fn()
            .mockResolvedValue([{ userId: 'u1' }, { userId: 'u2' }]),
        },
        notificationSettings: {
          findMany: jest
            .fn()
            .mockResolvedValue([settingsRow('u1'), settingsRow('u2')]),
        },
        userNotification: {
          findMany: jest.fn().mockResolvedValue([]),
          createMany: jest.fn().mockResolvedValue({ count: 2 }),
        },
      },
    };

    push = { send: jest.fn().mockResolvedValue(undefined) };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ReleaseNotificationService,
        { provide: PrismaService, useValue: prisma },
        { provide: PushService, useValue: push },
      ],
    }).compile();

    service = module.get(ReleaseNotificationService);
  });

  it('creates one notification per follower with release metadata', async () => {
    const result = await service.generateForRelease('r1');

    expect(result).toEqual({
      releaseId: 'r1',
      recipients: 2,
      notifications: 2,
    });

    const createArgs =
      prisma.client.userNotification.createMany.mock.calls[0][0];

    expect(createArgs.skipDuplicates).toBe(true);
    expect(createArgs.data).toHaveLength(2);
    expect(createArgs.data[0]).toEqual(
      expect.objectContaining({
        userId: 'u1',
        type: 'new_release',
        releaseId: 'r1',
        title: 'Новий реліз: Luna Waves',
        payload: {
          releaseId: 'r1',
          title: 'Midnight Drive',
          coverUrl: 'https://cdn.lumi/cover.jpg',
          artistId: 'a1',
          artistName: 'Luna Waves',
          albumId: 'al1',
          albumTitle: 'Night Roads',
        },
      }),
    );

    expect(push.send).toHaveBeenCalledTimes(2);
  });

  it('does not generate notifications for an unpublished release', async () => {
    prisma.client.track.findUnique.mockResolvedValue({
      ...release,
      status: 'DRAFT',
    });

    const result = await service.generateForRelease('r1');

    expect(result.recipients).toBe(0);
    expect(prisma.client.userNotification.createMany).not.toHaveBeenCalled();
  });

  it('does nothing when the release does not exist', async () => {
    prisma.client.track.findUnique.mockResolvedValue(null);

    const result = await service.generateForRelease('r1');

    expect(result).toEqual({
      releaseId: 'r1',
      recipients: 0,
      notifications: 0,
    });

    expect(prisma.client.subscription.findMany).not.toHaveBeenCalled();
  });

  it('skips followers who turned release notifications off', async () => {
    prisma.client.notificationSettings.findMany.mockResolvedValue([
      settingsRow('u1'),
      settingsRow('u2', false),
    ]);
    prisma.client.userNotification.createMany.mockResolvedValue({ count: 1 });

    const result = await service.generateForRelease('r1');

    expect(result.recipients).toBe(1);

    const createArgs =
      prisma.client.userNotification.createMany.mock.calls[0][0];

    expect(createArgs.data).toEqual([
      expect.objectContaining({ userId: 'u1' }),
    ]);
  });

  it('keeps followers without a settings row (defaults apply)', async () => {
    prisma.client.notificationSettings.findMany.mockResolvedValue([]);

    const result = await service.generateForRelease('r1');

    expect(result.recipients).toBe(2);
  });

  it('does not create a second notification for the same release', async () => {
    // подія про публікацію прийшла вдруге — обидва підписники вже мають сповіщення
    prisma.client.userNotification.findMany.mockResolvedValue([
      { userId: 'u1' },
      { userId: 'u2' },
    ]);

    const result = await service.generateForRelease('r1');

    expect(result).toEqual({
      releaseId: 'r1',
      recipients: 2,
      notifications: 0,
    });

    expect(prisma.client.userNotification.createMany).not.toHaveBeenCalled();
    expect(push.send).not.toHaveBeenCalled();
  });

  it('retries the generation job before giving up', async () => {
    prisma.client.userNotification.createMany
      .mockRejectedValueOnce(new Error('db is down'))
      .mockResolvedValueOnce({ count: 2 });

    const result = await service.generateForRelease('r1');

    expect(result.notifications).toBe(2);
    expect(prisma.client.userNotification.createMany).toHaveBeenCalledTimes(2);
  });

  it('throws when every attempt fails', async () => {
    prisma.client.userNotification.createMany.mockRejectedValue(
      new Error('db is down'),
    );

    await expect(service.generateForRelease('r1')).rejects.toThrow(
      'db is down',
    );

    expect(prisma.client.userNotification.createMany).toHaveBeenCalledTimes(3);
  });

  it('sends nothing when the artist has no followers', async () => {
    prisma.client.subscription.findMany.mockResolvedValue([]);

    const result = await service.generateForRelease('r1');

    expect(result).toEqual({
      releaseId: 'r1',
      recipients: 0,
      notifications: 0,
    });

    expect(prisma.client.userNotification.createMany).not.toHaveBeenCalled();
  });
});
