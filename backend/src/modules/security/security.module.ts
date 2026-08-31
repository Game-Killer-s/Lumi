import { Global, Module } from '@nestjs/common';

import { APP_FILTER } from '@nestjs/core';

import { ThrottlerModule } from '@nestjs/throttler';

import { SecurityCryptoService } from './security-crypto.service';

import { SecurityAuditService } from './security-audit.service';

import { SecurityThrottlerGuard } from './security-throttler.guard';

import { RateLimitAuditFilter } from './rate-limit-audit.filter';

@Global()
@Module({
  imports: [
    ThrottlerModule.forRoot([
      {
        name: 'default',

        // Загальний safety net.
        ttl: 60_000,
        limit: 300,
      },
    ]),
  ],

  providers: [
    SecurityCryptoService,
    SecurityAuditService,
    SecurityThrottlerGuard,

    {
      provide: APP_FILTER,
      useClass: RateLimitAuditFilter,
    },
  ],

  exports: [
    SecurityCryptoService,
    SecurityAuditService,
    SecurityThrottlerGuard,
  ],
})
export class SecurityModule {}
