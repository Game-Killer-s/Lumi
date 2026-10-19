import { Role } from '@prisma/client';

import {
  canViewPrivateProfile,
  canViewProfile,
  ProfileUserRow,
  toProfileDto,
  toPublicProfileDto,
} from './profile-projection';

const profile: ProfileUserRow = {
  id: 'u1',
  nickname: 'Vasyl',
  email: 'vasyl@example.com',
  avatarUrl: '/media/avatar/u1.png',
  role: Role.LISTENER,
  isPublic: false,
  showCurrentListening: false,
  createdAt: new Date('2026-10-01T10:00:00.000Z'),
};

describe('profile projection policy', () => {
  it('keeps private fields in the owner/admin projection', () => {
    expect(toProfileDto(profile)).toEqual({
      id: 'u1',
      nickname: 'Vasyl',
      email: 'vasyl@example.com',
      avatarUrl: '/media/avatar/u1.png',
      role: Role.LISTENER,
      isPublic: false,
      showCurrentListening: false,
      createdAt: profile.createdAt,
    });
  });

  it('never leaks email, role or security fields in the public projection', () => {
    const publicProfile = toPublicProfileDto(profile);

    expect(publicProfile).toEqual({
      id: 'u1',
      nickname: 'Vasyl',
      avatarUrl: '/media/avatar/u1.png',
      createdAt: profile.createdAt,
    });
    expect(publicProfile).not.toHaveProperty('email');
    expect(publicProfile).not.toHaveProperty('role');
    expect(publicProfile).not.toHaveProperty('isPublic');
    expect(publicProfile).not.toHaveProperty('showCurrentListening');
    expect(publicProfile).not.toHaveProperty('isBlocked');
    expect(publicProfile).not.toHaveProperty('passwordHash');
  });

  it('allows private fields only for the owner or an admin', () => {
    const owner = { sub: 'u1', role: Role.LISTENER };
    const admin = { sub: 'admin-1', role: Role.ADMIN };
    const stranger = { sub: 'u2', role: Role.LISTENER };

    expect(canViewPrivateProfile(owner, 'u1')).toBe(true);
    expect(canViewPrivateProfile(admin, 'u1')).toBe(true);
    expect(canViewPrivateProfile(stranger, 'u1')).toBe(false);
    expect(canViewPrivateProfile(undefined, 'u1')).toBe(false);
  });

  it('hides a private profile from everyone except owner/admin', () => {
    const admin = { sub: 'admin-1', role: Role.ADMIN };
    const stranger = { sub: 'u2', role: Role.LISTENER };

    expect(canViewProfile(profile, undefined, 'u1')).toBe(false);
    expect(canViewProfile(profile, stranger, 'u1')).toBe(false);
    expect(canViewProfile(profile, admin, 'u1')).toBe(true);
  });

  it('shows a public profile to anonymous users', () => {
    expect(canViewProfile({ isPublic: true }, undefined, 'u1')).toBe(true);
  });
});
