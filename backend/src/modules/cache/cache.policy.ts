import { CACHE_TTL } from './cache.constants';
import { CacheNamespace } from './cache.service';

// Список endpoints, які можна (і треба) кешувати.
// Це публічні дані — каталог і discovery стрічки.
export interface CacheableEndpoint {
  method: string;
  path: string;
  namespace: CacheNamespace;
  ttlMs: number;
}

export const CACHEABLE_ENDPOINTS: CacheableEndpoint[] = [
  {
    method: 'GET',
    path: '/api/catalog',
    namespace: 'catalog',
    ttlMs: CACHE_TTL.catalog,
  },
  {
    method: 'GET',
    path: '/api/catalog/popular',
    namespace: 'discovery',
    ttlMs: CACHE_TTL.popular,
  },
  {
    method: 'GET',
    path: '/api/catalog/new-releases',
    namespace: 'discovery',
    ttlMs: CACHE_TTL.newReleases,
  },
  {
    method: 'GET',
    path: '/api/discovery',
    namespace: 'discovery',
    ttlMs: CACHE_TTL.discovery,
  },
];

// Персональні дані НІКОЛИ не кешуємо в shared cache.
// Вони належать конкретному користувачу і не мають бути
// доступні іншим через кеш.
export const NON_CACHEABLE_ENDPOINTS: string[] = [
  '/api/auth/*',
  '/api/me',
  '/api/playlists/*',
  '/api/likes/*',
  '/api/history/*',
  '/api/recommendations/*',
];
