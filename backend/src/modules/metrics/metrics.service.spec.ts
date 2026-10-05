import { ConfigService } from '@nestjs/config';
import { Test, TestingModule } from '@nestjs/testing';

import { MetricsService } from './metrics.service';
import { redact, safeJson } from './redact.util';

describe('MetricsService', () => {
  let metrics: MetricsService;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        MetricsService,
        {
          provide: ConfigService,
          useValue: {
            get: jest.fn((key: string) => {
              if (key === 'slowRequestThresholdMs') return 100;
              if (key === 'slowQueryThresholdMs') return 50;
              return undefined;
            }),
          },
        },
      ],
    }).compile();

    metrics = module.get(MetricsService);
    metrics.reset();
  });

  it('aggregates requests by method/route/status label', () => {
    metrics.recordRequest(
      { method: 'GET', route: '/api/health', status: 200 },
      10,
      100,
    );
    metrics.recordRequest(
      { method: 'GET', route: '/api/health', status: 200 },
      30,
      200,
    );

    const snapshot = metrics.snapshot([]) as {
      requests: Array<{
        method: string;
        route: string;
        status: number;
        count: number;
        avgLatencyMs: number;
        avgPayloadBytes: number;
        slowCount: number;
      }>;
    };

    expect(snapshot.requests).toHaveLength(1);
    expect(snapshot.requests[0]).toEqual({
      method: 'GET',
      route: '/api/health',
      status: 200,
      count: 2,
      avgLatencyMs: 20,
      avgPayloadBytes: 150,
      slowCount: 0,
    });
  });

  it('keeps different labels separate', () => {
    metrics.recordRequest(
      { method: 'GET', route: '/api/health', status: 200 },
      10,
      50,
    );
    metrics.recordRequest(
      { method: 'GET', route: '/api/health', status: 500 },
      10,
      50,
    );

    const snapshot = metrics.snapshot([]) as {
      requests: Array<{ route: string; status: number }>;
    };

    expect(snapshot.requests).toHaveLength(2);
  });

  it('marks slow requests and counts them', () => {
    const slow = metrics.recordRequest(
      { method: 'GET', route: '/api/search', status: 200 },
      150,
      50,
    );

    const snapshot = metrics.snapshot([]) as {
      requests: Array<{ slowCount: number }>;
      totals: { slowRequests: number };
    };

    expect(slow).toBe(true);
    expect(snapshot.requests[0].slowCount).toBe(1);
    expect(snapshot.totals.slowRequests).toBe(1);
  });

  it('records prisma queries with model and operation labels', () => {
    metrics.recordPrismaQuery('User', 'findUnique', 10);
    metrics.recordPrismaQuery('User', 'findUnique', 20);

    const snapshot = metrics.snapshot([]) as {
      prismaQueries: Array<{
        model: string;
        operation: string;
        count: number;
        avgMs: number;
        slowCount: number;
      }>;
    };

    expect(snapshot.prismaQueries).toHaveLength(1);
    expect(snapshot.prismaQueries[0]).toEqual({
      model: 'User',
      operation: 'findUnique',
      count: 2,
      avgMs: 15,
      slowCount: 0,
    });
  });

  it('marks slow prisma queries', () => {
    const slow = metrics.recordPrismaQuery('User', 'findMany', 60, {
      where: { password: 'x' },
    });

    expect(slow).toBe(true);
  });

  it('computes cache totals and hit rate from cache stats', () => {
    const snapshot = metrics.snapshot([
      { namespace: 'discovery', hits: 8, misses: 2, hitRate: 0.8 },
      { namespace: 'track', hits: 0, misses: 4, hitRate: 0 },
    ]) as {
      totals: {
        cacheHits: number;
        cacheMisses: number;
        cacheHitRate: number;
      };
    };

    expect(snapshot.totals.cacheHits).toBe(8);
    expect(snapshot.totals.cacheMisses).toBe(6);
    expect(snapshot.totals.cacheHitRate).toBe(0.57);
  });
});

describe('redact', () => {
  it('replaces sensitive values but keeps the rest', () => {
    const input = {
      password: 'secret',
      name: 'Luna',
      where: { token: 'abc', artistId: 'a1' },
    };

    expect(redact(input)).toEqual({
      password: '[REDACTED]',
      name: 'Luna',
      where: { token: '[REDACTED]', artistId: 'a1' },
    });
  });

  it('redacts keys inside arrays', () => {
    expect(redact([{ passwordHash: 'x' }, { title: 't' }])).toEqual([
      { passwordHash: '[REDACTED]' },
      { title: 't' },
    ]);
  });

  it('safeJson does not throw on unserializable values', () => {
    expect(safeJson({ big: BigInt(1) })).toBe('[unserializable]');
  });
});
