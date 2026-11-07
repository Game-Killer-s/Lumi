import {
  CanActivate,
  ExecutionContext,
  HttpException,
  HttpStatus,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';

import { AuthenticatedRequest } from '../../auth/types/authenticated-request.type';
import { PresenceRateLimiter } from '../presence-rate-limiter';

@Injectable()
export class PresenceRateLimitGuard implements CanActivate {
  constructor(private readonly limiter: PresenceRateLimiter) {}

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<AuthenticatedRequest>();

    if (!request.user) {
      throw new UnauthorizedException();
    }

    if (!this.limiter.consume(request.user.sub)) {
      throw new HttpException(
        'Too many current listening updates',
        HttpStatus.TOO_MANY_REQUESTS,
      );
    }

    return true;
  }
}
