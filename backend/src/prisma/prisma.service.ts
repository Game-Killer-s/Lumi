import { Injectable, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { PrismaClient, Prisma } from '@prisma/client';

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

  get like() {
    return this.client.like;
  }

  get comment() {
    return this.client.comment;
  }

  get authSession() {
    return this.client.authSession;
  }

  get currentListening() {
    return this.client.currentListening;
  }

  get mediaAsset() {
    return this.client.mediaAsset;
  }

  get mediaProcessingJob() {
    return this.client.mediaProcessingJob;
  }

  get mediaVariant() {
    return this.client.mediaVariant;
  }

  // Атомарне завантаження: asset + job + оновлення треку в одній транзакції.
  transaction<T>(
    callback: (tx: Prisma.TransactionClient) => Promise<T>,
  ): Promise<T> {
    return this.client.$transaction(callback);
  }

  async onModuleInit() {
    await this.client.$connect();
  }

  async onModuleDestroy() {
    await this.client.$disconnect();
  }
}
