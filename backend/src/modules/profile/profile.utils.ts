export function normalizeNickname(value: string): string {
  return value.trim().replace(/\s+/g, ' ');
}

export function normalizeEmail(value: string): string {
  return value.trim().toLowerCase();
}

// З /media/... дістаємо storage key, щоб видалити старий файл.
export function storageKeyFromUrl(url: string): string | null {
  const prefix = '/media/';

  if (!url.startsWith(prefix)) {
    return null;
  }

  return url.slice(prefix.length).replace(/\\/g, '/');
}
