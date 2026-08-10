import { Injectable } from '@nestjs/common';

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

  hashRefreshToken(refreshToken: string): Promise<string> {
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
}