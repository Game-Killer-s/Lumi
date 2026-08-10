import { Module } from '@nestjs/common';
import { JwtModule } from '@nestjs/jwt';
import { APP_GUARD } from '@nestjs/core';

import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';

import { JwtAuthGuard } from './guards/jwt-auth.guard';
import { RolesGuard } from './guards/roles.guard';

import { PasswordService } from './services/password.service';
import { TokenService } from './services/token.service';

@Module({
  imports: [
    JwtModule.register({}),
  ],

  controllers: [
    AuthController,
  ],

  providers: [
    AuthService,
    PasswordService,
    TokenService,
    JwtAuthGuard,
    RolesGuard,

    {
      provide: APP_GUARD,
      useExisting: JwtAuthGuard,
    },
  ],

  exports: [
    AuthService,
    PasswordService,
    TokenService,
    RolesGuard,
  ],
})
export class AuthModule {}