import { Injectable } from '@nestjs/common';

import { ThrottlerGuard } from '@nestjs/throttler';

interface RateLimitRequest {
  ip?: string;

  socket?: {
    remoteAddress?: string;
  };

  user?: {
    sub?: string;
  };
}

@Injectable()
export class SecurityThrottlerGuard extends ThrottlerGuard {
  protected getTracker(req: RateLimitRequest): Promise<string> {
    /*
     * Якщо JwtAuthGuard вже
     * автентифікував користувача,
     * rate limit прив'язуємо до userId.
     *
     * Це означає:
     *
     * user:UUID
     */
    if (req.user?.sub) {
      return Promise.resolve(`user:${req.user.sub}`);
    }

    /*
     * Для public endpoints:
     *
     * login
     * register
     * recovery
     * OAuth start
     *
     * використовуємо IP.
     */
    const ip = req.ip ?? req.socket?.remoteAddress ?? 'unknown';

    return Promise.resolve(`ip:${ip}`);
  }
}
