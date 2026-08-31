import { Injectable, Logger } from '@nestjs/common';

import { Prisma, SecurityAuditEventType } from '@prisma/client';

import { PrismaService } from '../../prisma/prisma.service';
import { SecurityCryptoService } from './security-crypto.service';

interface SecurityAuditInput {
  type: SecurityAuditEventType;

  userId?: string;
  route?: string;

  ip?: string;
  userAgent?: string;

  metadata?: Prisma.InputJsonValue;
}

@Injectable()
export class SecurityAuditService {
  private readonly logger = new Logger(SecurityAuditService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly crypto: SecurityCryptoService,
  ) {}

  async log(input: SecurityAuditInput): Promise<void> {
    try {
      await this.prisma.securityAuditEvent.create({
        data: {
          type: input.type,

          userId: input.userId,
          route: input.route,

          ipHash: input.ip ? this.crypto.hmac(input.ip) : undefined,

          userAgentHash: input.userAgent
            ? this.crypto.hmac(input.userAgent)
            : undefined,

          metadata: input.metadata,
        },
      });
    } catch (error) {
      // Audit logging не повинен покласти API.
      this.logger.error('Failed to write security audit event', error);
    }
  }
}
