import {
  BadRequestException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';

import { SecurityAuditEventType } from '@prisma/client';

import { ConfigService } from '@nestjs/config';

import { generateSecret, generateURI, verify } from 'otplib';

import { randomBytes } from 'crypto';

import { PrismaService } from '../../../prisma/prisma.service';

import { SecurityCryptoService } from '../../security/security-crypto.service';

import { SecurityAuditService } from '../../security/security-audit.service';

import { PasswordService } from './password.service';

// ============================================================
// Result returned after password verification
// ============================================================

export interface TwoFactorChallengeResult {
  requiresTwoFactor: true;

  challengeId: string;

  expiresIn: number;
}

@Injectable()
export class TwoFactorService {
  /*
   * Login challenge:
   * 5 minutes.
   */
  private readonly challengeTtlMs = 5 * 60 * 1000;

  /*
   * Enrollment:
   * 10 minutes.
   */
  private readonly enrollmentTtlMs = 10 * 60 * 1000;

  /*
   * Maximum TOTP/recovery attempts
   * for a single login challenge.
   */
  private readonly maxAttempts = 5;

  /*
   * Accept one standard TOTP period
   * of clock drift.
   *
   * otplib v13 uses seconds,
   * not the old "window: 1".
   */
  private readonly totpToleranceSeconds = 30;

  constructor(
    private readonly prisma: PrismaService,

    private readonly config: ConfigService,

    private readonly crypto: SecurityCryptoService,

    private readonly audit: SecurityAuditService,

    private readonly passwordService: PasswordService,
  ) {}

  // ============================================================
  // Create login challenge
  // ============================================================

  async createLoginChallenge(
    userId: string,
  ): Promise<TwoFactorChallengeResult> {
    /*
     * Закриваємо попередні
     * незавершені challenge.
     */
    await this.prisma.twoFactorChallenge.updateMany({
      where: {
        userId,
        consumedAt: null,
      },

      data: {
        consumedAt: new Date(),
      },
    });

    const challenge = await this.prisma.twoFactorChallenge.create({
      data: {
        userId,

        expiresAt: new Date(Date.now() + this.challengeTtlMs),
      },
    });

    return {
      requiresTwoFactor: true,

      challengeId: challenge.id,

      expiresIn: this.challengeTtlMs / 1000,
    };
  }

  // ============================================================
  // Enrollment
  // ============================================================

  async enroll(userId: string): Promise<{
    secret: string;

    otpauthUri: string;

    expiresIn: number;
  }> {
    const user = await this.prisma.user.findUnique({
      where: {
        id: userId,
      },

      select: {
        email: true,

        twoFactorEnabled: true,
      },
    });

    if (!user) {
      throw new UnauthorizedException('User not found');
    }

    if (user.twoFactorEnabled) {
      throw new BadRequestException(
        'Two-factor authentication is already enabled',
      );
    }

    /*
     * otplib v13.
     *
     * Base32 secret compatible
     * with Google Authenticator,
     * Aegis, Microsoft Authenticator,
     * etc.
     */
    const secret = generateSecret();

    const issuer =
      this.config.get<string>('security.twoFactorIssuer') ?? 'Lumi';

    /*
     * otpauth:// URI,
     * який потім можна
     * перетворити на QR.
     */
    const otpauthUri = generateURI({
      issuer,

      label: user.email,

      secret,
    });

    /*
     * Secret у БД
     * зберігаємо зашифрованим.
     */
    const encryptedSecret = this.crypto.encrypt(secret);

    await this.prisma.twoFactorEnrollment.upsert({
      where: {
        userId,
      },

      create: {
        userId,

        encryptedSecret,

        expiresAt: new Date(Date.now() + this.enrollmentTtlMs),
      },

      update: {
        encryptedSecret,

        expiresAt: new Date(Date.now() + this.enrollmentTtlMs),
      },
    });

    return {
      secret,

      otpauthUri,

      expiresIn: this.enrollmentTtlMs / 1000,
    };
  }

  // ============================================================
  // Verify enrollment
  // ============================================================

  async verifyEnrollment(
    userId: string,
    code: string,
  ): Promise<{
    recoveryCodes: string[];
  }> {
    const enrollment = await this.prisma.twoFactorEnrollment.findUnique({
      where: {
        userId,
      },
    });

    if (!enrollment || enrollment.expiresAt <= new Date()) {
      throw new UnauthorizedException('2FA enrollment expired');
    }

    const secret = this.crypto.decrypt(enrollment.encryptedSecret);

    /*
     * otplib v13 verify()
     * повертає:
     *
     * {
     *   valid: boolean,
     *   ...
     * }
     */
    const verification = await verify({
      secret,

      token: code.trim(),

      epochTolerance: this.totpToleranceSeconds,
    });

    if (!verification.valid) {
      throw new UnauthorizedException('Invalid two-factor code');
    }

    /*
     * Recovery codes показуємо
     * користувачу тільки один раз.
     */
    const recoveryCodes = Array.from(
      {
        length: 10,
      },

      () => this.generateRecoveryCode(),
    );

    /*
     * У БД recovery code
     * у відкритому вигляді
     * не зберігаємо.
     */
    const hashes = recoveryCodes.map((value) =>
      this.crypto.hmac(this.normalizeRecoveryCode(value)),
    );

    await this.prisma.$transaction(async (tx) => {
      await tx.twoFactorRecoveryCode.deleteMany({
        where: {
          userId,
        },
      });

      await tx.twoFactorRecoveryCode.createMany({
        data: hashes.map((codeHash) => ({
          userId,
          codeHash,
        })),
      });

      await tx.user.update({
        where: {
          id: userId,
        },

        data: {
          twoFactorEnabled: true,

          twoFactorSecretEncrypted: enrollment.encryptedSecret,
        },
      });

      /*
       * Enrollment більше
       * не потрібен.
       */
      await tx.twoFactorEnrollment.delete({
        where: {
          userId,
        },
      });
    });

    await this.audit.log({
      type: SecurityAuditEventType.TWO_FACTOR_ENABLED,

      userId,
    });

    return {
      recoveryCodes,
    };
  }

  // ============================================================
  // Verify login challenge
  // ============================================================

  async verifyLoginChallenge(
    challengeId: string,
    code: string,
  ): Promise<string> {
    /*
     * Атомарно резервуємо
     * одну verification attempt.
     *
     * attempts < 5
     */
    const claim = await this.prisma.twoFactorChallenge.updateMany({
      where: {
        id: challengeId,

        consumedAt: null,

        expiresAt: {
          gt: new Date(),
        },

        attempts: {
          lt: this.maxAttempts,
        },
      },

      data: {
        attempts: {
          increment: 1,
        },
      },
    });

    if (claim.count !== 1) {
      throw new UnauthorizedException('2FA challenge expired or locked');
    }

    const challenge = await this.prisma.twoFactorChallenge.findUnique({
      where: {
        id: challengeId,
      },

      include: {
        user: true,
      },
    });

    if (
      !challenge ||
      challenge.user.isBlocked ||
      !challenge.user.twoFactorEnabled
    ) {
      throw new UnauthorizedException('Invalid two-factor challenge');
    }

    const verified = await this.verifyFactor(
      challenge.userId,

      challenge.user.twoFactorSecretEncrypted,

      code,
    );

    if (!verified.valid) {
      /*
       * Якщо використана
       * остання спроба,
       * остаточно закриваємо challenge.
       */
      if (challenge.attempts >= this.maxAttempts) {
        await this.prisma.twoFactorChallenge.update({
          where: {
            id: challenge.id,
          },

          data: {
            consumedAt: new Date(),
          },
        });
      }

      await this.audit.log({
        type: SecurityAuditEventType.TWO_FACTOR_FAILED,

        userId: challenge.userId,
      });

      throw new UnauthorizedException('Invalid two-factor code');
    }

    /*
     * Challenge single-use.
     */
    await this.prisma.twoFactorChallenge.update({
      where: {
        id: challenge.id,
      },

      data: {
        consumedAt: new Date(),
      },
    });

    if (verified.recovery) {
      await this.audit.log({
        type: SecurityAuditEventType.TWO_FACTOR_RECOVERY_USED,

        userId: challenge.userId,
      });
    }

    return challenge.userId;
  }

  // ============================================================
  // Disable 2FA
  // ============================================================

  async disable(
    userId: string,

    password: string | undefined,

    code: string,
  ): Promise<void> {
    const user = await this.prisma.user.findUnique({
      where: {
        id: userId,
      },
    });

    if (!user || !user.twoFactorEnabled) {
      throw new BadRequestException('Two-factor authentication is not enabled');
    }

    /*
     * Для local/password account
     * вимагаємо password.
     *
     * OAuth-only account
     * passwordHash не має.
     */
    if (user.passwordHash) {
      if (!password) {
        throw new UnauthorizedException('Password is required');
      }

      const passwordValid = await this.passwordService.verify(
        user.passwordHash,
        password,
      );

      if (!passwordValid) {
        throw new UnauthorizedException('Invalid credentials');
      }
    }

    /*
     * Крім password,
     * потрібно підтвердити
     * TOTP або recovery code.
     */
    const verified = await this.verifyFactor(
      userId,

      user.twoFactorSecretEncrypted,

      code,
    );

    if (!verified.valid) {
      throw new UnauthorizedException('Invalid two-factor code');
    }

    await this.prisma.$transaction(async (tx) => {
      await tx.user.update({
        where: {
          id: userId,
        },

        data: {
          twoFactorEnabled: false,

          twoFactorSecretEncrypted: null,
        },
      });

      await tx.twoFactorRecoveryCode.deleteMany({
        where: {
          userId,
        },
      });

      await tx.twoFactorChallenge.deleteMany({
        where: {
          userId,
        },
      });

      await tx.twoFactorEnrollment.deleteMany({
        where: {
          userId,
        },
      });

      /*
       * Disable 2FA —
       * security downgrade.
       *
       * Викидаємо користувача
       * з усіх sessions.
       */
      await tx.authSession.updateMany({
        where: {
          userId,

          revokedAt: null,
        },

        data: {
          revokedAt: new Date(),
        },
      });
    });

    await this.audit.log({
      type: SecurityAuditEventType.TWO_FACTOR_DISABLED,

      userId,
    });
  }

  // ============================================================
  // Verify TOTP OR recovery code
  // ============================================================

  private async verifyFactor(
    userId: string,

    encryptedSecret: string | null,

    code: string,
  ): Promise<{
    valid: boolean;

    recovery: boolean;
  }> {
    const normalized = code.trim();

    // ==========================================================
    // TOTP
    // ==========================================================

    if (/^\d{6}$/.test(normalized)) {
      if (!encryptedSecret) {
        return {
          valid: false,

          recovery: false,
        };
      }

      const secret = this.crypto.decrypt(encryptedSecret);

      const verification = await verify({
        secret,

        token: normalized,

        /*
         * ±30 sec.
         */
        epochTolerance: this.totpToleranceSeconds,
      });

      return {
        valid: verification.valid,

        recovery: false,
      };
    }

    // ==========================================================
    // Recovery code
    // ==========================================================

    const recoveryCode = this.normalizeRecoveryCode(normalized);

    const codeHash = this.crypto.hmac(recoveryCode);

    const record = await this.prisma.twoFactorRecoveryCode.findUnique({
      where: {
        codeHash,
      },
    });

    if (!record || record.userId !== userId || record.usedAt) {
      return {
        valid: false,

        recovery: false,
      };
    }

    /*
     * Atomic single-use.
     */
    const consumed = await this.prisma.twoFactorRecoveryCode.updateMany({
      where: {
        id: record.id,

        usedAt: null,
      },

      data: {
        usedAt: new Date(),
      },
    });

    const valid = consumed.count === 1;

    return {
      valid,

      recovery: valid,
    };
  }

  // ============================================================
  // Recovery code generation
  // ============================================================

  private generateRecoveryCode(): string {
    const raw = randomBytes(10).toString('hex').toUpperCase();

    const groups = raw.match(/.{1,4}/g);

    return `LUMI-${groups?.join('-') ?? raw}`;
  }

  // ============================================================
  // Recovery code normalization
  // ============================================================

  private normalizeRecoveryCode(code: string): string {
    return code.trim().toUpperCase().replace(/\s+/g, '');
  }
}
