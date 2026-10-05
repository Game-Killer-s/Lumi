import {
  CanActivate,
  ExecutionContext,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';

import { Reflector } from '@nestjs/core';
import { Request } from 'express';

import { PrismaService } from '../../../prisma/prisma.service';

import { IS_PUBLIC_KEY } from '../decorators/public.decorator';
import { TokenService } from '../services/token.service';

import { AuthenticatedRequest } from '../types/authenticated-request.type';

@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    private readonly tokenService: TokenService,
    private readonly prisma: PrismaService,
  ) {}

  async canActivate(
    context: ExecutionContext,
  ): Promise<boolean> {
    const isPublic =
      this.reflector.getAllAndOverride<boolean>(
        IS_PUBLIC_KEY,
        [
          context.getHandler(),
          context.getClass(),
        ],
      );

    if (isPublic) {
      return true;
    }

    const request =
      context
        .switchToHttp()
        .getRequest<AuthenticatedRequest>();

    const token =
      this.extractBearerToken(request);

    if (!token) {
      throw new UnauthorizedException(
        'Bearer token is required',
      );
    }

    const payload =
      await this.tokenService.verifyAccessToken(
        token,
      );

    const session =
      await this.prisma.client.authSession.findUnique({
        where: {
          id: payload.sessionId,
        },
        select: {
          userId: true,
          revokedAt: true,
          expiresAt: true,

          user: {
            select: {
              isBlocked: true,
              role: true,
            },
          },
        },
      });

    if (
      !session ||
      session.userId !== payload.sub ||
      session.revokedAt ||
      session.expiresAt <= new Date() ||
      session.user.isBlocked
    ) {
      throw new UnauthorizedException(
        'Session is invalid or revoked',
      );
    }

    /*
     * Беремо актуальну роль із БД.
     *
     * Якщо адміністратор змінив роль користувача,
     * чекати завершення access JWT не потрібно.
     */
    request.user = {
      ...payload,
      role: session.user.role,
    };

    return true;
  }

  private extractBearerToken(
    request: Request,
  ): string | undefined {
    const authorization =
      request.headers.authorization;

    if (!authorization) {
      return undefined;
    }

    const parts =
      authorization
        .trim()
        .split(/\s+/);

    if (parts.length !== 2) {
      return undefined;
    }

    const [scheme, token] = parts;

    if (
      scheme.toLowerCase() !== 'bearer' ||
      !token
    ) {
      return undefined;
    }

    return token;
  }
}