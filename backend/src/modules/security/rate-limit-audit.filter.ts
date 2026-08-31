import { ArgumentsHost, Catch, ExceptionFilter } from '@nestjs/common';

import { ThrottlerException } from '@nestjs/throttler';

import { Request, Response } from 'express';

import { SecurityAuditEventType } from '@prisma/client';

import { SecurityAuditService } from './security-audit.service';

@Catch(ThrottlerException)
export class RateLimitAuditFilter implements ExceptionFilter {
  constructor(private readonly audit: SecurityAuditService) {}

  catch(_exception: ThrottlerException, host: ArgumentsHost): void {
    const context = host.switchToHttp();

    const request = context.getRequest<
      Request & {
        user?: { sub?: string };
      }
    >();

    const response = context.getResponse<Response>();

    void this.audit.log({
      type: SecurityAuditEventType.RATE_LIMITED,

      userId: request.user?.sub,

      route: request.originalUrl,

      ip: request.ip,

      userAgent: request.headers['user-agent'],
    });

    response.status(429).json({
      statusCode: 429,
      error: 'Too Many Requests',
      message: 'Too many requests. Try again later.',
    });
  }
}
