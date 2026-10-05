import { Injectable, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { PrismaClient } from '@prisma/client';

import { MetricsService } from '../modules/metrics/metrics.service';

@Injectable()
export class PrismaService implements OnModuleInit, OnModuleDestroy {
  readonly client: PrismaClient;

  constructor(private readonly metricsService: MetricsService) {
    this.client = new PrismaClient().$extends({
      query: {
        $allModels: {
          async $allOperations({ model, operation, args, query }) {
            const start = Date.now();
            const result = await query(args);
            const durationMs = Date.now() - start;

            metricsService.recordPrismaQuery(
              model,
              operation,
              durationMs,
              args,
            );

            return result;
          },
        },
      },
    }) as unknown as PrismaClient;
  }

  // Прямий доступ до моделей (this.prisma.track, this.prisma.user, ...).
  // Цей стиль використовує код, що прийшов з гілки main (модуль content та
  // публічна частина каталогу), тому делегуємо виклики до metrics-обгортки
  // this.client — щоб метрики Prisma працювали для всіх модулів однаково.
  get user() {
    return this.client.user;
  }

  get artist() {
    return this.client.artist;
  }

  get album() {
    return this.client.album;
  }

  get genre() {
    return this.client.genre;
  }

  get track() {
    return this.client.track;
  }

  get trackAuditLog() {
    return this.client.trackAuditLog;
  }

  get authSession() {
    return this.client.authSession;
  }

  async onModuleInit() {
    await this.client.$connect();
  }

  async onModuleDestroy() {
    await this.client.$disconnect();
  }
}
