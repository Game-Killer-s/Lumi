import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

export type CacheNamespace =
  | 'track'
  | 'artist'
  | 'album'
  | 'catalog'
  | 'discovery'
  | 'recommendation';

export interface CacheNamespaceStat {
  namespace: CacheNamespace;
  hits: number;
  misses: number;
  hitRate: number;
}

interface CacheEntry {
  value: unknown;
  version: number;
  expiresAt: number;
}

interface NamespaceState {
  version: number;
  entries: Map<string, CacheEntry>;
  tombstones: Set<string>;
}

interface NamespaceStats {
  hits: number;
  misses: number;
}

export const DEFAULT_STALE_WINDOW_MS = 60_000;

@Injectable()
export class CacheService {
  private readonly namespaces = new Map<CacheNamespace, NamespaceState>();
  private readonly stats = new Map<CacheNamespace, NamespaceStats>();
  private readonly staleWindowMs: number;

  constructor(configService: ConfigService) {
    this.staleWindowMs =
      configService.get<number>('cache.staleWindowMs') ??
      DEFAULT_STALE_WINDOW_MS;
  }

  private state(namespace: CacheNamespace): NamespaceState {
    let ns = this.namespaces.get(namespace);

    if (!ns) {
      ns = { version: 0, entries: new Map(), tombstones: new Set() };
      this.namespaces.set(namespace, ns);
    }

    return ns;
  }

  private stat(namespace: CacheNamespace): NamespaceStats {
    let s = this.stats.get(namespace);

    if (!s) {
      s = { hits: 0, misses: 0 };
      this.stats.set(namespace, s);
    }

    return s;
  }

  get<T>(namespace: CacheNamespace, key: string): T | null {
    const ns = this.state(namespace);
    const stats = this.stat(namespace);

    if (ns.tombstones.has(key)) {
      stats.misses += 1;
      return null;
    }

    const entry = ns.entries.get(key);

    if (!entry || entry.version !== ns.version) {
      stats.misses += 1;
      return null;
    }

    if (entry.expiresAt <= Date.now()) {
      ns.entries.delete(key);
      stats.misses += 1;
      return null;
    }

    stats.hits += 1;

    return entry.value as T;
  }

  set<T>(
    namespace: CacheNamespace,
    key: string,
    value: T,
    ttlMs?: number,
  ): boolean {
    const ns = this.state(namespace);

    if (ns.tombstones.has(key)) {
      return false;
    }

    ns.entries.set(key, {
      value,
      version: ns.version,
      expiresAt: Date.now() + (ttlMs ?? this.staleWindowMs),
    });

    return true;
  }

  setIfVersion<T>(
    namespace: CacheNamespace,
    key: string,
    value: T,
    expectedVersion: number,
    ttlMs?: number,
  ): boolean {
    const ns = this.state(namespace);

    if (ns.version !== expectedVersion || ns.tombstones.has(key)) {
      return false;
    }

    ns.entries.set(key, {
      value,
      version: expectedVersion,
      expiresAt: Date.now() + (ttlMs ?? this.staleWindowMs),
    });

    return true;
  }

  getVersion(namespace: CacheNamespace): number {
    return this.state(namespace).version;
  }

  getStats(): CacheNamespaceStat[] {
    return Array.from(this.stats.entries()).map(([namespace, s]) => {
      const total = s.hits + s.misses;

      return {
        namespace,
        hits: s.hits,
        misses: s.misses,
        hitRate: total === 0 ? 0 : Math.round((s.hits / total) * 100) / 100,
      };
    });
  }

  invalidate(namespace: CacheNamespace): void {
    const ns = this.state(namespace);

    ns.version += 1;
    ns.tombstones.clear();
  }

  invalidateKey(namespace: CacheNamespace, key: string): void {
    const ns = this.state(namespace);

    ns.entries.delete(key);
    ns.tombstones.add(key);
  }

  async getOrLoad<T>(
    namespace: CacheNamespace,
    key: string,
    loader: () => Promise<T>,
    ttlMs?: number,
  ): Promise<T> {
    const cached = this.get<T>(namespace, key);

    if (cached !== null) {
      return cached;
    }

    const version = this.getVersion(namespace);
    const value = await loader();

    this.setIfVersion(namespace, key, value, version, ttlMs);

    return value;
  }
}
