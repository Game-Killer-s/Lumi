import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

// In-memory sliding window. Для одного інстансу API цього достатньо;
// у multi-instance конфігурації ліміт слід винести в Redis.
@Injectable()
export class PresenceRateLimiter {
  private readonly buckets = new Map<string, number[]>();

  constructor(private readonly configService: ConfigService) {}

  consume(userId: string, now: number = Date.now()): boolean {
    const cutoff = now - this.windowMs;
    const hits = (this.buckets.get(userId) ?? []).filter(
      (timestamp) => timestamp > cutoff,
    );

    if (hits.length >= this.max) {
      this.buckets.set(userId, hits);

      return false;
    }

    hits.push(now);
    this.buckets.set(userId, hits);

    return true;
  }

  reset(userId?: string): void {
    if (userId) {
      this.buckets.delete(userId);

      return;
    }

    this.buckets.clear();
  }

  private get max(): number {
    return this.configService.get<number>('presence.rateLimitMax') ?? 30;
  }

  private get windowMs(): number {
    return (
      this.configService.get<number>('presence.rateLimitWindowMs') ?? 60_000
    );
  }
}
