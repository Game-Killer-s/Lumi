import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { APP_GUARD } from '@nestjs/core';

import configuration from './config/configuration';
import { validationSchema } from './config/validation.schema';

import { HealthModule } from './modules/health/health.module';
import { AuthModule } from './modules/auth/auth.module';
import { SecurityModule } from './modules/security/security.module';

import { JwtAuthGuard } from './modules/auth/guards/jwt-auth.guard';
import { SecurityThrottlerGuard } from './modules/security/security-throttler.guard';

import { PrismaModule } from './prisma/prisma.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      load: [configuration],
      validationSchema,
      envFilePath: [`.env.${process.env.NODE_ENV || 'development'}`, '.env'],
    }),

    PrismaModule,

    SecurityModule,
    AuthModule,
    HealthModule,
  ],

  providers: [
    /*
     * ПОРЯДОК ВАЖЛИВИЙ:
     *
     * 1. JWT -> request.user
     * 2. Rate limit -> user/IP key
     */
    {
      provide: APP_GUARD,
      useExisting: JwtAuthGuard,
    },

    {
      provide: APP_GUARD,
      useExisting: SecurityThrottlerGuard,
    },
  ],
})
export class AppModule {}
