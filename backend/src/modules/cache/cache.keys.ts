// Cache keys — рядки, за якими дані зберігаються в кеші.
// Один і той самий запит => один і той самий ключ => відповідь із кешу.

export interface CatalogKeyParams {
  genreId?: string;
  sort: string;
  page: number;
  limit: number;
}

// Ключ для каталогу: враховує всі фільтри запиту.
// Приклад: catalog:all:popular:1:20
export function catalogKey(params: CatalogKeyParams): string {
  const genre = params.genreId ?? 'all';

  return `catalog:${genre}:${params.sort}:${params.page}:${params.limit}`;
}

// Ключ для популярних треків.
// Приклад: popular:10
export function popularKey(limit: number): string {
  return `popular:${limit}`;
}

// Ключ для нових релізів.
// Приклад: new-releases:10
export function newReleasesKey(limit: number): string {
  return `new-releases:${limit}`;
}

// Ключ для discovery стрічки.
// Приклад: discovery:home
export function discoveryKey(feed: string): string {
  return `discovery:${feed}`;
}
