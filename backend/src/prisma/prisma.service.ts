import { Injectable, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { PrismaClient } from '@prisma/client';

// PrismaService - це обгортка над PrismaClient.
// Вона підключається до бази даних при старті застосунку
// і закриває з'єднання при зупинці.
@Injectable()
export class PrismaService
  extends PrismaClient
  implements OnModuleInit, OnModuleDestroy
{
  async onModuleInit() {
    // Підключаємось до PostgreSQL при запуску
    await this.$connect();
  }

  async onModuleDestroy() {
    // Закриваємо з'єднання при зупинці застосунку
    await this.$disconnect();
  }
}
