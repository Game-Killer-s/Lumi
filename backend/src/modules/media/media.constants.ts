import { BadRequestException } from '@nestjs/common';
import type { MulterOptions } from '@nestjs/platform-express/multer/interfaces/multer-options.interface';
import { extname, join } from 'node:path';
import { tmpdir } from 'node:os';

// Вид медіафайлу. AVATAR не є MediaAssetKind, але використовує ту саму теку.
export type MediaKind = 'AUDIO' | 'COVER' | 'AVATAR';

// Файл, який multer поклав на диск (потрібне нам підмножина полів).
export interface UploadedMediaFile {
  originalname: string;
  mimetype: string;
  size: number;
  path: string;
}

// Тимчасова папка: сюди multer стрімить файл, далі MediaSecurityService
// його перевіряє, і тільки після цього файл переїжджає у UPLOAD_DIR.
export const MEDIA_TEMP_DIR = join(tmpdir(), 'lumi-media-uploads');

// Ліміти розміру файлу (ТЗ: аудіо - великі FLAC/WAV, обкладинки - невеликі).
export const MAX_AUDIO_UPLOAD_BYTES = 100 * 1024 * 1024; // 100 MB
export const MAX_COVER_UPLOAD_BYTES = 5 * 1024 * 1024; // 5 MB

// Дозволені типи: mime з клієнта -> канонічне розширення для storage key.
const AUDIO_MIME_TO_EXTENSION: Record<string, string> = {
  'audio/flac': '.flac',
  'audio/x-flac': '.flac',
  'audio/wav': '.wav',
  'audio/x-wav': '.wav',
  'audio/wave': '.wav',
  'audio/vnd.wave': '.wav',
};

const COVER_MIME_TO_EXTENSION: Record<string, string> = {
  'image/jpeg': '.jpg',
  'image/png': '.png',
};

const AUDIO_EXTENSIONS = ['.flac', '.wav'];
const COVER_EXTENSIONS = ['.jpg', '.jpeg', '.png'];

// PNG має фіксовану 8-байтову сигнатуру.
const PNG_SIGNATURE = Buffer.from([
  0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
]);

// Сигнатура тестового "вірусу" EICAR - стандартний спосіб перевірити,
// що антивірусний pre-check справді працює (файл безпечний).
export const EICAR_MARKER = 'X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR';

// Публічне посилання на файл. Сама роздача (CDN / підписані посилання)
// у цій задачі не робиться - тут фіксуємо стабільний шлях до об'єкта.
export function mediaPublicUrl(storageKey: string): string {
  return `/media/${storageKey.replace(/\\/g, '/')}`;
}

// Повертає розширення для storage key, якщо mime і розширення файлу
// дозволені; інакше null (файл відкидаємо).
export function extensionForMedia(
  kind: MediaKind,
  originalName: string,
  mimeType: string,
): string | null {
  const canonical =
    kind === 'AUDIO'
      ? AUDIO_MIME_TO_EXTENSION[mimeType]
      : COVER_MIME_TO_EXTENSION[mimeType];

  const allowed = kind === 'AUDIO' ? AUDIO_EXTENSIONS : COVER_EXTENSIONS;
  const fromName = extname(originalName ?? '').toLowerCase();

  if (!canonical || !allowed.includes(fromName)) {
    return null;
  }

  return canonical;
}

// Перевірка справжнього типу за магічними байтами: клієнтському mime
// довіряти не можна, файл може бути названий "track.flac" а бути чимось іншим.
export function matchesMediaSignature(kind: MediaKind, head: Buffer): boolean {
  if (kind === 'AUDIO') {
    const marker = head.subarray(0, 4).toString('latin1');

    if (marker === 'fLaC') {
      return true; // FLAC
    }

    // WAV: "RIFF" на початку і "WAVE" у 8-11 байтах
    return (
      marker === 'RIFF' && head.subarray(8, 12).toString('latin1') === 'WAVE'
    );
  }

  const isJpeg = head[0] === 0xff && head[1] === 0xd8 && head[2] === 0xff;
  const isPng = head.subarray(0, 8).equals(PNG_SIGNATURE);

  return isJpeg || isPng;
}

// Валідація типу ще до запису на диск: якщо формат не наш - одразу 400.
function buildFileFilter(kind: MediaKind) {
  return (
    _request: unknown,
    file: { originalname: string; mimetype: string },
    callback: (error: Error | null, acceptFile: boolean) => void,
  ): void => {
    if (!extensionForMedia(kind, file.originalname, file.mimetype)) {
      callback(
        new BadRequestException(
          `Unsupported ${kind.toLowerCase()} format: ${file.mimetype}`,
        ),
        false,
      );

      return;
    }

    callback(null, true);
  };
}

// Аудіо: стрім на диск (без буферизації у пам'яті) + ліміт 100 MB.
export const AUDIO_UPLOAD_OPTIONS: MulterOptions = {
  dest: MEDIA_TEMP_DIR,
  limits: { fileSize: MAX_AUDIO_UPLOAD_BYTES, files: 1 },
  fileFilter: buildFileFilter('AUDIO'),
};

// Обкладинка: те саме, але ліміт 5 MB.
export const COVER_UPLOAD_OPTIONS: MulterOptions = {
  dest: MEDIA_TEMP_DIR,
  limits: { fileSize: MAX_COVER_UPLOAD_BYTES, files: 1 },
  fileFilter: buildFileFilter('COVER'),
};
