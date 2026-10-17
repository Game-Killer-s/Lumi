import { ConfigService } from '@nestjs/config';
import { Test, TestingModule } from '@nestjs/testing';

import { CacheService } from './cache.service';

describe('CacheService', () => {
  let cache: CacheService;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        CacheService,
        {
          provide: ConfigService,
          useValue: { get: jest.fn() },
        },
      ],
    }).compile();

    cache = module.get(CacheService);
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('returns null when nothing is cached', () => {
    expect(cache.get('track', 'missing')).toBeNull();
  });

  it('stores and returns a value', () => {
    cache.set('track', 't1', { title: 'Midnight Drive' });

    expect(cache.get('track', 't1')).toEqual({ title: 'Midnight Drive' });
  });

  it('expires entries after the stale window', () => {
    jest.useFakeTimers();

    cache.set('track', 't1', { title: 'Old' }, 1000);
    jest.advanceTimersByTime(1001);

    expect(cache.get('track', 't1')).toBeNull();
  });

  it('keeps separate namespaces isolated', () => {
    cache.set('track', 't1', { title: 'Track' });
    cache.set('album', 't1', { title: 'Album' });

    expect(cache.get('album', 't1')).toEqual({ title: 'Album' });
    expect(cache.get('track', 't1')).toEqual({ title: 'Track' });
  });

  it('invalidates the whole namespace by bumping its version', () => {
    cache.set('discovery', 'home', [{ id: 't1' }]);

    cache.invalidate('discovery');

    expect(cache.get('discovery', 'home')).toBeNull();

    cache.set('discovery', 'home', [{ id: 't2' }]);

    expect(cache.get('discovery', 'home')).toEqual([{ id: 't2' }]);
  });

  it('does not affect other namespaces when one is invalidated', () => {
    cache.set('track', 't1', { title: 'Track' });

    cache.invalidate('discovery');

    expect(cache.get('track', 't1')).toEqual({ title: 'Track' });
  });

  it('invalidates a single key without touching the namespace', () => {
    cache.set('track', 't1', { title: 'A' });
    cache.set('track', 't2', { title: 'B' });

    cache.invalidateKey('track', 't1');

    expect(cache.get('track', 't1')).toBeNull();
    expect(cache.get('track', 't2')).toEqual({ title: 'B' });
  });

  it('does not repopulate a tombstoned key', () => {
    cache.invalidateKey('track', 't1');
    cache.set('track', 't1', { title: 'Stale' });

    expect(cache.get('track', 't1')).toBeNull();
  });

  it('setIfVersion skips the write when the version changed', () => {
    const version = cache.getVersion('discovery');

    cache.invalidate('discovery');

    const written = cache.setIfVersion(
      'discovery',
      'home',
      [{ id: 'old' }],
      version,
    );

    expect(written).toBe(false);
    expect(cache.get('discovery', 'home')).toBeNull();
  });

  it('setIfVersion writes when the version is unchanged', () => {
    const version = cache.getVersion('discovery');

    const written = cache.setIfVersion(
      'discovery',
      'home',
      [{ id: 'new' }],
      version,
    );

    expect(written).toBe(true);
    expect(cache.get('discovery', 'home')).toEqual([{ id: 'new' }]);
  });

  it('getOrLoad returns cached value without calling the loader twice', async () => {
    const loader = jest.fn().mockResolvedValue([{ id: 't1' }]);

    const first = await cache.getOrLoad('discovery', 'home', loader);
    const second = await cache.getOrLoad('discovery', 'home', loader);

    expect(first).toEqual([{ id: 't1' }]);
    expect(second).toEqual([{ id: 't1' }]);
    expect(loader).toHaveBeenCalledTimes(1);
  });

  it('getOrLoad does not cache data loaded during invalidation', async () => {
    let resolveLoader!: (value: string) => void;

    const loader = () =>
      new Promise<string>((resolve) => {
        resolveLoader = resolve;
      });

    const pending = cache.getOrLoad('discovery', 'home', loader);

    cache.invalidate('discovery');

    resolveLoader('stale-data');

    await expect(pending).resolves.toBe('stale-data');
    expect(cache.get('discovery', 'home')).toBeNull();
  });

  it('tracks cache hits and misses per namespace', () => {
    expect(cache.get('track', 't1')).toBeNull();

    cache.set('track', 't1', { title: 'A' });

    expect(cache.get('track', 't1')).toEqual({ title: 'A' });
    expect(cache.get('track', 't1')).toEqual({ title: 'A' });

    const track = cache.getStats().find((s) => s.namespace === 'track');

    expect(track).toEqual({
      namespace: 'track',
      hits: 2,
      misses: 1,
      hitRate: 0.67,
    });
  });

  it('returns empty stats when cache was not used', () => {
    expect(cache.getStats()).toEqual([]);
  });
});
