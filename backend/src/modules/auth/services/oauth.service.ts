import {
  BadRequestException,
  ConflictException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';

import { ConfigService } from '@nestjs/config';

import { OAuthTransactionMode, SecurityAuditEventType } from '@prisma/client';

import { randomBytes } from 'crypto';

import { PrismaService } from '../../../prisma/prisma.service';

import { SecurityCryptoService } from '../../security/security-crypto.service';

import { SecurityAuditService } from '../../security/security-audit.service';

import { OAuthProviderRegistry } from '../oauth/oauth-provider.registry';

import { OAuthIdentity } from '../oauth/oauth-provider.strategy';

import { AuthService } from '../auth.service';

import { TwoFactorService } from './two-factor.service';

@Injectable()
export class OAuthService {
  private readonly transactionTtlMs = 10 * 60 * 1000;

  private readonly handoffTtlMs = 2 * 60 * 1000;

  constructor(
    private readonly prisma: PrismaService,

    private readonly config: ConfigService,

    private readonly crypto: SecurityCryptoService,

    private readonly audit: SecurityAuditService,

    private readonly providers: OAuthProviderRegistry,

    private readonly authService: AuthService,

    private readonly twoFactor: TwoFactorService,
  ) {}

  startLogin(providerName: string, returnTo: string) {
    return this.start(providerName, OAuthTransactionMode.LOGIN, returnTo);
  }

  startLink(providerName: string, returnTo: string, userId: string) {
    return this.start(
      providerName,
      OAuthTransactionMode.LINK,
      returnTo,
      userId,
    );
  }

  async callback(
    providerName: string,
    state: string,
    code: string,
  ): Promise<{
    redirectUrl: string;
  }> {
    const stateHash = this.crypto.hmac(state);

    const transaction = await this.prisma.oAuthTransaction.findFirst({
      where: {
        provider: providerName,

        stateHash,

        consumedAt: null,

        expiresAt: {
          gt: new Date(),
        },
      },
    });

    if (!transaction) {
      throw new UnauthorizedException('Invalid or expired OAuth state');
    }

    /*
     * Один callback можна
     * використати лише один раз.
     */
    const consumed = await this.prisma.oAuthTransaction.updateMany({
      where: {
        id: transaction.id,
        consumedAt: null,
      },

      data: {
        consumedAt: new Date(),
      },
    });

    if (consumed.count !== 1) {
      throw new UnauthorizedException('OAuth state was already used');
    }

    const provider = this.providers.get(providerName);

    const identity = await provider.exchangeCode(code);

    if (transaction.mode === OAuthTransactionMode.LINK) {
      if (!transaction.initiatedByUserId) {
        throw new UnauthorizedException();
      }

      await this.linkAccount(transaction.initiatedByUserId, identity);

      return {
        redirectUrl: this.addQuery(transaction.returnTo, {
          oauthLinked: '1',
        }),
      };
    }

    const user = await this.resolveOAuthUser(identity);

    if (user.isBlocked) {
      throw new UnauthorizedException('User is blocked');
    }

    /*
     * OAuth замінює password factor,
     * але НЕ обходить 2FA.
     */
    if (user.twoFactorEnabled) {
      const challenge = await this.twoFactor.createLoginChallenge(user.id);

      return {
        redirectUrl: this.addQuery(transaction.returnTo, {
          requiresTwoFactor: '1',

          challengeId: challenge.challengeId,
        }),
      };
    }

    /*
     * НІКОЛИ не кладемо JWT
     * у query/deep-link.
     *
     * Видаємо одноразовий handoff.
     */
    const handoffCode = randomBytes(32).toString('base64url');

    await this.prisma.oAuthHandoff.create({
      data: {
        userId: user.id,

        codeHash: this.crypto.hmac(handoffCode),

        expiresAt: new Date(Date.now() + this.handoffTtlMs),
      },
    });

    await this.audit.log({
      type: SecurityAuditEventType.OAUTH_LOGIN,

      userId: user.id,

      metadata: {
        provider: providerName,
      },
    });

    return {
      redirectUrl: this.addQuery(transaction.returnTo, {
        code: handoffCode,
      }),
    };
  }

  async exchangeHandoff(code: string) {
    const codeHash = this.crypto.hmac(code);

    const handoff = await this.prisma.oAuthHandoff.findUnique({
      where: {
        codeHash,
      },

      include: {
        user: true,
      },
    });

    if (
      !handoff ||
      handoff.consumedAt ||
      handoff.expiresAt <= new Date() ||
      handoff.user.isBlocked
    ) {
      throw new UnauthorizedException('Invalid or expired OAuth handoff');
    }

    const consumed = await this.prisma.oAuthHandoff.updateMany({
      where: {
        id: handoff.id,
        consumedAt: null,
      },

      data: {
        consumedAt: new Date(),
      },
    });

    if (consumed.count !== 1) {
      throw new UnauthorizedException('OAuth handoff already used');
    }

    /*
     * Якщо між callback і exchange
     * користувач увімкнув 2FA —
     * handoff більше не приймаємо.
     */
    if (handoff.user.twoFactorEnabled) {
      throw new UnauthorizedException('Two-factor authentication required');
    }

    return this.authService.createSessionForUser(handoff.userId);
  }

  private async start(
    providerName: string,
    mode: OAuthTransactionMode,
    returnTo: string,
    userId?: string,
  ) {
    this.validateReturnTo(returnTo);

    const provider = this.providers.get(providerName);

    const state = randomBytes(32).toString('base64url');

    await this.prisma.oAuthTransaction.create({
      data: {
        provider: provider.name,

        mode,

        stateHash: this.crypto.hmac(state),

        initiatedByUserId: userId,

        returnTo,

        expiresAt: new Date(Date.now() + this.transactionTtlMs),
      },
    });

    return {
      authorizationUrl: provider.getAuthorizationUrl(state),

      expiresIn: this.transactionTtlMs / 1000,
    };
  }

  private async resolveOAuthUser(identity: OAuthIdentity) {
    const existing = await this.prisma.externalAccount.findUnique({
      where: {
        provider_externalId: {
          provider: identity.provider,

          externalId: identity.subject,
        },
      },

      include: {
        user: true,
      },
    });

    if (existing) {
      return existing.user;
    }

    if (!identity.emailVerified) {
      throw new UnauthorizedException('OAuth email is not verified');
    }

    /*
     * SAFE LINKING:
     *
     * Не auto-link по email.
     */
    const sameEmailUser = await this.prisma.user.findUnique({
      where: {
        email: identity.email,
      },
    });

    if (sameEmailUser) {
      await this.audit.log({
        type: SecurityAuditEventType.OAUTH_LINK_REJECTED,

        userId: sameEmailUser.id,

        metadata: {
          provider: identity.provider,

          reason: 'existing-email-requires-explicit-link',
        },
      });

      throw new ConflictException(
        'Account already exists. Sign in normally and link this OAuth provider from security settings.',
      );
    }

    return this.prisma.user.create({
      data: {
        email: identity.email,

        /*
         * OAuth-only user.
         */
        passwordHash: null,

        nickname: identity.displayName?.trim() || identity.email.split('@')[0],

        avatarUrl: identity.avatarUrl,

        externalAccounts: {
          create: {
            provider: identity.provider,

            externalId: identity.subject,

            email: identity.email,

            emailVerified: identity.emailVerified,
          },
        },
      },
    });
  }

  private async linkAccount(
    userId: string,
    identity: OAuthIdentity,
  ): Promise<void> {
    const external = await this.prisma.externalAccount.findUnique({
      where: {
        provider_externalId: {
          provider: identity.provider,

          externalId: identity.subject,
        },
      },
    });

    if (external && external.userId !== userId) {
      throw new ConflictException(
        'OAuth account is already linked to another user',
      );
    }

    const existingProvider = await this.prisma.externalAccount.findUnique({
      where: {
        userId_provider: {
          userId,

          provider: identity.provider,
        },
      },
    });

    if (existingProvider && existingProvider.externalId !== identity.subject) {
      throw new ConflictException(
        'Another account from this OAuth provider is already linked',
      );
    }

    if (!external) {
      await this.prisma.externalAccount.create({
        data: {
          userId,

          provider: identity.provider,

          externalId: identity.subject,

          email: identity.email,

          emailVerified: identity.emailVerified,
        },
      });
    }

    await this.audit.log({
      type: SecurityAuditEventType.OAUTH_LINKED,

      userId,

      metadata: {
        provider: identity.provider,
      },
    });
  }

  private validateReturnTo(returnTo: string): void {
    const allowed = this.config.getOrThrow<string[]>('oauth.allowedReturnUrls');

    if (!allowed.includes(returnTo)) {
      throw new BadRequestException('Invalid OAuth return URL');
    }
  }

  private addQuery(returnTo: string, values: Record<string, string>): string {
    const url = new URL(returnTo);

    for (const [key, value] of Object.entries(values)) {
      url.searchParams.set(key, value);
    }

    return url.toString();
  }
}
