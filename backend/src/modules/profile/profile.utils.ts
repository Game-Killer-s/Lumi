export function normalizeNickname(value: string): string {
  return value.trim().replace(/\s+/g, ' ');
}

export function normalizeEmail(value: string): string {
  return value.trim().toLowerCase();
}

/*
 * Порівняння імен без урахування регістру.
 *
 * У БД немає unique-індексу на nickname (реєстрація його не перевіряє),
 * тому легасі-дублі імен реальні. Завдяки цьому порівнянню «те саме імʼя»
 * не вважається зайнятим чужим акаунтом.
 */
export function sameNickname(a: string, b: string): boolean {
  return (
    normalizeNickname(a).toLowerCase() === normalizeNickname(b).toLowerCase()
  );
}

// З /media/... дістаємо storage key, щоб видалити старий файл.
export function storageKeyFromUrl(url: string): string | null {
  const prefix = '/media/';

  if (!url.startsWith(prefix)) {
    return null;
  }

  return url.slice(prefix.length).replace(/\\/g, '/');
}
