import { Role } from '@prisma/client';

import { ProfileDto } from './dto/profile.dto';
import { PublicProfileDto } from './dto/public-profile.dto';

export interface ProfileUserRow {
  id: string;
  nickname: string;
  email: string;
  avatarUrl: string | null;
  role: Role;
  isPublic: boolean;
  showCurrentListening: boolean;
  createdAt: Date;
}

export interface PublicProfileRow {
  id: string;
  nickname: string;
  avatarUrl: string | null;
  createdAt: Date;
}

export interface ProfileViewer {
  sub: string;
  role: Role;
}

// Приватна проєкція: тільки для власника або адміністратора.
export function toProfileDto(user: ProfileUserRow): ProfileDto {
  return {
    id: user.id,
    nickname: user.nickname,
    email: user.email,
    avatarUrl: user.avatarUrl,
    role: user.role,
    isPublic: user.isPublic,
    showCurrentListening: user.showCurrentListening,
    createdAt: user.createdAt,
  };
}

// Публічна проєкція: явний whitelist полів.
// email, role, privacy flags, isBlocked, passwordHash сюди не потрапляють.
export function toPublicProfileDto(user: PublicProfileRow): PublicProfileDto {
  return {
    id: user.id,
    nickname: user.nickname,
    avatarUrl: user.avatarUrl,
    createdAt: user.createdAt,
  };
}

export function canViewPrivateProfile(
  viewer: ProfileViewer | null | undefined,
  targetUserId: string,
): boolean {
  if (!viewer) {
    return false;
  }

  return viewer.sub === targetUserId || viewer.role === Role.ADMIN;
}

export function canViewProfile(
  profile: { isPublic: boolean },
  viewer: ProfileViewer | null | undefined,
  targetUserId: string,
): boolean {
  return profile.isPublic || canViewPrivateProfile(viewer, targetUserId);
}
