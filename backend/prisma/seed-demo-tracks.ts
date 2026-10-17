// Демо-наповнення каталогу для ручної перевірки клієнта.
//
// На відміну від seed.ts (довідники: жанри, виконавці, admin), цей скрипт
// додає кілька треків з РЕАЛЬНИМИ аудіофайлами та обкладинками, щоб
// головний екран був не порожній, а плеєр міг щось відтворити.
//
// Файли генеруються локально (WAV + PNG) — жодних зовнішніх залежностей
// чи інтернету. Аудіо коротке (12–18 с), цього достатньо для перевірки UI.
//
// Запуск: npm run prisma:seed:demo
//
// Скрипт ідемпотентний: id демо-сутностей фіксовані, файли перезаписуються
// тими самими шляхами, тому повторний запуск нічого не дублює.

import { PrismaClient, TrackStatus } from '@prisma/client';
import { promises as fs } from 'node:fs';
import { join, resolve } from 'node:path';
import { deflateSync } from 'node:zlib';

const prisma = new PrismaClient();

// Каталог сховища той самий, що читає MediaStorageService (UPLOAD_DIR/storage),
// а віддається він статикою за префіксом /media (див. src/main.ts).
const STORAGE_DIR = resolve(process.env.UPLOAD_DIR || 'storage');
const AUDIO_DIR = join(STORAGE_DIR, 'audio', 'demo');
const COVER_DIR = join(STORAGE_DIR, 'cover', 'demo');

const SAMPLE_RATE = 22_050;

// ============================================================
// 1. Генерація аудіо (PCM WAV)
// ============================================================

interface DemoClip {
  /** Тривалість у секундах (вона ж duration у БД). */
  seconds: number;
  bpm: number;
  /** Акорди по тактах: частоти основного тону. */
  chords: number[];
  /** Арпеджіо у півтонах від тону акорду. */
  pattern: number[];
  /** Додає «бочку» на кожну сильну долю. */
  drums: boolean;
}

/** Простий синтез: підкладка + арпеджіо (+ удар), без зовнішніх бібліотек. */
function renderClip(clip: DemoClip): Float32Array {
  const totalSamples = Math.floor(clip.seconds * SAMPLE_RATE);
  const samples = new Float32Array(totalSamples);

  const beatSeconds = 60 / clip.bpm;
  const stepSeconds = beatSeconds / 2; // восьмі
  const barSeconds = beatSeconds * 4;

  const semi = (freq: number, semitones: number) => freq * Math.pow(2, semitones / 12);

  for (let i = 0; i < totalSamples; i++) {
    const t = i / SAMPLE_RATE;
    const bar = Math.floor(t / barSeconds) % clip.chords.length;
    const chordRoot = clip.chords[bar];

    // --- Підкладка: корінь + терція + квінта, дуже тихо, з повільним LFO.
    const padGain = 0.16 * (0.75 + 0.25 * Math.sin(2 * Math.PI * 0.15 * t));
    const pad =
      Math.sin(2 * Math.PI * chordRoot * t) +
      0.6 * Math.sin(2 * Math.PI * semi(chordRoot, 4) * t) +
      0.5 * Math.sin(2 * Math.PI * semi(chordRoot, 7) * t);

    // --- Арпеджіо: короткі ноти з затуханням.
    const step = Math.floor(t / stepSeconds);
    const stepTime = t - step * stepSeconds;
    const noteFrequency = semi(chordRoot, clip.pattern[step % clip.pattern.length] + 12);
    const pluckEnvelope = Math.exp(-stepTime * 9);
    const pluck =
      Math.sin(2 * Math.PI * noteFrequency * t) +
      0.35 * Math.sin(2 * Math.PI * noteFrequency * 2 * t);

    // --- Ритм: затухаючий низький тон на кожну долю.
    let kick = 0;
    if (clip.drums) {
      const beatTime = t % beatSeconds;
      kick = Math.sin(2 * Math.PI * 55 * beatTime) * Math.exp(-beatTime * 22) * 0.35;
    }

    samples[i] = padGain * pad * 0.35 + pluck * pluckEnvelope * 0.22 + kick;
  }

  // Нормалізація до -3 dB, щоб не було кліпінгу.
  let peak = 0;
  for (const value of samples) {
    peak = Math.max(peak, Math.abs(value));
  }
  const scale = peak > 0 ? 0.7 / peak : 1;
  for (let i = 0; i < samples.length; i++) {
    samples[i] *= scale;
  }

  return samples;
}

/** PCM 16-bit mono WAV. */
function encodeWav(samples: Float32Array): Buffer {
  const dataSize = samples.length * 2;
  const buffer = Buffer.alloc(44 + dataSize);

  buffer.write('RIFF', 0, 'ascii');
  buffer.writeUInt32LE(36 + dataSize, 4);
  buffer.write('WAVE', 8, 'ascii');
  buffer.write('fmt ', 12, 'ascii');
  buffer.writeUInt32LE(16, 16);
  buffer.writeUInt16LE(1, 20); // PCM
  buffer.writeUInt16LE(1, 22); // mono
  buffer.writeUInt32LE(SAMPLE_RATE, 24);
  buffer.writeUInt32LE(SAMPLE_RATE * 2, 28);
  buffer.writeUInt16LE(2, 32);
  buffer.writeUInt16LE(16, 34);
  buffer.write('data', 36, 'ascii');
  buffer.writeUInt32LE(dataSize, 40);

  for (let i = 0; i < samples.length; i++) {
    const value = Math.max(-1, Math.min(1, samples[i]));
    buffer.writeInt16LE(Math.round(value * 32_767), 44 + i * 2);
  }

  return buffer;
}

// ============================================================
// 2. Генерація обкладинок (PNG, RGB, без зовнішніх бібліотек)
// ============================================================

const CRC_TABLE = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[n] = c;
  }
  return table;
})();

function crc32(buffer: Buffer): number {
  let crc = -1;
  for (const byte of buffer) {
    crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ -1) >>> 0;
}

function pngChunk(type: string, data: Buffer): Buffer {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length, 0);

  const typeBuffer = Buffer.from(type, 'ascii');
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuffer, data])), 0);

  return Buffer.concat([length, typeBuffer, data, crc]);
}

function hexToRgb(hex: string): [number, number, number] {
  const value = hex.replace('#', '');
  return [
    parseInt(value.slice(0, 2), 16),
    parseInt(value.slice(2, 4), 16),
    parseInt(value.slice(4, 6), 16),
  ];
}

/** Абстрактна обкладинка: градієнт + коло + смуги. Кольори задає альбом. */
function encodeCoverPng(
  size: number,
  from: string,
  to: string,
  accent: string,
  seed: number,
): Buffer {
  const [r1, g1, b1] = hexToRgb(from);
  const [r2, g2, b2] = hexToRgb(to);
  const [ra, ga, ba] = hexToRgb(accent);

  const circleX = size * (0.3 + (seed % 3) * 0.18);
  const circleY = size * (0.32 + (seed % 2) * 0.2);
  const circleRadius = size * 0.3;

  const raw = Buffer.alloc((size * 3 + 1) * size);

  for (let y = 0; y < size; y++) {
    const rowStart = y * (size * 3 + 1);
    raw[rowStart] = 0; // filter: none

    for (let x = 0; x < size; x++) {
      // Діагональний градієнт.
      const mix = (x + y) / (2 * size);

      let r = r1 + (r2 - r1) * mix;
      let g = g1 + (g2 - g1) * mix;
      let b = b1 + (b2 - b1) * mix;

      // Світле коло — «місяць» у центрі композиції.
      const distance = Math.hypot(x - circleX, y - circleY);
      if (distance < circleRadius) {
        const glow = 1 - distance / circleRadius;
        r += (ra - r) * glow * 0.45;
        g += (ga - g) * glow * 0.45;
        b += (ba - b) * glow * 0.45;
      }

      // Тонкі горизонтальні смуги додають «вінілового» вигляду.
      if (Math.floor(y / 6) % 9 === 0) {
        r *= 0.9;
        g *= 0.9;
        b *= 0.9;
      }

      const offset = rowStart + 1 + x * 3;
      raw[offset] = Math.round(Math.max(0, Math.min(255, r)));
      raw[offset + 1] = Math.round(Math.max(0, Math.min(255, g)));
      raw[offset + 2] = Math.round(Math.max(0, Math.min(255, b)));
    }
  }

  const header = Buffer.alloc(13);
  header.writeUInt32BE(size, 0);
  header.writeUInt32BE(size, 4);
  header[8] = 8; // bit depth
  header[9] = 2; // color type: RGB
  header[10] = 0; // compression
  header[11] = 0; // filter
  header[12] = 0; // interlace

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk('IHDR', header),
    pngChunk('IDAT', deflateSync(raw, { level: 9 })),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

// ============================================================
// 3. Демо-дані
// ============================================================

interface DemoAlbum {
  id: string;
  slug: string;
  title: string;
  artistName: string;
  releaseYear: number;
  from: string;
  to: string;
  accent: string;
}

interface DemoTrack {
  id: string;
  slug: string;
  title: string;
  artistName: string;
  albumSlug: string;
  genre: string;
  playCount: number;
  likeCount: number;
  clip: DemoClip;
}

const DEMO_ALBUMS: DemoAlbum[] = [
  {
    id: 'd0000000-0000-4000-8000-0000000000b1',
    slug: 'neon-nights',
    title: 'Neon Nights',
    artistName: 'Neon Sky',
    releaseYear: 2026,
    from: '#1B1035',
    to: '#6A2BD9',
    accent: '#FF7AD9',
  },
  {
    id: 'd0000000-0000-4000-8000-0000000000b2',
    slug: 'moonlit-drive',
    title: 'Moonlit Drive',
    artistName: 'Luna Waves',
    releaseYear: 2026,
    from: '#06283D',
    to: '#2E8BC0',
    accent: '#B1D4E0',
  },
  {
    id: 'd0000000-0000-4000-8000-0000000000b3',
    slug: 'blue-hour-sessions',
    title: 'Blue Hour Sessions',
    artistName: 'Midnight Jazz Club',
    releaseYear: 2025,
    from: '#1A1A2E',
    to: '#3F3351',
    accent: '#E9C46A',
  },
  {
    id: 'd0000000-0000-4000-8000-0000000000b4',
    slug: 'highway-fire',
    title: 'Highway Fire',
    artistName: 'Rock Legends',
    releaseYear: 2025,
    from: '#3E0703',
    to: '#B33A21',
    accent: '#FFD166',
  },
];

const DEMO_TRACKS: DemoTrack[] = [
  {
    id: 'd0000000-0000-4000-8000-000000000001',
    slug: 'neon-overflow',
    title: 'Neon Overflow',
    artistName: 'Neon Sky',
    albumSlug: 'neon-nights',
    genre: 'Electronic',
    playCount: 1280,
    likeCount: 84,
    clip: {
      seconds: 16,
      bpm: 134,
      chords: [220, 261.63, 196, 174.61],
      pattern: [0, 7, 12, 7, 3, 7, 12, 15],
      drums: true,
    },
  },
  {
    id: 'd0000000-0000-4000-8000-000000000002',
    slug: 'midnight-arcade',
    title: 'Midnight Arcade',
    artistName: 'Neon Sky',
    albumSlug: 'neon-nights',
    genre: 'Electronic',
    playCount: 980,
    likeCount: 61,
    clip: {
      seconds: 14,
      bpm: 122,
      chords: [174.61, 196, 220, 164.81],
      pattern: [0, 4, 7, 12, 7, 4],
      drums: true,
    },
  },
  {
    id: 'd0000000-0000-4000-8000-000000000003',
    slug: 'afterglow',
    title: 'Afterglow',
    artistName: 'Neon Sky',
    albumSlug: 'neon-nights',
    genre: 'Electronic',
    playCount: 640,
    likeCount: 39,
    clip: {
      seconds: 18,
      bpm: 100,
      chords: [261.63, 220, 174.61, 196],
      pattern: [0, 7, 3, 10],
      drums: false,
    },
  },
  {
    id: 'd0000000-0000-4000-8000-000000000004',
    slug: 'tidal-bloom',
    title: 'Tidal Bloom',
    artistName: 'Luna Waves',
    albumSlug: 'moonlit-drive',
    genre: 'Electronic',
    playCount: 1500,
    likeCount: 122,
    clip: {
      seconds: 15,
      bpm: 112,
      chords: [146.83, 174.61, 196, 130.81],
      pattern: [0, 7, 12, 10, 7, 3],
      drums: true,
    },
  },
  {
    id: 'd0000000-0000-4000-8000-000000000005',
    slug: 'silver-coast',
    title: 'Silver Coast',
    artistName: 'Luna Waves',
    albumSlug: 'moonlit-drive',
    genre: 'Lo-Fi',
    playCount: 720,
    likeCount: 47,
    clip: {
      seconds: 17,
      bpm: 96,
      chords: [220, 174.61, 261.63, 196],
      pattern: [0, 3, 7, 12],
      drums: false,
    },
  },
  {
    id: 'd0000000-0000-4000-8000-000000000006',
    slug: 'smoke-and-velvet',
    title: 'Smoke & Velvet',
    artistName: 'Midnight Jazz Club',
    albumSlug: 'blue-hour-sessions',
    genre: 'Jazz',
    playCount: 860,
    likeCount: 55,
    clip: {
      seconds: 16,
      bpm: 88,
      chords: [130.81, 155.56, 174.61, 116.54],
      pattern: [0, 3, 7, 10, 7, 3],
      drums: true,
    },
  },
  {
    id: 'd0000000-0000-4000-8000-000000000007',
    slug: 'last-call',
    title: 'Last Call',
    artistName: 'Midnight Jazz Club',
    albumSlug: 'blue-hour-sessions',
    genre: 'Jazz',
    playCount: 410,
    likeCount: 28,
    clip: {
      seconds: 18,
      bpm: 76,
      chords: [116.54, 130.81, 155.56, 174.61],
      pattern: [0, 4, 7, 11],
      drums: false,
    },
  },
  {
    id: 'd0000000-0000-4000-8000-000000000008',
    slug: 'thunder-mile',
    title: 'Thunder Mile',
    artistName: 'Rock Legends',
    albumSlug: 'highway-fire',
    genre: 'Rock',
    playCount: 1120,
    likeCount: 73,
    clip: {
      seconds: 13,
      bpm: 140,
      chords: [164.81, 196, 220, 146.83],
      pattern: [0, 7, 12, 7],
      drums: true,
    },
  },
];

const GENRE_COLORS: Record<string, string> = {
  Electronic: '#40C4FF',
  'Lo-Fi': '#7C4DFF',
  Jazz: '#69F0AE',
  Rock: '#FF5252',
};

// ============================================================
// 4. Запис у базу
// ============================================================

const ARTIST_DESCRIPTIONS: Record<string, string> = {
  'Neon Sky': 'Синтвейв і ретро-електроніка',
  'Luna Waves': 'Електронна музика для вечірнього настрою',
  'Midnight Jazz Club': 'Джазові композиції',
  'Rock Legends': 'Класичний рок',
};

/** Виконавці в схемі не мають unique-поля, тому шукаємо за іменем. */
async function ensureArtist(name: string): Promise<string> {
  const description = ARTIST_DESCRIPTIONS[name] ?? null;
  const existing = await prisma.artist.findFirst({ where: { name } });

  if (existing) {
    await prisma.artist.update({ where: { id: existing.id }, data: { description } });
    return existing.id;
  }

  const created = await prisma.artist.create({ data: { name, description } });
  return created.id;
}

async function main(): Promise<void> {
  console.log('Наповнюємо каталог демо-треками...');

  await fs.mkdir(AUDIO_DIR, { recursive: true });
  await fs.mkdir(COVER_DIR, { recursive: true });

  // --- Жанри (унікальні за назвою) ---
  const genreIds = new Map<string, string>();

  for (const name of new Set(DEMO_TRACKS.map((track) => track.genre))) {
    const genre = await prisma.genre.upsert({
      where: { name },
      update: {},
      create: { name, color: GENRE_COLORS[name] ?? null },
    });
    genreIds.set(name, genre.id);
  }

  console.log(`Жанри: ${genreIds.size}`);

  // --- Виконавці, альбоми та обкладинки ---
  const albumIds = new Map<string, string>();
  const albumArtistIds = new Map<string, string>();
  const albumCovers = new Map<string, string>();

  for (const [index, album] of DEMO_ALBUMS.entries()) {
    const artistId = await ensureArtist(album.artistName);
    albumIds.set(album.slug, album.id);
    albumArtistIds.set(album.slug, artistId);

    await fs.writeFile(
      join(COVER_DIR, `${album.slug}.png`),
      encodeCoverPng(600, album.from, album.to, album.accent, index + 1),
    );

    const coverUrl = `/media/cover/demo/${album.slug}.png`;
    const data = {
      title: album.title,
      coverUrl,
      releaseYear: album.releaseYear,
      artistId,
    };

    await prisma.album.upsert({
      where: { id: album.id },
      update: data,
      create: { id: album.id, ...data },
    });

    albumCovers.set(album.slug, coverUrl);
  }

  console.log(`Альбоми: ${DEMO_ALBUMS.length}`);

  // --- Треки та аудіофайли ---
  for (const track of DEMO_TRACKS) {
    await fs.writeFile(
      join(AUDIO_DIR, `${track.slug}.wav`),
      encodeWav(renderClip(track.clip)),
    );

    const data = {
      title: track.title,
      duration: track.clip.seconds,
      coverUrl: albumCovers.get(track.albumSlug) ?? null,
      audioUrl: `/media/audio/demo/${track.slug}.wav`,
      playCount: track.playCount,
      likeCount: track.likeCount,
      status: TrackStatus.PUBLISHED,
      artistId: albumArtistIds.get(track.albumSlug) as string,
      albumId: albumIds.get(track.albumSlug) ?? null,
      genreId: genreIds.get(track.genre) ?? null,
    };

    await prisma.track.upsert({
      where: { id: track.id },
      update: data,
      create: { id: track.id, ...data },
    });
  }

  console.log(`Треки: ${DEMO_TRACKS.length} (WAV + PNG у ${STORAGE_DIR})`);

  // --- Треки зі старого seed.ts: без аудіо картка в плеєрі «німа».
  // Підставляємо їм демо-файли, щоб можна було натиснути будь-яку картку.
  const silentTracks = await prisma.track.findMany({
    where: { status: TrackStatus.PUBLISHED, audioUrl: null },
    orderBy: { createdAt: 'asc' },
  });

  for (const [index, track] of silentTracks.entries()) {
    const demo = DEMO_TRACKS[index % DEMO_TRACKS.length];

    await prisma.track.update({
      where: { id: track.id },
      data: {
        audioUrl: `/media/audio/demo/${demo.slug}.wav`,
        coverUrl: albumCovers.get(demo.albumSlug) ?? null,
        duration: demo.clip.seconds,
      },
    });
  }

  if (silentTracks.length > 0) {
    console.log(`Раніше засіяним трекам додано аудіо: ${silentTracks.length}`);
  }

  const [published, playable] = await Promise.all([
    prisma.track.count({ where: { status: TrackStatus.PUBLISHED } }),
    prisma.track.count({
      where: { status: TrackStatus.PUBLISHED, audioUrl: { not: null } },
    }),
  ]);

  console.log(`Готово. Опубліковано треків: ${published}, з аудіо: ${playable}`);
  console.log('Перевірка: GET /api/tracks/popular та GET /api/tracks/new');
}

main()
  .catch((error: unknown) => {
    console.error('Не вдалося наповнити каталог:', error);
    process.exitCode = 1;
  })
  .finally(() => {
    void prisma.$disconnect();
  });

