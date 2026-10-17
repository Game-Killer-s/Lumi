import { INestApplication } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import {
  MediaAssetKind,
  MediaMetadataStatus,
  MediaScanStatus,
  ProcessingStatus,
  Role,
  TrackStatus,
} from '@prisma/client';
import { createHash } from 'node:crypto';
import { promises as fs } from 'node:fs';
import request from 'supertest';

import { AppModule } from '../src/app.module';
import { setupApp } from '../src/app.setup';
import { PrismaService } from '../src/prisma/prisma.service';
import { PasswordService } from '../src/modules/auth/services/password.service';
import { AudioToolchainService } from '../src/modules/media/audio-toolchain.service';
import { EICAR_MARKER } from '../src/modules/media/media.constants';
import { MediaStorageService } from '../src/modules/media/media-storage.service';

/** Тип тіла відповіді на завантаження медіа */
interface UploadBody {
  track: {
    id: string;
    status: TrackStatus;
    audioUrl: string | null;
    coverUrl: string | null;
    title: string;
    duration: number;
    lyrics: string | null;
  };
  asset?: {
    id: string;
    storageKey: string;
    metadataStatus: MediaMetadataStatus;
    durationSeconds: number | null;
    codec: string | null;
    sampleRateHz: number | null;
    channels: number | null;
    metadataError: string | null;
  };
  job?: { status: ProcessingStatus; failureReason: string | null };
}

// ffprobe підмінений: тести не залежать від наявності бінарника на машині,
// але перевіряють, що метадані з probe справді лягають у media record.
const PROBE_RESULT = {
  streams: [
    {
      codec_type: 'audio',
      codec_name: 'flac',
      sample_rate: '44100',
      channels: 2,
      bit_rate: '1014000',
      duration: '214.360000',
      tags: { title: 'Midnight Drive' },
    },
  ],
  format: {
    duration: '214.360000',
    bit_rate: '1014000',
    tags: { title: 'Midnight Drive', artist: 'Lumi' },
  },
};

const toolchain = { probe: jest.fn().mockResolvedValue(PROBE_RESULT) };

// Справжній FLAC: сигнатура + дані. Перевірка вмісту проходить.
const FLAC_BUFFER = Buffer.concat([Buffer.from('fLaC'), Buffer.alloc(4096, 7)]);

// Справжній PNG (сигнатура) - для обкладинок.
const PNG_BUFFER = Buffer.concat([
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  Buffer.alloc(2048, 3),
]);

/**
 * E2E тести завантаження медіа (multipart).
 *
 * Потрібна робоча база даних (PostgreSQL) із застосованими міграціями.
 * Тестові дані створюються в beforeAll і видаляються в afterAll.
 */
describe('Media upload (e2e)', () => {
  let app: INestApplication;
  let prisma: PrismaService;
  let passwordService: PasswordService;
  let storage: MediaStorageService;

  let adminToken: string;
  let listenerToken: string;

  let artistId: string;
  let draftTrackId: string;
  let publishedTrackId: string;

  const suffix = Date.now();
  const listenerEmail = `media-listener-${suffix}@lumi.app`;

  // Файли, які тести створили у сховищі - прибираємо в afterAll.
  const storedKeys: string[] = [];

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    })
      .overrideProvider(AudioToolchainService)
      .useValue(toolchain)
      .compile();

    app = moduleFixture.createNestApplication();
    setupApp(app);
    await app.init();

    prisma = app.get(PrismaService);
    passwordService = app.get(PasswordService);
    storage = app.get(MediaStorageService);

    const listenerPasswordHash = await passwordService.hash('listener123');

    await prisma.user.create({
      data: {
        email: listenerEmail,
        passwordHash: listenerPasswordHash,
        nickname: 'Media E2E Listener',
        role: Role.LISTENER,
      },
    });

    const artist = await prisma.artist.create({
      data: { name: `Media E2E Artist ${suffix}` },
    });
    artistId = artist.id;

    const genre = await prisma.genre.findFirstOrThrow();

    const draftTrack = await prisma.track.create({
      data: {
        title: `Media E2E Draft ${suffix}`,
        duration: 0,
        status: TrackStatus.DRAFT,
        artistId,
      },
    });
    draftTrackId = draftTrack.id;

    const publishedTrack = await prisma.track.create({
      data: {
        title: `Media E2E Published ${suffix}`,
        duration: 180,
        status: TrackStatus.PUBLISHED,
        artistId,
        genreId: genre.id,
      },
    });
    publishedTrackId = publishedTrack.id;

    adminToken = await login('admin@lumi.app', 'admin123');
    listenerToken = await login(listenerEmail, 'listener123');
  });

  async function login(email: string, password: string) {
    const response = await request(app.getHttpServer())
      .post('/api/auth/login')
      .send({ email, password })
      .expect(200);

    return (response.body as { accessToken: string }).accessToken;
  }

  function uploadAudio(
    trackId: string,
    buffer: Buffer,
    filename = 'song.flac',
    contentType = 'audio/flac',
    token = adminToken,
  ) {
    return request(app.getHttpServer())
      .post(`/api/admin/tracks/${trackId}/audio`)
      .set('Authorization', `Bearer ${token}`)
      .attach('audio', buffer, { filename, contentType });
  }

  function uploadCover(
    trackId: string,
    buffer: Buffer,
    filename = 'cover.png',
    contentType = 'image/png',
    token = adminToken,
  ) {
    return request(app.getHttpServer())
      .post(`/api/admin/tracks/${trackId}/cover`)
      .set('Authorization', `Bearer ${token}`)
      .attach('cover', buffer, { filename, contentType });
  }

  async function createDraftTrack(): Promise<string> {
    const track = await prisma.track.create({
      data: {
        title: `Media E2E Draft ${suffix}-${storedKeys.length}`,
        duration: 0,
        status: TrackStatus.DRAFT,
        artistId,
      },
    });

    return track.id;
  }

  afterAll(async () => {
    for (const key of storedKeys) {
      await fs.rm(storage.absolutePath(key), { force: true });
    }

    if (prisma) {
      // Каскадом видаляться asset-и, job-и, варіанти й audit log.
      await prisma.track.deleteMany({ where: { artistId } });
      await prisma.artist.deleteMany({ where: { id: artistId } });
      await prisma.user.deleteMany({ where: { email: listenerEmail } });
    }

    if (app) {
      await app.close();
    }
  });

  describe('access control', () => {
    it('rejects an anonymous upload', async () => {
      await request(app.getHttpServer())
        .post(`/api/admin/tracks/${draftTrackId}/audio`)
        .attach('audio', FLAC_BUFFER, {
          filename: 'song.flac',
          contentType: 'audio/flac',
        })
        .expect(401);
    });

    it('rejects a listener upload', async () => {
      await uploadAudio(
        draftTrackId,
        FLAC_BUFFER,
        'song.flac',
        'audio/flac',
        listenerToken,
      ).expect(403);

      await uploadCover(
        publishedTrackId,
        PNG_BUFFER,
        'cover.png',
        'image/png',
        listenerToken,
      ).expect(403);
    });
  });

  describe('format validation', () => {
    it('rejects an unsupported audio format', async () => {
      const response = await uploadAudio(
        draftTrackId,
        Buffer.from('ID3'),
        'song.mp3',
        'audio/mpeg',
      ).expect(400);

      expect((response.body as { message: string }).message).toContain(
        'Unsupported audio format',
      );

      expect(
        await prisma.mediaAsset.count({ where: { trackId: draftTrackId } }),
      ).toBe(0);
    });

    it('rejects a cover that is not JPG/PNG', async () => {
      await uploadCover(
        publishedTrackId,
        Buffer.from('GIF89a'),
        'cover.gif',
        'image/gif',
      ).expect(400);
    });

    it('rejects an oversized cover with 413', async () => {
      // Ліміт обкладинки - 5 MB.
      const oversized = Buffer.concat([
        PNG_BUFFER,
        Buffer.alloc(6 * 1024 * 1024, 1),
      ]);

      await uploadCover(publishedTrackId, oversized).expect(413);
    });
  });

  describe('security pre-check', () => {
    it('rejects a file without the required audio container', async () => {
      const response = await uploadAudio(
        draftTrackId,
        Buffer.concat([Buffer.from('ID3'), Buffer.alloc(4096, 1)]),
      ).expect(400);

      expect((response.body as { message: string }).message).toContain(
        'Security pre-check failed',
      );

      expect(
        await prisma.mediaAsset.count({ where: { trackId: draftTrackId } }),
      ).toBe(0);
    });

    it('rejects a file with the EICAR antivirus signature', async () => {
      const infected = Buffer.concat([
        Buffer.from('fLaC'),
        Buffer.from(EICAR_MARKER),
        Buffer.alloc(4096, 1),
      ]);

      const response = await uploadAudio(draftTrackId, infected).expect(400);

      expect((response.body as { message: string }).message).toContain(
        'Security pre-check failed',
      );

      expect(
        await prisma.mediaAsset.count({ where: { trackId: draftTrackId } }),
      ).toBe(0);
    });
  });

  describe('valid uploads', () => {
    it('stores a FLAC file and moves the track to UPLOADED', async () => {
      const trackId = await createDraftTrack();
      const response = await uploadAudio(trackId, FLAC_BUFFER).expect(201);

      const body = response.body as UploadBody;

      expect(body.track.status).toBe(TrackStatus.UPLOADED);
      expect(body.track.audioUrl).toMatch(
        /^\/media\/audio\/\d{4}\/\d{2}\/[0-9a-f-]{36}\.flac$/,
      );

      // Тривалість у формі не передавали - її взяв probe.
      expect(body.track.duration).toBe(214);
      expect(body.asset?.metadataStatus).toBe(MediaMetadataStatus.EXTRACTED);
      expect(body.job?.status).toBe(ProcessingStatus.QUEUED);

      const storageKey = (body.track.audioUrl as string).replace('/media/', '');
      storedKeys.push(storageKey);

      // Файл справді у сховищі та під іменем, яке не задавав клієнт.
      expect(storageKey).not.toContain('song');

      const stored = await fs.readFile(storage.absolutePath(storageKey));

      expect(stored.equals(FLAC_BUFFER)).toBe(true);

      const asset = await prisma.mediaAsset.findFirstOrThrow({
        where: { trackId },
      });

      expect(asset.kind).toBe(MediaAssetKind.AUDIO);
      expect(asset.scanStatus).toBe(MediaScanStatus.CLEAN);
      expect(asset.originalName).toBe('song.flac');
      expect(asset.sizeBytes).toBe(FLAC_BUFFER.length);
      expect(asset.checksum).toBe(
        createHash('sha256').update(FLAC_BUFFER).digest('hex'),
      );

      // Метадані з probe збережені в тому самому media record.
      expect(asset.metadataStatus).toBe(MediaMetadataStatus.EXTRACTED);
      expect(asset.durationSeconds).toBe(214.36);
      expect(asset.codec).toBe('flac');
      expect(asset.sampleRateHz).toBe(44100);
      expect(asset.channels).toBe(2);
      expect(asset.tags).toEqual({ title: 'Midnight Drive', artist: 'Lumi' });
      expect(asset.metadataError).toBeNull();

      // Job обробки поставлено у чергу.
      const job = await prisma.mediaProcessingJob.findUniqueOrThrow({
        where: { assetId: asset.id },
      });

      expect(job.status).toBe(ProcessingStatus.QUEUED);
      expect(job.attempts).toBe(0);

      const logs = await prisma.trackAuditLog.findMany({
        where: {
          trackId,
          action: 'MEDIA_UPLOADED:DRAFT->UPLOADED',
        },
      });

      expect(logs.length).toBeGreaterThan(0);
    });

    it('applies title, duration and lyrics from the multipart body', async () => {
      const trackId = await createDraftTrack();

      const response = await request(app.getHttpServer())
        .post(`/api/admin/tracks/${trackId}/audio`)
        .set('Authorization', `Bearer ${adminToken}`)
        .field('title', `Uploaded ${suffix}`)
        .field('duration', '214')
        .field('lyrics', 'la la la')
        .attach('audio', FLAC_BUFFER, {
          filename: 'song.flac',
          contentType: 'audio/flac',
        })
        .expect(201);

      const body = response.body as UploadBody;

      storedKeys.push((body.track.audioUrl as string).replace('/media/', ''));

      expect(body.track.title).toBe(`Uploaded ${suffix}`);
      expect(body.track.duration).toBe(214);
      expect(body.track.lyrics).toBe('la la la');
    });

    it('stores a PNG cover for a published track', async () => {
      const response = await uploadCover(publishedTrackId, PNG_BUFFER).expect(
        201,
      );

      const body = response.body as UploadBody;

      expect(body.track.coverUrl).toMatch(
        /^\/media\/cover\/\d{4}\/\d{2}\/[0-9a-f-]{36}\.png$/,
      );

      storedKeys.push((body.track.coverUrl as string).replace('/media/', ''));

      const asset = await prisma.mediaAsset.findFirstOrThrow({
        where: { trackId: publishedTrackId, kind: MediaAssetKind.COVER },
      });

      expect(asset.mimeType).toBe('image/png');
      expect(asset.scanStatus).toBe(MediaScanStatus.CLEAN);

      // Для обкладинки job обробки не створюється.
      expect(
        await prisma.mediaProcessingJob.count({
          where: { assetId: asset.id },
        }),
      ).toBe(0);
    });

    it('refuses audio for a track that is no longer a draft', async () => {
      await uploadAudio(publishedTrackId, FLAC_BUFFER).expect(400);
    });

    it('returns 404 for an unknown track', async () => {
      await uploadAudio(
        '00000000-0000-0000-0000-000000000000',
        FLAC_BUFFER,
      ).expect(404);
    });
  });
});
