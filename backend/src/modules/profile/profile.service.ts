import {
  BadRequestException,
  ConflictException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { Role } from '@prisma/client';
import { promises as fs } from 'node:fs';

import { PrismaService } from '../../prisma/prisma.service';
import { PasswordService } from '../auth/services/password.service';
import {
  extensionForMedia,
  mediaPublicUrl,
  UploadedMediaFile,
} from '../media/media.constants';
import { MediaStorageService } from '../media/media-storage.service';
import { AVATAR_MAX_DIMENSION, AVATAR_MIN_DIMENSION } from './avatar.constants';
import { readImageDimensions } from './avatar-image.util';
import { ChangePasswordDto } from './dto/change-password.dto';
import { ProfileDto, UpdateProfileDto } from './dto/profile.dto';
import { UpdatePrivacyDto } from './dto/privacy.dto';
import {
  PublicProfileDto,
  PublicProfileListDto,
} from './dto/public-profile.dto';
import { SearchProfilesQueryDto } from './dto/search-profiles-query.dto';
import {
  canViewPrivateProfile,
  canViewProfile,
  ProfileViewer,
  toProfileDto,
  toPublicProfileDto,
} from './profile-projection';
import {
  normalizeEmail,
  normalizeNickname,
  storageKeyFromUrl,
} from './profile.utils';

const PROFILE_SELECT = {
  id: true,
  nickname: true,
  email: true,
  avatarUrl: true,
  role: true,
  isPublic: true,
  showCurrentListening: true,
  isBlocked: true,
  createdAt: true,
} as const;

const PUBLIC_PROFILE_SELECT = {
  id: true,
  nickname: true,
  avatarUrl: true,
  createdAt: true,
} as const;

@Injectable()
export class ProfileService {
  private readonly logger = new Logger(ProfileService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly passwordService: PasswordService,
    private readonly storage: MediaStorageService,
  ) {}

  async getProfile(userId: string): Promise<ProfileDto> {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: PROFILE_SELECT,
    });

    if (!user) {
      throw new NotFoundException('Profile not found');
    }

    return toProfileDto(user);
  }

  // Профіль за id: публічна проєкція для сторонніх,
  // приватна — для власника або адміністратора.
  async getProfileForViewer(
    targetUserId: string,
    viewer?: ProfileViewer,
  ): Promise<ProfileDto | PublicProfileDto> {
    const user = await this.prisma.user.findUnique({
      where: { id: targetUserId },
      select: PROFILE_SELECT,
    });

    if (!user) {
      throw new NotFoundException('Profile not found');
    }

    const privileged = canViewPrivateProfile(viewer, targetUserId);

    if (user.isBlocked && !privileged) {
      throw new NotFoundException('Profile not found');
    }

    if (!canViewProfile(user, viewer, targetUserId)) {
      throw new NotFoundException('Profile not found');
    }

    if (privileged) {
      if (viewer?.role === Role.ADMIN && viewer.sub !== targetUserId) {
        // Аудит доступу до приватних полів: без email та інших PII.
        this.logger.log(
          `Admin profile access: actor=${viewer.sub}, target=${targetUserId}`,
        );
      }

      return toProfileDto(user);
    }

    return toPublicProfileDto(user);
  }

  // Пошук бачить лише публічні, незаблоковані профілі.
  async searchProfiles(
    query: SearchProfilesQueryDto,
  ): Promise<PublicProfileListDto> {
    const nickname = query.q.trim();

    if (!nickname) {
      throw new BadRequestException('Search query must not be blank');
    }

    const where = {
      isPublic: true,
      isBlocked: false,
      nickname: { contains: nickname, mode: 'insensitive' as const },
    };

    const [users, total] = await Promise.all([
      this.prisma.user.findMany({
        where,
        select: PUBLIC_PROFILE_SELECT,
        orderBy: { nickname: 'asc' },
        skip: (query.page - 1) * query.limit,
        take: query.limit,
      }),
      this.prisma.user.count({ where }),
    ]);

    return {
      items: users.map((user) => toPublicProfileDto(user)),
      page: query.page,
      limit: query.limit,
      total,
    };
  }

  async updatePrivacy(
    userId: string,
    dto: UpdatePrivacyDto,
  ): Promise<ProfileDto> {
    const data: { isPublic?: boolean; showCurrentListening?: boolean } = {};
    const changed: string[] = [];

    if (dto.isPublic !== undefined) {
      data.isPublic = dto.isPublic;
      changed.push('isPublic');
    }

    if (dto.showCurrentListening !== undefined) {
      data.showCurrentListening = dto.showCurrentListening;
      changed.push('showCurrentListening');
    }

    if (changed.length === 0) {
      throw new BadRequestException('No privacy settings to update');
    }

    try {
      const user = await this.prisma.transaction(async (tx) => {
        const updated = await tx.user.update({
          where: { id: userId },
          data,
          select: PROFILE_SELECT,
        });

        // Якщо presence більше не можна публікувати — прибираємо його.
        if (data.isPublic === false || data.showCurrentListening === false) {
          await tx.currentListening.deleteMany({ where: { userId } });
        }

        return updated;
      });

      this.logger.log(
        `Profile privacy updated: user=${userId}, fields=${changed.join(',')}`,
      );

      return toProfileDto(user);
    } catch (error) {
      if (this.hasPrismaCode(error, 'P2025')) {
        throw new NotFoundException('Profile not found');
      }

      throw error;
    }
  }

  async updateProfile(
    userId: string,
    dto: UpdateProfileDto,
  ): Promise<ProfileDto> {
    const data: { nickname?: string; email?: string } = {};
    const changed: string[] = [];

    if (dto.nickname !== undefined) {
      const nickname = normalizeNickname(dto.nickname);

      if (!nickname) {
        throw new BadRequestException('Nickname must not be blank');
      }

      const existing = await this.prisma.user.findFirst({
        where: {
          nickname: { equals: nickname, mode: 'insensitive' },
          NOT: { id: userId },
        },
        select: { id: true },
      });

      if (existing) {
        throw new ConflictException('Nickname is already taken');
      }

      data.nickname = nickname;
      changed.push('nickname');
    }

    if (dto.email !== undefined) {
      const email = normalizeEmail(dto.email);
      const existing = await this.prisma.user.findUnique({
        where: { email },
        select: { id: true },
      });

      if (existing && existing.id !== userId) {
        throw new ConflictException('Email is already in use');
      }

      data.email = email;
      changed.push('email');
    }

    if (changed.length === 0) {
      throw new BadRequestException('No profile fields to update');
    }

    try {
      const user = await this.prisma.user.update({
        where: { id: userId },
        data,
        select: PROFILE_SELECT,
      });

      // Не логуємо значення полів — лише факт зміни.
      this.logger.log(
        `Profile updated: user=${userId}, fields=${changed.join(',')}`,
      );

      return toProfileDto(user);
    } catch (error) {
      if (this.hasPrismaCode(error, 'P2002')) {
        throw new ConflictException('Email or nickname is already in use');
      }

      if (this.hasPrismaCode(error, 'P2025')) {
        throw new NotFoundException('Profile not found');
      }

      throw error;
    }
  }

  async changePassword(
    userId: string,
    currentSessionId: string,
    dto: ChangePasswordDto,
  ): Promise<void> {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: { passwordHash: true },
    });

    if (!user) {
      throw new NotFoundException('Profile not found');
    }

    const passwordMatches = await this.passwordService.verify(
      user.passwordHash,
      dto.currentPassword,
    );

    if (!passwordMatches) {
      throw new BadRequestException('Current password is incorrect');
    }

    const passwordHash = await this.passwordService.hash(dto.newPassword);

    // Хеш і відкликання сесій змінюються атомарно.
    await this.prisma.transaction(async (tx) => {
      await tx.user.update({
        where: { id: userId },
        data: { passwordHash },
      });

      // Політика: поточна сесія лишається активною, всі інші відкликаються.
      await tx.authSession.updateMany({
        where: {
          userId,
          id: { not: currentSessionId },
          revokedAt: null,
        },
        data: { revokedAt: new Date() },
      });
    });

    this.logger.log(`Password changed: user=${userId}`);
  }

  async updateAvatar(
    userId: string,
    file: UploadedMediaFile | undefined,
  ): Promise<ProfileDto> {
    if (!file) {
      throw new BadRequestException('Avatar file is required');
    }

    const extension = extensionForMedia(
      'AVATAR',
      file.originalname,
      file.mimetype,
    );

    if (!extension) {
      await this.storage.removeTemp(file.path);
      throw new BadRequestException('Only JPG and PNG avatars are supported');
    }

    const dimensions = await this.readDimensions(file);

    try {
      this.assertAvatarDimensions(dimensions);
    } catch (error) {
      await this.storage.removeTemp(file.path);
      throw error;
    }

    const previous = await this.prisma.user.findUnique({
      where: { id: userId },
      select: { avatarUrl: true },
    });

    if (!previous) {
      await this.storage.removeTemp(file.path);
      throw new NotFoundException('Profile not found');
    }

    const storageKey = this.storage.buildStorageKey('AVATAR', extension);

    try {
      await this.storage.moveIntoStorage(file.path, storageKey);
    } catch (error) {
      await this.storage.removeTemp(file.path);
      throw error;
    }

    try {
      const user = await this.prisma.user.update({
        where: { id: userId },
        data: { avatarUrl: mediaPublicUrl(storageKey) },
        select: PROFILE_SELECT,
      });

      if (previous.avatarUrl) {
        const oldKey = storageKeyFromUrl(previous.avatarUrl);

        if (oldKey) {
          try {
            await this.storage.remove(oldKey);
          } catch {
            this.logger.warn(`Cannot remove old avatar: ${oldKey}`);
          }
        }
      }

      this.logger.log(`Avatar updated: user=${userId}`);

      return toProfileDto(user);
    } catch (error) {
      await this.storage.remove(storageKey);

      if (this.hasPrismaCode(error, 'P2025')) {
        throw new NotFoundException('Profile not found');
      }

      throw error;
    }
  }

  private async readDimensions(
    file: UploadedMediaFile,
  ): Promise<{ width: number; height: number } | null> {
    try {
      const buffer = await fs.readFile(file.path);

      return readImageDimensions(buffer);
    } catch {
      await this.storage.removeTemp(file.path);

      throw new BadRequestException('Cannot read avatar file');
    }
  }

  private assertAvatarDimensions(
    dimensions: { width: number; height: number } | null,
  ): void {
    if (!dimensions) {
      throw new BadRequestException('Avatar must be a valid JPG or PNG image');
    }

    const { width, height } = dimensions;

    if (width < AVATAR_MIN_DIMENSION || height < AVATAR_MIN_DIMENSION) {
      throw new BadRequestException(
        `Avatar must be at least ${AVATAR_MIN_DIMENSION}x${AVATAR_MIN_DIMENSION}`,
      );
    }

    if (width > AVATAR_MAX_DIMENSION || height > AVATAR_MAX_DIMENSION) {
      throw new BadRequestException(
        `Avatar must be at most ${AVATAR_MAX_DIMENSION}x${AVATAR_MAX_DIMENSION}`,
      );
    }
  }

  private hasPrismaCode(error: unknown, code: string): boolean {
    return (error as { code?: string })?.code === code;
  }
}
