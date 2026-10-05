import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

import { redact, safeJson } from './redact.util';

export interface RequestLabels {
  method: string;
  route: string;
  status: number;
}

export interface CacheStat {
  namespace: string;
  hits: number;
  misses: number;
  hitRate: number;
}

interface RequestStat {
  count: number;
  totalLatencyMs: number;
  totalBytes: number;
  slowCount: number;
}

interface PrismaStat {
  count: number;
  totalMs: number;
  slowCount: number;
}

@Injectable()
export class MetricsService {
  private readonly logger = new Logger('Metrics');

  private readonly requests = new Map<string, RequestStat>();
  private readonly prismaQueries = new Map<string, PrismaStat>();

  private readonly slowRequestThresholdMs: number;
  private readonly slowQueryThresholdMs: number;

  constructor(configService: ConfigService) {
    this.slowRequestThresholdMs =
      configService.get<number>('slowRequestThresholdMs') ?? 500;
    this.slowQueryThresholdMs =
      configService.get<number>('slowQueryThresholdMs') ?? 100;
  }

  recordRequest(
    labels: RequestLabels,
    latencyMs: number,
    payloadBytes: number,
  ): boolean {
    const key = `${labels.method} ${labels.route} ${labels.status}`;

    const stat = this.requests.get(key) ?? {
      count: 0,
      totalLatencyMs: 0,
      totalBytes: 0,
      slowCount: 0,
    };

    stat.count += 1;
    stat.totalLatencyMs += latencyMs;
    stat.totalBytes += payloadBytes;

    const slow = latencyMs > this.slowRequestThresholdMs;

    if (slow) {
      stat.slowCount += 1;
    }

    this.requests.set(key, stat);

    return slow;
  }

  recordPrismaQuery(
    model: string,
    operation: string,
    durationMs: number,
    args?: unknown,
  ): boolean {
    const key = `${model}.${operation}`;

    const stat = this.prismaQueries.get(key) ?? {
      count: 0,
      totalMs: 0,
      slowCount: 0,
    };

    stat.count += 1;
    stat.totalMs += durationMs;

    const slow = durationMs > this.slowQueryThresholdMs;

    if (slow) {
      stat.slowCount += 1;
    }

    this.prismaQueries.set(key, stat);

    if (slow) {
      this.logger.warn(
        `Slow query ${model}.${operation} ${durationMs}ms args=${safeJson(redact(args))}`,
      );
    }

    return slow;
  }

  reset(): void {
    this.requests.clear();
    this.prismaQueries.clear();
  }

  snapshot(cacheStats: CacheStat[]): Record<string, unknown> {
    const requests = Array.from(this.requests.entries()).map(([key, stat]) => {
      const [method, route, status] = key.split(' ');

      return {
        method,
        route,
        status: Number(status),
        count: stat.count,
        avgLatencyMs: Math.round(stat.totalLatencyMs / stat.count),
        avgPayloadBytes: Math.round(stat.totalBytes / stat.count),
        slowCount: stat.slowCount,
      };
    });

    const prismaQueries = Array.from(this.prismaQueries.entries()).map(
      ([key, stat]) => {
        const [model, operation] = key.split('.');

        return {
          model,
          operation,
          count: stat.count,
          avgMs: Math.round(stat.totalMs / stat.count),
          slowCount: stat.slowCount,
        };
      },
    );

    const totalRequests = requests.reduce((sum, r) => sum + r.count, 0);
    const slowRequests = requests.reduce((sum, r) => sum + r.slowCount, 0);
    const totalLatencyMs = requests.reduce(
      (sum, r) => sum + r.avgLatencyMs * r.count,
      0,
    );

    const cacheHits = cacheStats.reduce((sum, c) => sum + c.hits, 0);
    const cacheMisses = cacheStats.reduce((sum, c) => sum + c.misses, 0);
    const cacheTotal = cacheHits + cacheMisses;

    return {
      uptimeSeconds: Math.round(process.uptime()),
      slowRequestThresholdMs: this.slowRequestThresholdMs,
      slowQueryThresholdMs: this.slowQueryThresholdMs,
      requests,
      prismaQueries,
      cache: cacheStats,
      totals: {
        totalRequests,
        slowRequests,
        avgLatencyMs:
          totalRequests === 0 ? 0 : Math.round(totalLatencyMs / totalRequests),
        cacheHits,
        cacheMisses,
        cacheHitRate:
          cacheTotal === 0
            ? 0
            : Math.round((cacheHits / cacheTotal) * 100) / 100,
      },
    };
  }
}
