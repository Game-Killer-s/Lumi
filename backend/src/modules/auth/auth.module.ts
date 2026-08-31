import { Module } from '@nestjs/common';
import { JwtModule } from '@nestjs/jwt';

import { AuthController } from './auth.controller';
import { OAuthController } from './oauth.controller';
import { TwoFactorController } from './two-factor.controller';
import { SessionsController } from './sessions.controller';
import { AuthService } from './auth.service';

import { JwtAuthGuard } from './guards/jwt-auth.guard';
import { RolesGuard } from './guards/roles.guard';

import { PasswordService } from './services/password.service';
import { TokenService } from './services/token.service';
import { TwoFactorService } from './services/two-factor.service';
import { OAuthService } from './services/oauth.service';
import { GoogleOAuthStrategy } from './oauth/google-oauth.strategy';
import { OAuthProviderRegistry } from './oauth/oauth-provider.registry';

@Module({
  imports: [JwtModule.register({})],

  controllers: [
    AuthController,
    OAuthController,
    TwoFactorController,
    SessionsController,
  ],

  providers: [
    AuthService,

    PasswordService,
    TokenService,

    TwoFactorService,
    OAuthService,

    GoogleOAuthStrategy,
    OAuthProviderRegistry,

    JwtAuthGuard,
    RolesGuard,
  ],

  exports: [
    AuthService,
    PasswordService,
    TokenService,

    RolesGuard,
    JwtAuthGuard,
  ],
})
export class AuthModule {}
