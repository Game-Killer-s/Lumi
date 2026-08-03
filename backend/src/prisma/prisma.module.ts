import { Global, Module } from '@nestjs/common';
import { PrismaService } from './prisma.service';

// Global - означає, що PrismaService доступний у всіх модулях
// без необхідності імпортувати цей модуль кожного разу.
@Global()
@Module({
  providers: [PrismaService],
  exports: [PrismaService],
})
export class PrismaModule {}
