import {
  BadRequestException,
  ConflictException,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import { Role } from '@prisma/client';
import { randomUUID } from 'node:crypto';
import { promises as fs } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { PrismaService } from '../../prisma/prisma.service';
import { PasswordService } from '../auth/services/password.service';
import { MediaStorageService } from '../media/media-storage.service';
import { ProfileService } from './profile.service';

function pngBuffer(width: number, height: number): Buffer {
  const buffer = Buffer.alloc(33);
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]).copy(buffer, 0);
  buffer.writeUInt32BE(13, 8);
  buffer.write('IHDR', 12, 'latin1');
  buffer.writeUInt32BE(width, 16);
  buffer.writeUInt32BE(height, 20);

  return buffer;
}

const profile = {
  id: 'u1',
  nickname: 'Vasyl',
  email: 'vasyl@example.com',
  avatarUrl: null as string | null,
  role: Role.LISTENER,
  isPublic: true,
  showCurrentListening: true,
  createdAt: new Date('2026-10-01T10:00:00.000Z'),
};

describe('ProfileService', () => {
  let service: ProfileService;
  let prisma: any;
  let password: { hash: jest.Mock; verify: jest.Mock };
  let storage: {
    buildStorageKey: jest.Mock;
    moveIntoStorage: jest.Mock;
    remove: jest.Mock;
    removeTemp: jest.Mock;
  };
  let logSpy: jest.SpyInstance;
  const tempPaths: string[] = [];

  async function tempPng(width: number, height: number): Promise<string> {
    const path = join(tmpdir(), `lumi-avatar-${randomUUID()}.png`);

    await fs.writeFile(path, pngBuffer(width, height));
    tempPaths.push(path);

    return path;
  }

  beforeEach(async () => {
    prisma = {
      user: {
        findUnique: jest.fn().mockResolvedValue(profile),
        findFirst: jest.fn().mockResolvedValue(null),
        findMany: jest.fn().mockResolvedValue([profile]),
        count: jest.fn().mockResolvedValue(1),
        update: jest.fn().mockResolvedValue(profile),
      },
      currentListening: {
        deleteMany: jest.fn().mockResolvedValue({ count: 1 }),
      },
      client: {
        authSession: {
          updateMany: jest.fn().mockResolvedValue({ count: 1 }),
        },
      },
      transaction: jest.fn((callback: (tx: unknown) => Promise<unknown>) =>
        callback({
          user: prisma.user,
          authSession: prisma.client.authSession,
          currentListening: prisma.currentListening,
        }),
      ),
    };

    password = {
      hash: jest.fn().mockResolvedValue('hashed-new-password'),
      verify: jest.fn().mockResolvedValue(true),
    };

    storage = {
      buildStorageKey: jest.fn().mockReturnValue('avatar/2026/10/new.png'),
      moveIntoStorage: jest.fn().mockResolvedValue(undefined),
      remove: jest.fn().mockResolvedValue(undefined),
      removeTemp: jest.fn().mockResolvedValue(undefined),
    };

    logSpy = jest.spyOn(Logger.prototype, 'log').mockImplementation();

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ProfileService,
        { provide: PrismaService, useValue: prisma },
        { provide: PasswordService, useValue: password },
        { provide: MediaStorageService, useValue: storage },
      ],
    }).compile();

    service = module.get(ProfileService);
  });

  afterEach(async () => {
    jest.restoreAllMocks();

    await Promise.all(
      tempPaths.splice(0).map((path) => fs.rm(path, { force: true })),
    );
  });

  it('returns the current user profile', async () => {
    await expect(service.getProfile('u1')).resolves.toEqual(profile);
  });

  it('throws 404 when the profile does not exist', async () => {
    prisma.user.findUnique.mockResolvedValue(null);

    await expect(service.getProfile('u1')).rejects.toBeInstanceOf(
      NotFoundException,
    );
  });

  it('normalizes nickname and email before saving', async () => {
    prisma.user.update.mockResolvedValue({
      ...profile,
      nickname: 'New Name',
      email: 'new@example.com',
    });

    const result = await service.updateProfile('u1', {
      nickname: '  New   Name ',
      email: ' New@Example.com ',
    });

    expect(prisma.user.findFirst).toHaveBeenCalledWith(
      expect.objectContaining({
        where: {
          nickname: { equals: 'New Name', mode: 'insensitive' },
          NOT: { id: 'u1' },
        },
      }),
    );

    expect(prisma.user.findUnique).toHaveBeenCalledWith({
      where: { email: 'new@example.com' },
      select: { id: true },
    });

    expect(prisma.user.update).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: 'u1' },
        data: { nickname: 'New Name', email: 'new@example.com' },
      }),
    );

    expect(result.nickname).toBe('New Name');

    // В аудит не повинні потрапляти персональні значення.
    expect(logSpy).toHaveBeenCalledWith(
      'Profile updated: user=u1, fields=nickname,email',
    );
    expect(JSON.stringify(logSpy.mock.calls)).not.toContain('New Name');
    expect(JSON.stringify(logSpy.mock.calls)).not.toContain('new@example.com');
  });

  it('rejects a nickname taken by another user', async () => {
    prisma.user.findFirst.mockResolvedValue({ id: 'u2' });

    await expect(
      service.updateProfile('u1', { nickname: 'taken' }),
    ).rejects.toBeInstanceOf(ConflictException);

    expect(prisma.user.update).not.toHaveBeenCalled();
  });

  it('rejects an email taken by another user', async () => {
    prisma.user.findUnique
      .mockResolvedValueOnce(profile) // поточний профіль користувача
      .mockResolvedValueOnce({ id: 'u2' }); // email належить іншому користувачу

    await expect(
      service.updateProfile('u1', { email: 'taken@example.com' }),
    ).rejects.toBeInstanceOf(ConflictException);

    expect(prisma.user.update).not.toHaveBeenCalled();
  });

  it('saves other fields when the unchanged nickname has a legacy duplicate', async () => {
    // У БД є другий користувач із тим самим імʼям (реєстрація це дозволяє).
    prisma.user.findFirst.mockResolvedValue({ id: 'u2' });
    prisma.user.update.mockResolvedValue({
      ...profile,
      email: 'fresh@example.com',
    });

    const result = await service.updateProfile('u1', {
      nickname: 'Vasyl',
      email: 'fresh@example.com',
    });

    // Незмінене імʼя навіть не перевіряємо — інакше 409 на рівному місці.
    expect(prisma.user.findFirst).not.toHaveBeenCalled();
    expect(prisma.user.update).toHaveBeenCalledWith(
      expect.objectContaining({ data: { email: 'fresh@example.com' } }),
    );
    expect(result.email).toBe('fresh@example.com');
  });

  it('ignores a case-only nickname clash caused by legacy duplicates', async () => {
    prisma.user.findFirst.mockResolvedValue({ id: 'u2' });
    prisma.user.update.mockResolvedValue({
      ...profile,
      email: 'fresh@example.com',
    });

    await service.updateProfile('u1', {
      nickname: 'VASYL',
      email: 'fresh@example.com',
    });

    expect(prisma.user.update).toHaveBeenCalledWith(
      expect.objectContaining({ data: { email: 'fresh@example.com' } }),
    );
  });

  it('applies a different nickname spelling when it is free', async () => {
    prisma.user.update.mockResolvedValue({ ...profile, nickname: 'Vasylko' });

    const result = await service.updateProfile('u1', {
      nickname: '  Vasylko ',
    });

    expect(prisma.user.update).toHaveBeenCalledWith(
      expect.objectContaining({ data: { nickname: 'Vasylko' } }),
    );
    expect(result.nickname).toBe('Vasylko');
  });

  it('returns the profile unchanged when nothing actually changed', async () => {
    const result = await service.updateProfile('u1', {
      nickname: ' Vasyl ',
      email: 'VASYL@example.com',
    });

    expect(prisma.user.findFirst).not.toHaveBeenCalled();
    expect(prisma.user.update).not.toHaveBeenCalled();
    expect(result.nickname).toBe('Vasyl');
    expect(result.email).toBe('vasyl@example.com');
  });

  it('throws 404 when the profile to update is missing', async () => {
    prisma.user.findUnique.mockResolvedValue(null);

    await expect(
      service.updateProfile('u1', { nickname: 'Vasylko' }),
    ).rejects.toBeInstanceOf(NotFoundException);
  });

  it('maps a database unique violation to 409', async () => {
    prisma.user.update.mockRejectedValue({ code: 'P2002' });

    await expect(
      service.updateProfile('u1', { nickname: 'vasyl' }),
    ).rejects.toBeInstanceOf(ConflictException);
  });

  it('rejects an empty update', async () => {
    await expect(service.updateProfile('u1', {})).rejects.toBeInstanceOf(
      BadRequestException,
    );

    expect(prisma.user.update).not.toHaveBeenCalled();
  });

  it('does not change the password when the current one is wrong', async () => {
    password.verify.mockResolvedValue(false);

    await expect(
      service.changePassword('u1', 's1', {
        currentPassword: 'wrong',
        newPassword: 'NewPassword123',
      }),
    ).rejects.toBeInstanceOf(BadRequestException);

    expect(password.hash).not.toHaveBeenCalled();
    expect(prisma.user.update).not.toHaveBeenCalled();
    expect(prisma.client.authSession.updateMany).not.toHaveBeenCalled();
  });

  it('hashes the new password and revokes other sessions', async () => {
    await service.changePassword('u1', 's1', {
      currentPassword: 'OldPassword123',
      newPassword: 'NewPassword123',
    });

    expect(password.hash).toHaveBeenCalledWith('NewPassword123');
    expect(prisma.user.update).toHaveBeenCalledWith({
      where: { id: 'u1' },
      data: { passwordHash: 'hashed-new-password' },
    });
    expect(prisma.client.authSession.updateMany).toHaveBeenCalledWith({
      where: { userId: 'u1', id: { not: 's1' }, revokedAt: null },
      data: { revokedAt: expect.any(Date) },
    });
  });

  it('rejects an avatar with an unsupported format', async () => {
    await expect(
      service.updateAvatar('u1', {
        originalname: 'avatar.gif',
        mimetype: 'image/gif',
        size: 100,
        path: await tempPng(128, 128),
      }),
    ).rejects.toBeInstanceOf(BadRequestException);

    expect(storage.removeTemp).toHaveBeenCalled();
    expect(storage.moveIntoStorage).not.toHaveBeenCalled();
  });

  it('rejects an avatar smaller than 64x64 and removes its temp file', async () => {
    const path = await tempPng(32, 32);

    await expect(
      service.updateAvatar('u1', {
        originalname: 'avatar.png',
        mimetype: 'image/png',
        size: 33,
        path,
      }),
    ).rejects.toBeInstanceOf(BadRequestException);

    expect(storage.removeTemp).toHaveBeenCalledWith(path);
    expect(storage.moveIntoStorage).not.toHaveBeenCalled();
  });

  it('stores a new avatar and removes the previous file', async () => {
    const path = await tempPng(128, 128);

    prisma.user.findUnique.mockResolvedValue({
      avatarUrl: '/media/avatar/2026/10/old.png',
    });
    prisma.user.update.mockResolvedValue({
      ...profile,
      avatarUrl: '/media/avatar/2026/10/new.png',
    });

    const result = await service.updateAvatar('u1', {
      originalname: 'avatar.png',
      mimetype: 'image/png',
      size: 33,
      path,
    });

    expect(storage.buildStorageKey).toHaveBeenCalledWith('AVATAR', '.png');
    expect(storage.moveIntoStorage).toHaveBeenCalledWith(
      path,
      'avatar/2026/10/new.png',
    );
    expect(prisma.user.update).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: 'u1' },
        data: { avatarUrl: '/media/avatar/2026/10/new.png' },
      }),
    );
    expect(storage.remove).toHaveBeenCalledWith('avatar/2026/10/old.png');
    expect(result.avatarUrl).toBe('/media/avatar/2026/10/new.png');
  });

  it('removes the new avatar if saving the profile fails', async () => {
    const path = await tempPng(128, 128);

    prisma.user.findUnique.mockResolvedValue({ avatarUrl: null });
    prisma.user.update.mockRejectedValue({ code: 'P2025' });

    await expect(
      service.updateAvatar('u1', {
        originalname: 'avatar.png',
        mimetype: 'image/png',
        size: 33,
        path,
      }),
    ).rejects.toBeInstanceOf(NotFoundException);

    expect(storage.remove).toHaveBeenCalledWith('avatar/2026/10/new.png');
  });

  it('returns only the public projection to another user', async () => {
    const result = await service.getProfileForViewer('u1', {
      sub: 'u2',
      role: Role.LISTENER,
    });

    expect(result).toEqual({
      id: 'u1',
      nickname: 'Vasyl',
      avatarUrl: null,
      createdAt: profile.createdAt,
    });
    expect(result).not.toHaveProperty('email');
    expect(result).not.toHaveProperty('role');
    expect(result).not.toHaveProperty('isPublic');
    expect(result).not.toHaveProperty('showCurrentListening');
  });

  it('hides a private profile from other users', async () => {
    prisma.user.findUnique.mockResolvedValue({
      ...profile,
      isPublic: false,
    });

    await expect(
      service.getProfileForViewer('u1', {
        sub: 'u2',
        role: Role.LISTENER,
      }),
    ).rejects.toBeInstanceOf(NotFoundException);
  });

  it('returns private fields to the owner', async () => {
    const result = await service.getProfileForViewer('u1', {
      sub: 'u1',
      role: Role.LISTENER,
    });

    expect(result).toHaveProperty('email', 'vasyl@example.com');
  });

  it('returns private fields to an admin and logs access without PII', async () => {
    const result = await service.getProfileForViewer('u1', {
      sub: 'admin-1',
      role: Role.ADMIN,
    });

    expect(result).toHaveProperty('email', 'vasyl@example.com');
    expect(logSpy).toHaveBeenCalledWith(
      'Admin profile access: actor=admin-1, target=u1',
    );
    expect(JSON.stringify(logSpy.mock.calls)).not.toContain(
      'vasyl@example.com',
    );
  });

  it('searches only public profiles and returns the public projection', async () => {
    const result = await service.searchProfiles({
      q: ' vas ',
      page: 1,
      limit: 20,
    });

    expect(prisma.user.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: {
          isPublic: true,
          isBlocked: false,
          nickname: { contains: 'vas', mode: 'insensitive' },
        },
        skip: 0,
        take: 20,
      }),
    );
    expect(result.total).toBe(1);
    expect(result.items[0]).toEqual({
      id: 'u1',
      nickname: 'Vasyl',
      avatarUrl: null,
      createdAt: profile.createdAt,
    });
    expect(result.items[0]).not.toHaveProperty('email');
  });

  it('updates privacy flags and clears presence when publishing is disabled', async () => {
    prisma.user.update.mockResolvedValue({ ...profile, isPublic: false });

    const result = await service.updatePrivacy('u1', { isPublic: false });

    expect(prisma.user.update).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: 'u1' },
        data: { isPublic: false },
      }),
    );
    expect(prisma.currentListening.deleteMany).toHaveBeenCalledWith({
      where: { userId: 'u1' },
    });
    expect(result.isPublic).toBe(false);
    expect(logSpy).toHaveBeenCalledWith(
      'Profile privacy updated: user=u1, fields=isPublic',
    );
  });

  it('rejects an empty privacy update', async () => {
    await expect(service.updatePrivacy('u1', {})).rejects.toBeInstanceOf(
      BadRequestException,
    );

    expect(prisma.user.update).not.toHaveBeenCalled();
  });
});
