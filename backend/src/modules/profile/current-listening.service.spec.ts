import { NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Role, TrackStatus } from '@prisma/client';

import { PrismaService } from '../../prisma/prisma.service';
import { CurrentListeningService } from './current-listening.service';

describe('CurrentListeningService', () => {
  const now = new Date('2026-10-17T10:00:00.000Z');
  const track = {
    id: 't1',
    title: 'After Hours',
    duration: 240,
    coverUrl: null,
    status: TrackStatus.PUBLISHED,
    artist: { name: 'The Weeknd' },
  };

  let service: CurrentListeningService;
  let prisma: any;

  beforeEach(() => {
    jest.useFakeTimers().setSystemTime(now);

    prisma = {
      user: {
        findUnique: jest.fn().mockResolvedValue({
          isPublic: true,
          showCurrentListening: true,
          isBlocked: false,
        }),
      },
      track: {
        findUnique: jest.fn().mockResolvedValue({
          id: 't1',
          status: TrackStatus.PUBLISHED,
        }),
      },
      currentListening: {
        upsert: jest.fn().mockResolvedValue({}),
        findUnique: jest.fn().mockResolvedValue({
          updatedAt: now,
          track,
        }),
        deleteMany: jest.fn().mockResolvedValue({ count: 1 }),
      },
    };

    const config = {
      get: jest.fn().mockReturnValue(1800),
    } as unknown as ConfigService;

    service = new CurrentListeningService(prisma as PrismaService, config);
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('rejects a track that does not exist', async () => {
    prisma.track.findUnique.mockResolvedValue(null);

    await expect(service.update('u1', 'missing')).rejects.toBeInstanceOf(
      NotFoundException,
    );
    expect(prisma.currentListening.upsert).not.toHaveBeenCalled();
  });

  it('rejects a track that is not published', async () => {
    prisma.track.findUnique.mockResolvedValue({
      id: 't2',
      status: TrackStatus.DRAFT,
    });

    await expect(service.update('u1', 't2')).rejects.toBeInstanceOf(
      NotFoundException,
    );
    expect(prisma.currentListening.upsert).not.toHaveBeenCalled();
  });

  it('stores the timestamp and reports a visible presence', async () => {
    const result = await service.update('u1', 't1');

    expect(prisma.currentListening.upsert).toHaveBeenCalledWith({
      where: { userId: 'u1' },
      create: { userId: 'u1', trackId: 't1', updatedAt: now },
      update: { trackId: 't1', updatedAt: now },
    });
    expect(result).toEqual({
      trackId: 't1',
      updatedAt: now,
      expiresAt: new Date(now.getTime() + 1800 * 1000),
      visible: true,
    });
  });

  it('keeps presence hidden when the user disabled it', async () => {
    prisma.user.findUnique.mockResolvedValue({
      isPublic: true,
      showCurrentListening: false,
      isBlocked: false,
    });

    const result = await service.update('u1', 't1');

    expect(result.visible).toBe(false);
    expect(prisma.currentListening.upsert).toHaveBeenCalled();
  });

  it('hides presence from other users when the profile is private', async () => {
    prisma.user.findUnique.mockResolvedValue({
      isPublic: false,
      showCurrentListening: true,
      isBlocked: false,
    });

    await expect(
      service.getForProfile('u1', { sub: 'u2', role: Role.LISTENER }),
    ).rejects.toBeInstanceOf(NotFoundException);
  });

  it('hides presence when showCurrentListening is disabled', async () => {
    prisma.user.findUnique.mockResolvedValue({
      isPublic: true,
      showCurrentListening: false,
      isBlocked: false,
    });

    await expect(service.getForProfile('u1')).rejects.toBeInstanceOf(
      NotFoundException,
    );
  });

  it('lets the owner read their own hidden presence', async () => {
    prisma.user.findUnique.mockResolvedValue({
      isPublic: false,
      showCurrentListening: false,
      isBlocked: false,
    });

    const result = await service.getForProfile('u1', {
      sub: 'u1',
      role: Role.LISTENER,
    });

    expect(result.track).toEqual({
      id: 't1',
      title: 'After Hours',
      duration: 240,
      coverUrl: null,
      artistName: 'The Weeknd',
    });
    expect(result.expiresAt).toEqual(new Date(now.getTime() + 1800 * 1000));
  });

  it('removes expired presence and hides it', async () => {
    prisma.currentListening.findUnique.mockResolvedValue({
      updatedAt: new Date(now.getTime() - 1801 * 1000),
      track,
    });

    await expect(service.getForProfile('u1')).rejects.toBeInstanceOf(
      NotFoundException,
    );
    expect(prisma.currentListening.deleteMany).toHaveBeenCalledWith({
      where: { userId: 'u1' },
    });
  });

  it('removes presence for a blocked track', async () => {
    prisma.currentListening.findUnique.mockResolvedValue({
      updatedAt: now,
      track: { ...track, status: TrackStatus.BLOCKED },
    });

    await expect(service.getForProfile('u1')).rejects.toBeInstanceOf(
      NotFoundException,
    );
    expect(prisma.currentListening.deleteMany).toHaveBeenCalled();
  });

  it('clears presence for a banned user', async () => {
    prisma.user.findUnique.mockResolvedValue({
      isPublic: true,
      showCurrentListening: true,
      isBlocked: true,
    });

    await expect(service.getForProfile('u1')).rejects.toBeInstanceOf(
      NotFoundException,
    );
    expect(prisma.currentListening.deleteMany).toHaveBeenCalledWith({
      where: { userId: 'u1' },
    });
  });

  it('clears presence on demand', async () => {
    await service.clear('u1');

    expect(prisma.currentListening.deleteMany).toHaveBeenCalledWith({
      where: { userId: 'u1' },
    });
  });
});
