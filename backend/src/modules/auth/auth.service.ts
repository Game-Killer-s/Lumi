import { Injectable, UnauthorizedException } from '@nestjs/common';

import { Role, SecurityAuditEventType } from '@prisma/client';

import { randomUUID } from 'crypto';

import { PrismaService } from '../../prisma/prisma.service';

import { SecurityAuditService } from '../security/security-audit.service';

import { PasswordService } from './services/password.service';

import { TokenPair, TokenService } from './services/token.service';

import {
  TwoFactorChallengeResult,
  TwoFactorService,
} from './services/two-factor.service';

import { IssueTokenPairInput } from './types/issue-token-pair.type';

import { RefreshTokenPayload } from './types/token-payload.type';

@Injectable()
export class AuthService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly passwordService: PasswordService,
    private readonly tokenService: TokenService,
    private readonly twoFactorService: TwoFactorService,
    private readonly audit: SecurityAuditService,
  ) {}

  // ============================================================
  // Password helpers
  // ============================================================

  hashPassword(password: string): Promise<string> {
    return this.passwordService.hash(password);
  }

  verifyPassword(passwordHash: string, password: string): Promise<boolean> {
    return this.passwordService.verify(passwordHash, password);
  }

  // ============================================================
  // Token helpers
  // ============================================================

  issueTokenPair(input: IssueTokenPairInput): Promise<TokenPair> {
    return this.tokenService.signTokenPair(
      {
        sub: input.userId,
        sessionId: input.sessionId,
        role: input.role,
        type: 'access',
      },
      {
        sub: input.userId,
        sessionId: input.sessionId,
        familyId: input.familyId,
        jti: input.refreshJti,
        type: 'refresh',
      },
    );
  }

  verifyRefreshToken(refreshToken: string): Promise<RefreshTokenPayload> {
    return this.tokenService.verifyRefreshToken(refreshToken);
  }

  hashRefreshToken(refreshToken: string): Promise<string> {
    return this.passwordService.hash(refreshToken);
  }

  verifyRefreshTokenHash(
    refreshTokenHash: string,
    refreshToken: string,
  ): Promise<boolean> {
    return this.passwordService.verify(refreshTokenHash, refreshToken);
  }

  // ============================================================
  // Login
  // ============================================================

  async login(
    login: string,
    password: string,
  ): Promise<TokenPair | TwoFactorChallengeResult> {
    const email = login.trim().toLowerCase();

    const user = await this.prisma.user.findUnique({
      where: {
        email,
      },
    });

    /*
     * passwordHash nullable,
     * тому OAuth-only account
     * не може входити через пароль.
     */
    if (!user || user.isBlocked || !user.passwordHash) {
      throw new UnauthorizedException('Invalid credentials');
    }

    const passwordValid = await this.verifyPassword(
      user.passwordHash,
      password,
    );

    if (!passwordValid) {
      await this.audit.log({
        type: SecurityAuditEventType.LOGIN_FAILED,

        userId: user.id,

        metadata: {
          method: 'password',
        },
      });

      throw new UnauthorizedException('Invalid credentials');
    }

    /*
     * Якщо 2FA увімкнено,
     * JWT ще не видаємо.
     */
    if (user.twoFactorEnabled) {
      return this.twoFactorService.createLoginChallenge(user.id);
    }

    const tokens = await this.createSession(user.id, user.role);

    await this.audit.log({
      type: SecurityAuditEventType.LOGIN_SUCCESS,

      userId: user.id,

      metadata: {
        method: 'password',
        twoFactor: false,
      },
    });

    return tokens;
  }

  // ============================================================
  // Complete 2FA login
  // ============================================================

  async completeTwoFactorLogin(
    challengeId: string,
    code: string,
  ): Promise<TokenPair> {
    const userId = await this.twoFactorService.verifyLoginChallenge(
      challengeId,
      code,
    );

    const tokens = await this.createSessionForUser(userId);

    await this.audit.log({
      type: SecurityAuditEventType.LOGIN_SUCCESS,

      userId,

      metadata: {
        method: 'password',
        twoFactor: true,
      },
    });

    return tokens;
  }

  // ============================================================
  // Create session for known user
  // ============================================================

  /*
   * Використовується 2FA та OAuth.
   */
  async createSessionForUser(userId: string): Promise<TokenPair> {
    const user = await this.prisma.user.findUnique({
      where: {
        id: userId,
      },

      select: {
        id: true,
        role: true,
        isBlocked: true,
      },
    });

    if (!user || user.isBlocked) {
      throw new UnauthorizedException('User is unavailable');
    }

    return this.createSession(user.id, user.role);
  }

  // ============================================================
  // Refresh
  // ============================================================

  async refresh(refreshToken: string): Promise<TokenPair> {
    const payload = await this.verifyRefreshToken(refreshToken);

    const session = await this.prisma.authSession.findUnique({
      where: {
        id: payload.sessionId,
      },

      include: {
        user: true,
      },
    });

    if (!session) {
      throw new UnauthorizedException('Session not found');
    }

    if (session.revokedAt || session.expiresAt <= new Date()) {
      throw new UnauthorizedException('Session expired or revoked');
    }

    /*
     * Refresh token має належати
     * саме цій session/family/user.
     */
    if (
      session.userId !== payload.sub ||
      session.familyId !== payload.familyId
    ) {
      await this.revokeSession(session.id);

      await this.logRefreshReuse(
        session.userId,
        session.id,
        'payload-mismatch',
      );

      throw new UnauthorizedException('Invalid refresh token');
    }

    if (session.user.isBlocked) {
      await this.revokeSession(session.id);

      throw new UnauthorizedException('User is blocked');
    }

    /*
     * JTI mismatch =
     * старий refresh token
     * використаний повторно.
     */
    if (session.currentJti !== payload.jti) {
      await this.revokeSession(session.id);

      await this.logRefreshReuse(session.userId, session.id, 'jti-mismatch');

      throw new UnauthorizedException('Refresh token reuse detected');
    }

    const tokenHashValid = await this.verifyRefreshTokenHash(
      session.refreshTokenHash,
      refreshToken,
    );

    if (!tokenHashValid) {
      await this.revokeSession(session.id);

      await this.logRefreshReuse(session.userId, session.id, 'hash-mismatch');

      throw new UnauthorizedException('Refresh token reuse detected');
    }

    const nextJti = randomUUID();

    const tokens = await this.issueTokenPair({
      userId: session.userId,

      sessionId: session.id,

      familyId: session.familyId,

      refreshJti: nextJti,

      role: session.user.role,
    });

    const refreshTokenHash = await this.hashRefreshToken(tokens.refreshToken);

    /*
     * Атомарна refresh rotation.
     *
     * Лише request зі старим
     * currentJti може оновити session.
     */
    const updated = await this.prisma.authSession.updateMany({
      where: {
        id: session.id,

        currentJti: payload.jti,

        revokedAt: null,
      },

      data: {
        currentJti: nextJti,

        refreshTokenHash,

        expiresAt: new Date(Date.now() + tokens.refreshTokenExpiresIn * 1000),
      },
    });

    /*
     * Інший request уже використав
     * цей refresh token.
     */
    if (updated.count !== 1) {
      await this.revokeSession(session.id);

      await this.logRefreshReuse(
        session.userId,
        session.id,
        'concurrent-refresh',
      );

      throw new UnauthorizedException('Refresh token reuse detected');
    }

    return tokens;
  }

  // ============================================================
  // Logout current session
  // ============================================================

  async logout(sessionId: string, userId: string): Promise<void> {
    const result = await this.prisma.authSession.updateMany({
      where: {
        id: sessionId,
        userId,
        revokedAt: null,
      },

      data: {
        revokedAt: new Date(),
      },
    });

    if (result.count === 1) {
      await this.audit.log({
        type: SecurityAuditEventType.SESSION_REVOKED,

        userId,

        metadata: {
          sessionId,
          reason: 'logout',
        },
      });
    }
  }

  // ============================================================
  // Revoke specific session
  // ============================================================

  /*
   * Наприклад:
   *
   * телефон
   * ноутбук
   * старий браузер
   *
   * Користувач може завершити
   * конкретну session.
   */
  async revokeUserSession(userId: string, sessionId: string): Promise<void> {
    const result = await this.prisma.authSession.updateMany({
      where: {
        id: sessionId,

        /*
         * Критично:
         * не даємо користувачу
         * revoke чужої session.
         */
        userId,

        revokedAt: null,
      },

      data: {
        revokedAt: new Date(),
      },
    });

    if (result.count === 1) {
      await this.audit.log({
        type: SecurityAuditEventType.SESSION_REVOKED,

        userId,

        metadata: {
          sessionId,
          reason: 'manual-revoke',
        },
      });
    }
  }

  // ============================================================
  // Revoke ALL sessions
  // ============================================================

  /*
   * "Вийти на всіх пристроях".
   *
   * Включно з поточною session.
   */
  async revokeAllSessions(userId: string): Promise<void> {
    const result = await this.prisma.authSession.updateMany({
      where: {
        userId,
        revokedAt: null,
      },

      data: {
        revokedAt: new Date(),
      },
    });

    await this.audit.log({
      type: SecurityAuditEventType.ALL_SESSIONS_REVOKED,

      userId,

      metadata: {
        revokedSessions: result.count,
      },
    });
  }

  // ============================================================
  // Private: create session
  // ============================================================

  private async createSession(userId: string, role: Role): Promise<TokenPair> {
    const sessionId = randomUUID();

    const familyId = randomUUID();

    const refreshJti = randomUUID();

    const tokens = await this.issueTokenPair({
      userId,
      sessionId,
      familyId,
      refreshJti,
      role,
    });

    const refreshTokenHash = await this.hashRefreshToken(tokens.refreshToken);

    await this.prisma.authSession.create({
      data: {
        id: sessionId,

        userId,

        familyId,

        currentJti: refreshJti,

        refreshTokenHash,

        expiresAt: new Date(Date.now() + tokens.refreshTokenExpiresIn * 1000),
      },
    });

    return tokens;
  }

  // ============================================================
  // Private: internal session revoke
  // ============================================================

  /*
   * Використовується всередині
   * security checks.
   *
   * Наприклад при refresh reuse.
   */
  private async revokeSession(sessionId: string): Promise<void> {
    await this.prisma.authSession.updateMany({
      where: {
        id: sessionId,
        revokedAt: null,
      },

      data: {
        revokedAt: new Date(),
      },
    });
  }

  // ============================================================
  // Private: refresh reuse audit
  // ============================================================

  private async logRefreshReuse(
    userId: string,
    sessionId: string,
    reason: string,
  ): Promise<void> {
    await this.audit.log({
      type: SecurityAuditEventType.REFRESH_REUSE_DETECTED,

      userId,

      metadata: {
        sessionId,
        reason,
      },
    });
  }
}
