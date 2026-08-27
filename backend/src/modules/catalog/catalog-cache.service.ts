import { Injectable } from '@nestjs/common';

/**
 * Простий in-memory cache для каталогу.
 *
 * Коли змінюється статус треку (lifecycle change, block/unblock)
 * або його метадані - ContentLifecycleService та AdminTracksService
 * викликають invalidateCatalog(), щоб слухачі одразу бачили
 * актуальні дані, а не застарілу копію з кешу.
 */
@Injectable()
export class CatalogCacheService {
  private readonly cache = new Map<string, unknown>();

  get<T>(key: string): T | undefined {
    return this.cache.get(key) as T | undefined;
  }

  set<T>(key: string, value: T): void {
    this.cache.set(key, value);
  }

  invalidateCatalog(): void {
    this.cache.clear();
  }
}
