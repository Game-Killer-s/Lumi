import {
  BadRequestException,
  ConflictException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Role, User } from '@prisma/client';
import { OAuth2Client } from 'google-auth-library';
import { randomBytes, randomUUID } from 'crypto';

import { PrismaService } from '../../prisma/prisma.service';

import { AuthResponseDto } from './dto/auth-response.dto';
import { PasswordService } from './services/password.service';
import {
  TokenPair,
  TokenService,
} from './services/token.service';

import { IssueTokenPairInput } from './types/issue-token-pair.type';
import { RefreshTokenPayload } from './types/token-payload.type';

import { MailService } from './services/mail.service';

// Термін дії токена відновлення пароля — 1 година.
const RESET_TOKEN_TTL_MS = 60 * 60 * 1000;

@Injectable()
export class AuthService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly passwordService: PasswordService,
    private readonly tokenService: TokenService,
    private readonly configService: ConfigService,
    private readonly mailService: MailService,
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
    email: string,
    password: string,
  ): Promise<AuthResponseDto> {
    const normalizedEmail = email.trim().toLowerCase();

    const user = await this.prisma.client.user.findUnique({
      where: {
        email: normalizedEmail,
      },
    });

    if (!user || user.isBlocked) {
      throw new UnauthorizedException(
        'Користувача з таким email не знайдено',
      );
    }

    const passwordValid =
      await this.verifyPassword(
        user.passwordHash,
        password,
      );

    if (!passwordValid) {
      throw new UnauthorizedException(
        'Неправильний пароль',
      );
    }

    const tokens = await this.createSession(
      user.id,
      user.role,
    );

    return this.toAuthResponse(user, tokens);
  }

  async register(
    nickname: string,
    email: string,
    password: string,
  ): Promise<AuthResponseDto> {
    const normalizedEmail = email.trim().toLowerCase();

    const existing =
      await this.prisma.client.user.findUnique({
        where: {
          email: normalizedEmail,
        },
      });

    if (existing) {
      throw new ConflictException(
        'Користувач з таким email вже існує',
      );
    }

    const passwordHash =
      await this.hashPassword(password);

    let user: User;

    try {
      user = await this.prisma.client.user.create({
        data: {
          email: normalizedEmail,
          passwordHash,
          nickname: nickname.trim(),
        },
      });
    } catch (error) {
      // P2002 — unique constraint violation.
      // Можлива гонка між перевіркою вище і create.
      if (this.isUniqueViolation(error)) {
        throw new ConflictException(
          'Користувач з таким email вже існує',
        );
      }

      throw error;
    }

    const tokens = await this.createSession(
      user.id,
      user.role,
    );

    return this.toAuthResponse(user, tokens);
  }

  async googleLogin(
    idToken: string,
  ): Promise<AuthResponseDto> {
    const googleClientId =
      this.configService.get<string>('google.clientId');

    if (!googleClientId) {
      throw new BadRequestException(
        'Google Sign-In не налаштовано на сервері (GOOGLE_CLIENT_ID)',
      );
    }

    const client = new OAuth2Client(googleClientId);

    const ticket = await client.verifyIdToken({
      idToken,
      audience: googleClientId,
    });

    const payload = ticket.getPayload();

    const email = payload?.email;

    if (!email) {
      throw new UnauthorizedException(
        'Не вдалося отримати email з Google-акаунта',
      );
    }

    const normalizedEmail = email.trim().toLowerCase();
    const nickname =
      payload?.name?.trim() ||
      normalizedEmail.split('@')[0];

    let user =
      await this.prisma.client.user.findUnique({
        where: {
          email: normalizedEmail,
        },
      });

    if (!user) {
      // Швидка реєстрація в один клік:
      // акаунт створюється автоматично.
      const passwordHash =
        await this.hashPassword(randomUUID());

      user = await this.prisma.client.user.create({
        data: {
          email: normalizedEmail,
          passwordHash,
          nickname,
        },
      });
    }

    if (user.isBlocked) {
      throw new UnauthorizedException(
        'Користувача з таким email не знайдено',
      );
    }

    const tokens = await this.createSession(
      user.id,
      user.role,
    );

    return this.toAuthResponse(user, tokens);
  }

  async forgotPassword(email: string): Promise<void> {
    const normalizedEmail = email.trim().toLowerCase();

    const user = await this.prisma.client.user.findUnique({
      where: {
        email: normalizedEmail,
      },
    });

    // Відповідь однакова незалежно від того,
    // чи існує акаунт, щоб не розкривати email'и.
    if (!user) {
      return;
    }

    // Анулюємо всі попередні активні токени користувача.
    await this.prisma.client.passwordResetToken.updateMany({
      where: {
        userId: user.id,
        usedAt: null,
        expiresAt: { gt: new Date() },
      },
      data: {
        expiresAt: new Date(0),
      },
    });

    const token = randomBytes(32).toString('hex');
    const tokenHash = await this.hashPassword(token);

    await this.prisma.client.passwordResetToken.create({
      data: {
        userId: user.id,
        tokenHash,
        expiresAt: new Date(
          Date.now() + RESET_TOKEN_TTL_MS,
        ),
      },
    });

    await this.mailService.sendPasswordReset(
      user.email,
      token,
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
      await this.prisma.client.authSession.findUnique({
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
      await this.prisma.client.authSession.updateMany({
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
    await this.prisma.client.authSession.updateMany({
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

    await this.prisma.client.authSession.create({
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
    await this.prisma.client.authSession.updateMany({
      where: {
        id: sessionId,
        revokedAt: null,
      },
      data: {
        revokedAt: new Date(),
      },
    });
  }

  private toAuthResponse(
    user: User,
    tokens: TokenPair,
  ): AuthResponseDto {
    return {
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      tokenType: tokens.tokenType,
      accessTokenExpiresIn: tokens.accessTokenExpiresIn,
      refreshTokenExpiresIn: tokens.refreshTokenExpiresIn,
      user: {
        id: user.id,
        nickname: user.nickname,
        email: user.email,
        avatarUrl: user.avatarUrl,
        role: user.role,
        createdAt: user.createdAt,
      },
    };
  }

  private isUniqueViolation(error: unknown): boolean {
    return (
      typeof error === 'object' &&
      error !== null &&
      (error as { code?: string }).code === 'P2002'
    );
  }
}