import { Injectable, Logger } from '@nestjs/common';
import { MediaMetadataStatus, Prisma } from '@prisma/client';

import {
  AudioProbeResult,
  AudioProbeStream,
  AudioToolchainService,
} from './audio-toolchain.service';
import { MediaStorageService } from './media-storage.service';

// Кодеки, які ми очікуємо на вході: FLAC і WAV (PCM у різних розрядностях).
const SUPPORTED_INPUT_CODECS = new Set([
  'flac',
  'pcm_s16le',
  'pcm_s24le',
  'pcm_s32le',
  'pcm_f32le',
  'pcm_f64le',
  'pcm_u8',
  'pcm_s8',
]);

// Межі, у яких метадані вважаємо технічно коректними.
export const MIN_SAMPLE_RATE_HZ = 8000;
export const MAX_SAMPLE_RATE_HZ = 384_000;
export const MAX_CHANNELS = 8;

// Теги, які мають сенс у картці треку. Решту вмісту метаданих у базу не
// тягнемо - інакше туди потрапляє будь-що, що знайшлось у файлі.
const SUPPORTED_TAGS: readonly string[] = [
  'title',
  'artist',
  'album',
  'album_artist',
  'genre',
  'date',
  'year',
  'track',
  'tracknumber',
  'bpm',
  'composer',
  'comment',
  'copyright',
  'isrc',
  'label',
];

// Безпечні причини для Content Manager: без шляхів до файлів, без виводу
// ffprobe і без внутрішніх деталей сервера.
export const PROBE_TOOL_MISSING_REASON =
  'Audio probe tool is not available on the server';
export const CORRUPTED_AUDIO_REASON =
  'Audio file could not be read: it may be corrupted or truncated';

// Метадані у тому вигляді, у якому вони лягають у MediaAsset.
export interface ExtractedAudioMetadata {
  durationSeconds: number;
  codec: string;
  sampleRateHz: number;
  channels: number;
  bitrateKbps: number | null;
  tags: Record<string, string> | null;
}

// Результат перевірки файлу: або метадані, або причина для людини.
export type MetadataInspection =
  | { ok: true; metadata: ExtractedAudioMetadata }
  | { ok: false; reason: string };

/**
 * Помилка валідації probe.
 * code - стабільний ідентифікатор для логів і тестів,
 * safeReason - те, що побачить Content Manager.
 */
export class MediaMetadataError extends Error {
  constructor(
    readonly code: string,
    readonly safeReason: string,
    readonly details?: string,
  ) {
    super(details ?? safeReason);
    this.name = 'MediaMetadataError';
  }
}

// Прибирає з тексту шляхи до файлів (Windows та Linux), щоб назовні
// не витікала структура сховища.
export function stripFilePaths(text: string): string {
  return text
    .replace(/[A-Za-z]:[\\/][^\s'"]*/g, '<file>')
    .replace(/\/[^\s'"]+/g, '<file>')
    .trim();
}

/**
 * Єдина точка, де будь-яка помилка конвеєра перетворюється у текст для
 * Content Manager. Помилки probe приходять сюди вже з готовою причиною.
 */
export function toSafeFailureReason(error: unknown): string {
  if (error instanceof MediaMetadataError) {
    return error.safeReason;
  }

  const raw = (error instanceof Error ? error.message : String(error)).replace(
    /^(Media processing failed|Audio probe failed):\s*/i,
    '',
  );

  const cleaned = stripFilePaths(raw).slice(0, 160);

  return cleaned ? `Processing failed: ${cleaned}` : 'Processing failed';
}

// Поля MediaAsset, які заповнює probe. Спільні для upload і для обробки,
// тому лежать тут, а не в жодному з сервісів.
export function assetMetadataFields(inspection: MetadataInspection) {
  if (!inspection.ok) {
    return {
      metadataStatus: MediaMetadataStatus.FAILED,
      metadataError: inspection.reason,
    };
  }

  const { metadata } = inspection;

  return {
    durationSeconds: metadata.durationSeconds,
    codec: metadata.codec,
    sampleRateHz: metadata.sampleRateHz,
    channels: metadata.channels,
    bitrateKbps: metadata.bitrateKbps,
    tags: metadata.tags === null ? Prisma.JsonNull : metadata.tags,
    metadataStatus: MediaMetadataStatus.EXTRACTED,
    metadataError: null,
  };
}
function toNumber(value: unknown): number | null {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null;
  }

  if (typeof value !== 'string') {
    return null;
  }

  const parsed = Number.parseFloat(value);

  return Number.isFinite(parsed) ? parsed : null;
}

// ffprobe друкує "N/A" або порожній рядок - такі значення просто пропускаємо.
function firstPositive(...values: unknown[]): number | null {
  for (const value of values) {
    const parsed = toNumber(value);

    if (parsed !== null && parsed > 0) {
      return parsed;
    }
  }

  return null;
}

function bitsToKbps(...values: unknown[]): number | null {
  const bits = firstPositive(...values);

  return bits === null ? null : Math.round(bits / 1000);
}

function roundDuration(seconds: number | null): number | null {
  return seconds === null ? null : Math.round(seconds * 1000) / 1000;
}

/**
 * Витягнення метаданих з аудіо (US3).
 *
 * Схема роботи: ffprobe -> валідація -> нормалізовані метадані для MediaAsset.
 * ffprobe не декодує весь файл, тому крок дешевий - його роблять і під час
 * завантаження, і перед нормалізацією.
 */
@Injectable()
export class MediaMetadataService {
  private readonly logger = new Logger(MediaMetadataService.name);

  constructor(
    private readonly toolchain: AudioToolchainService,
    private readonly storage: MediaStorageService,
  ) {}

  /**
   * Повний прохід: probe + валідація.
   *
   * Помилку не кидаємо назовні: і upload, і обробка мають отримати причину,
   * яку можна показати Content Manager-у, а не стек помилки.
   */
  async inspect(storageKey: string): Promise<MetadataInspection> {
    try {
      const probe = await this.toolchain.probe(
        this.storage.absolutePath(storageKey),
      );

      return { ok: true, metadata: this.validateProbe(probe) };
    } catch (error) {
      const reason = this.probeFailureReason(error);

      this.logger.warn(
        `Metadata extraction failed for ${storageKey}: ${reason}`,
      );

      return { ok: false, reason };
    }
  }

  /**
   * Валідація результату probe перед транскодуванням.
   * Файл без аудіо-потоку, з нульовою тривалістю, дивною кількістю каналів
   * або чужим кодеком далі не йде.
   */
  validateProbe(probe: AudioProbeResult): ExtractedAudioMetadata {
    const stream = this.audioStream(probe);
    const codec = (stream.codec_name ?? '').toLowerCase();

    if (!SUPPORTED_INPUT_CODECS.has(codec)) {
      throw new MediaMetadataError(
        'UNSUPPORTED_CODEC',
        `Unsupported audio codec: ${codec || 'unknown'} - upload FLAC or WAV`,
      );
    }

    const durationSeconds = roundDuration(
      firstPositive(stream.duration, probe.format?.duration),
    );

    if (durationSeconds === null) {
      throw new MediaMetadataError(
        'INVALID_DURATION',
        'Audio file has no readable duration: it may be corrupted',
      );
    }

    const sampleRateHz = toNumber(stream.sample_rate);

    if (
      sampleRateHz === null ||
      !Number.isInteger(sampleRateHz) ||
      sampleRateHz < MIN_SAMPLE_RATE_HZ ||
      sampleRateHz > MAX_SAMPLE_RATE_HZ
    ) {
      throw new MediaMetadataError(
        'INVALID_SAMPLE_RATE',
        `Unsupported sample rate: ${sampleRateHz ?? 'unknown'} Hz (${MIN_SAMPLE_RATE_HZ}-${MAX_SAMPLE_RATE_HZ} Hz expected)`,
      );
    }

    const { channels } = stream;

    if (
      typeof channels !== 'number' ||
      channels < 1 ||
      channels > MAX_CHANNELS
    ) {
      throw new MediaMetadataError(
        'INVALID_CHANNELS',
        `Unsupported channel count: ${channels ?? 'unknown'}`,
      );
    }

    return {
      durationSeconds,
      codec,
      sampleRateHz,
      channels,
      bitrateKbps: bitsToKbps(probe.format?.bit_rate, stream.bit_rate),
      tags: this.extractTags(probe),
    };
  }

  /**
   * Причина збою probe для Content Manager.
   * Якщо не запустився сам інструмент - це проблема сервера. Усе інше
   * означає, що ffprobe не зміг прочитати контейнер (пошкоджений файл),
   * і назовні йде одна безпечна фраза без внутрішніх деталей.
   */
  probeFailureReason(error: unknown): string {
    if (error instanceof MediaMetadataError) {
      return error.safeReason;
    }

    const raw = error instanceof Error ? error.message : String(error);

    if (/ENOENT|is not recognized|no such file/i.test(raw)) {
      return PROBE_TOOL_MISSING_REASON;
    }

    return CORRUPTED_AUDIO_REASON;
  }

  // Аудіо-потік з відповіді probe (або перший, якщо тип не вказано).
  private audioStream(probe: AudioProbeResult): AudioProbeStream {
    const stream =
      probe.streams.find((candidate) => candidate.codec_type === 'audio') ??
      probe.streams[0];

    if (!stream) {
      throw new MediaMetadataError(
        'NO_AUDIO_STREAM',
        'Audio file has no readable audio stream: it may be corrupted',
      );
    }

    return stream;
  }

  // Embedded tags: спершу теги потоку, поверх них - теги контейнера.
  // Ключі нормалізуємо, залишаємо тільки відомі поля без порожніх значень.
  private extractTags(probe: AudioProbeResult): Record<string, string> | null {
    const stream =
      probe.streams.find((candidate) => candidate.codec_type === 'audio') ??
      probe.streams[0];

    const merged: Record<string, unknown> = {
      ...(stream?.tags ?? {}),
      ...(probe.format?.tags ?? {}),
    };

    const tags: Record<string, string> = {};

    for (const [key, value] of Object.entries(merged)) {
      const normalizedKey = key.toLowerCase().replace(/[\s-]+/g, '_');
      const trimmed = typeof value === 'string' ? value.trim() : '';

      if (SUPPORTED_TAGS.includes(normalizedKey) && trimmed) {
        tags[normalizedKey] = trimmed;
      }
    }

    return Object.keys(tags).length > 0 ? tags : null;
  }
}
