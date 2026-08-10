import {
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { Role } from '@prisma/client';
import { randomUUID } from 'crypto';

import { PrismaService } from '../../prisma/prisma.service';

import { PasswordService } from './services/password.service';
import {
  TokenPair,
  TokenService,
} from './services/token.service';

import { IssueTokenPairInput } from './types/issue-token-pair.type';
import { RefreshTokenPayload } from './types/token-payload.type';

@Injectable()
export class AuthService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly passwordService: PasswordService,
    private readonly tokenService: TokenService,
  ) {}

  hashPassword(password: string): Promise<string> {
    return this.passwordService.hash(password);
  }

  verifyPassword(
    passwordHash: string,
    password: string,
  ): Promise<boolean> {
    return this.passwordService.verify(
      passwordHash,
      password,
    );
  }

  issueTokenPair(
    input: IssueTokenPairInput,
  ): Promise<TokenPair> {
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

  verifyRefreshToken(
    refreshToken: string,
  ): Promise<RefreshTokenPayload> {
    return this.tokenService.verifyRefreshToken(
      refreshToken,
    );
  }

  hashRefreshToken(
    refreshToken: string,
  ): Promise<string> {
    return this.passwordService.hash(refreshToken);
  }

  verifyRefreshTokenHash(
    refreshTokenHash: string,
    refreshToken: string,
  ): Promise<boolean> {
    return this.passwordService.verify(
      refreshTokenHash,
      refreshToken,
    );
  }

  async login(
    login: string,
    password: string,
  ): Promise<TokenPair> {
    const email = login.trim().toLowerCase();

    const user = await this.prisma.user.findUnique({
      where: {
        email,
      },
    });

    if (!user || user.isBlocked) {
      throw new UnauthorizedException(
        'Invalid credentials',
      );
    }

    const passwordValid =
      await this.verifyPassword(
        user.passwordHash,
        password,
      );

    if (!passwordValid) {
      throw new UnauthorizedException(
        'Invalid credentials',
      );
    }

    return this.createSession(
      user.id,
      user.role,
    );
  }

  async refresh(
    refreshToken: string,
  ): Promise<TokenPair> {
    const payload =
      await this.verifyRefreshToken(
        refreshToken,
      );

    const session =
      await this.prisma.authSession.findUnique({
        where: {
          id: payload.sessionId,
        },
        include: {
          user: true,
        },
      });

    if (!session) {
      throw new UnauthorizedException(
        'Session not found',
      );
    }

    if (
      session.revokedAt ||
      session.expiresAt <= new Date()
    ) {
      throw new UnauthorizedException(
        'Session expired or revoked',
      );
    }

    if (
      session.userId !== payload.sub ||
      session.familyId !== payload.familyId
    ) {
      await this.revokeSession(session.id);

      throw new UnauthorizedException(
        'Invalid refresh token',
      );
    }

    if (session.user.isBlocked) {
      await this.revokeSession(session.id);

      throw new UnauthorizedException(
        'User is blocked',
      );
    }

    /*
     * Якщо JTI вже не збігається, значить цей refresh token
     * уже використовувався і після нього був виданий новий.
     *
     * Це refresh-token reuse attack.
     */
    if (session.currentJti !== payload.jti) {
      await this.revokeSession(session.id);

      throw new UnauthorizedException(
        'Refresh token reuse detected',
      );
    }

    const tokenHashValid =
      await this.verifyRefreshTokenHash(
        session.refreshTokenHash,
        refreshToken,
      );

    if (!tokenHashValid) {
      await this.revokeSession(session.id);

      throw new UnauthorizedException(
        'Refresh token reuse detected',
      );
    }

    const nextJti = randomUUID();

    const tokens =
      await this.issueTokenPair({
        userId: session.userId,
        sessionId: session.id,
        familyId: session.familyId,
        refreshJti: nextJti,
        role: session.user.role,
      });

    const refreshTokenHash =
      await this.hashRefreshToken(
        tokens.refreshToken,
      );

    /*
     * updateMany використовується навмисно.
     *
     * Умова currentJti гарантує, що два паралельних запити
     * з одним refresh token не зможуть обидва успішно
     * виконати rotation.
     */
    const updated =
      await this.prisma.authSession.updateMany({
        where: {
          id: session.id,
          currentJti: payload.jti,
          revokedAt: null,
        },
        data: {
          currentJti: nextJti,
          refreshTokenHash,

          // Sliding refresh session.
          expiresAt: new Date(
            Date.now() +
              tokens.refreshTokenExpiresIn * 1000,
          ),
        },
      });

    if (updated.count !== 1) {
      await this.revokeSession(session.id);

      throw new UnauthorizedException(
        'Refresh token reuse detected',
      );
    }

    return tokens;
  }

  async logout(
    sessionId: string,
    userId: string,
  ): Promise<void> {
    await this.prisma.authSession.updateMany({
      where: {
        id: sessionId,
        userId,
        revokedAt: null,
      },
      data: {
        revokedAt: new Date(),
      },
    });
  }

  private async createSession(
    userId: string,
    role: Role,
  ): Promise<TokenPair> {
    const sessionId = randomUUID();
    const familyId = randomUUID();
    const refreshJti = randomUUID();

    const tokens =
      await this.issueTokenPair({
        userId,
        sessionId,
        familyId,
        refreshJti,
        role,
      });

    const refreshTokenHash =
      await this.hashRefreshToken(
        tokens.refreshToken,
      );

    await this.prisma.authSession.create({
      data: {
        id: sessionId,
        userId,
        familyId,
        currentJti: refreshJti,
        refreshTokenHash,

        expiresAt: new Date(
          Date.now() +
            tokens.refreshTokenExpiresIn * 1000,
        ),
      },
    });

    return tokens;
  }

  private async revokeSession(
    sessionId: string,
  ): Promise<void> {
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
}