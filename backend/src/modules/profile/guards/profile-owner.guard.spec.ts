import {
  ExecutionContext,
  ForbiddenException,
  UnauthorizedException,
} from '@nestjs/common';

import { ProfileOwnerGuard } from './profile-owner.guard';

function contextFor(
  userId: string | undefined,
  params: { userId?: string },
): ExecutionContext {
  return {
    switchToHttp: () => ({
      getRequest: () => ({
        user: userId
          ? { sub: userId, sessionId: 's1', role: 'LISTENER' }
          : undefined,
        params,
      }),
    }),
  } as unknown as ExecutionContext;
}

describe('ProfileOwnerGuard', () => {
  const guard = new ProfileOwnerGuard();

  it('allows the owner to change the profile', () => {
    expect(guard.canActivate(contextFor('u1', { userId: 'u1' }))).toBe(true);
  });

  it('allows routes without a user id parameter', () => {
    expect(guard.canActivate(contextFor('u1', {}))).toBe(true);
  });

  it('forbids editing another account', () => {
    expect(() => guard.canActivate(contextFor('u1', { userId: 'u2' }))).toThrow(
      ForbiddenException,
    );
  });

  it('requires an authenticated user', () => {
    expect(() => guard.canActivate(contextFor(undefined, {}))).toThrow(
      UnauthorizedException,
    );
  });
});
