import { Injectable, Logger, NestMiddleware } from '@nestjs/common';
import { NextFunction, Request, Response } from 'express';

import { MetricsService } from './metrics.service';
import { routeLabel } from './route-label.util';

@Injectable()
export class RequestMetricsMiddleware implements NestMiddleware {
  private readonly logger = new Logger('RequestMetrics');

  constructor(private readonly metricsService: MetricsService) {}

  use(req: Request, res: Response, next: NextFunction): void {
    const start = Date.now();
    const method = req.method;
    const route = routeLabel(req.originalUrl || req.url);

    let payloadBytes = 0;

    const originalWrite = res.write.bind(res) as typeof res.write;
    const originalEnd = res.end.bind(res) as typeof res.end;

    res.write = (chunk: any, ...rest: any[]) => {
      if (chunk) {
        payloadBytes += Buffer.byteLength(chunk);
      }
      return originalWrite(chunk, ...rest);
    };

    res.end = (chunk: any, ...rest: any[]) => {
      if (chunk) {
        payloadBytes += Buffer.byteLength(chunk);
      }
      return originalEnd(chunk, ...rest);
    };

    res.on('finish', () => {
      const latencyMs = Date.now() - start;

      const slow = this.metricsService.recordRequest(
        {
          method,
          route,
          status: res.statusCode,
        },
        latencyMs,
        payloadBytes,
      );

      if (slow) {
        this.logger.warn(`Slow request: ${method} ${route} ${latencyMs}ms`);
      }
    });

    next();
  }
}
