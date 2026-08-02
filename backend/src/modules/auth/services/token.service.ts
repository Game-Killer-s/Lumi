import { Injectable, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';

import {
  AccessTokenPayload,
  RefreshTokenPayload,
} from '../types/token-payload.type';

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  accessTokenExpiresIn: number;
  refreshTokenExpiresIn: number;
}

@Injectable()
export class TokenService {
  constructor(
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
  ) {}

  signAccessToken(payload: AccessTokenPayload): Promise<string> {
    return this.jwtService.signAsync(payload, {
      secret: this.accessSecret,
      expiresIn: this.accessTtlSeconds,
      issuer: this.issuer,
      audience: this.audience,
    });
  }

  signRefreshToken(payload: RefreshTokenPayload): Promise<string> {
    return this.jwtService.signAsync(payload, {
      secret: this.refreshSecret,
      expiresIn: this.refreshTtlSeconds,
      issuer: this.issuer,
      audience: this.audience,
    });
  }

  async signTokenPair(
      accessPayload: AccessTokenPayload,
      refreshPayload: RefreshTokenPayload,
    ): Promise<TokenPair> {
      const [accessToken, refreshToken] = await Promise.all([
        this.signAccessToken(accessPayload),
        this.signRefreshToken(refreshPayload),
      ]);

      return {
        accessToken,
        refreshToken,
        tokenType: 'Bearer',
        accessTokenExpiresIn: this.accessTtlSeconds,
        refreshTokenExpiresIn: this.refreshTtlSeconds,
      };
    }

  async verifyAccessToken(token: string): Promise<AccessTokenPayload> {
    try {
      const payload = await this.jwtService.verifyAsync<AccessTokenPayload>(
        token,
        {
          secret: this.accessSecret,
          issuer: this.issuer,
          audience: this.audience,
        },
      );

      if (payload.type !== 'access') {
        throw new UnauthorizedException();
      }

      return payload;
    } catch {
      throw new UnauthorizedException('Access token is invalid or expired');
    }
  }

  async verifyRefreshToken(token: string): Promise<RefreshTokenPayload> {
    try {
      const payload = await this.jwtService.verifyAsync<RefreshTokenPayload>(
        token,
        {
          secret: this.refreshSecret,
          issuer: this.issuer,
          audience: this.audience,
        },
      );

      if (payload.type !== 'refresh') {
        throw new UnauthorizedException();
      }

      return payload;
    } catch {
      throw new UnauthorizedException('Refresh token is invalid or expired');
    }
  }

  private get accessSecret(): string {
    return this.configService.getOrThrow<string>('jwt.accessSecret');
  }

  private get refreshSecret(): string {
    return this.configService.getOrThrow<string>('jwt.refreshSecret');
  }

  private get accessTtlSeconds(): number {
    return this.configService.getOrThrow<number>('jwt.accessTtlSeconds');
  }

  private get refreshTtlSeconds(): number {
    return this.configService.getOrThrow<number>('jwt.refreshTtlSeconds');
  }

  private get issuer(): string {
    return this.configService.getOrThrow<string>('jwt.issuer');
  }

  private get audience(): string {
    return this.configService.getOrThrow<string>('jwt.audience');
  }
}
