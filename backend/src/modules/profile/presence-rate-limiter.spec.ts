import { ConfigService } from '@nestjs/config';

import { PresenceRateLimiter } from './presence-rate-limiter';

describe('PresenceRateLimiter', () => {
  const config = {
    get: jest.fn((key: string) =>
      key === 'presence.rateLimitMax'
        ? 2
        : key === 'presence.rateLimitWindowMs'
          ? 1000
          : undefined,
    ),
  } as unknown as ConfigService;

  let limiter: PresenceRateLimiter;

  beforeEach(() => {
    limiter = new PresenceRateLimiter(config);
  });

  it('allows updates up to the limit and rejects the next one', () => {
    expect(limiter.consume('u1', 1000)).toBe(true);
    expect(limiter.consume('u1', 1100)).toBe(true);
    expect(limiter.consume('u1', 1200)).toBe(false);
  });

  it('frees the window after it expires', () => {
    expect(limiter.consume('u1', 1000)).toBe(true);
    expect(limiter.consume('u1', 1100)).toBe(true);
    expect(limiter.consume('u1', 1200)).toBe(false);
    expect(limiter.consume('u1', 2101)).toBe(true);
  });

  it('keeps separate buckets per user', () => {
    expect(limiter.consume('u1', 1000)).toBe(true);
    expect(limiter.consume('u1', 1000)).toBe(true);
    expect(limiter.consume('u1', 1000)).toBe(false);
    expect(limiter.consume('u2', 1000)).toBe(true);
  });

  it('can reset one user or the whole limiter', () => {
    expect(limiter.consume('u1', 1000)).toBe(true);
    expect(limiter.consume('u1', 1000)).toBe(true);
    expect(limiter.consume('u1', 1000)).toBe(false);

    limiter.reset('u1');

    expect(limiter.consume('u1', 1000)).toBe(true);
  });
});
