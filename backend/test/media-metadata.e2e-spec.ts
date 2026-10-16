import { INestApplication } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import {
  MediaMetadataStatus,
  ProcessingStatus,
  Role,
  TrackStatus,
} from '@prisma/client';
import { promises as fs } from 'node:fs';
import request from 'supertest';

import { AppModule } from '../src/app.module';
import { setupApp } from '../src/app.setup';
import { PrismaService } from '../src/prisma/prisma.service';
import { PasswordService } from '../src/modules/auth/services/password.service';
import { AudioToolchainService } from '../src/modules/media/audio-toolchain.service';
import { CORRUPTED_AUDIO_REASON } from '../src/modules/media/media-metadata.service';
import { MediaStorageService } from '../src/modules/media/media-storage.service';

/** Тип тіла відповіді на завантаження аудіо */
interface UploadBody {
  track: {
    id: string;
    status: TrackStatus;
    audioUrl: string | null;
    duration: number;
  };
  asset: {
    id: string;
    storageKey: string;
    metadataStatus: MediaMetadataStatus;
    metadataError: string | null;
    durationSeconds: number | null;
    codec: string | null;
    sampleRateHz: number | null;
    channels: number | null;
    bitrateKbps: number | null;
    tags: Record<string, string> | null;
  };
  job: {
    id: string;
    status: ProcessingStatus;
    failureReason: string | null;
  };
}

// Справжні контейнери: security pre-check перевіряє магічні байти.
const FLAC_BUFFER = Buffer.concat([Buffer.from('fLaC'), Buffer.alloc(4096, 7)]);

const WAV_BUFFER = (() => {
  const buffer = Buffer.alloc(4096, 9);

  buffer.write('RIFF', 0, 'latin1');
  buffer.write('WAVE', 8, 'latin1');

  return buffer;
})();

// Вивід ffprobe для FLAC з embedded-тегами.
const FLAC_PROBE = {
  streams: [
    {
      codec_type: 'audio',
      codec_name: 'flac',
      sample_rate: '44100',
      channels: 2,
      bit_rate: '1014000',
      duration: '214.360000',
      tags: { title: 'Midnight Drive', ARTIST: 'Lumi' },
    },
  ],
  format: {
    duration: '214.360000',
    bit_rate: '1014000',
    tags: { title: 'Midnight Drive', artist: 'Lumi', album: 'Night Tapes' },
  },
};

// WAV (PCM 16 bit, моно) без тегів.
const WAV_PROBE = {
  streams: [
    {
      codec_type: 'audio',
      codec_name: 'pcm_s16le',
      sample_rate: '48000',
      channels: 1,
      bit_rate: '768000',
      duration: '12.400000',
    },
  ],
  format: { duration: '12.400000', bit_rate: '768000' },
};

/**
 * E2E тести витягнення метаданих (US3).
 *
 * ffprobe підмінений моком AudioToolchainService: тести не залежать від
 * наявності бінарника, але проходять весь шлях upload -> probe -> media record
 * і перевіряють сценарій пошкодженого файлу на реальних HTTP-запитах.
 */
describe('Audio metadata extraction (e2e)', () => {
  let app: INestApplication;
  let prisma: PrismaService;
  let storage: MediaStorageService;

  let adminToken: string;
  let artistId: string;

  const suffix = Date.now();
  const listenerEmail = `metadata-listener-${suffix}@lumi.app`;

  // Файли, які тести поклали у сховище - прибираємо в afterAll.
  const storedKeys: string[] = [];

  let toolchain: {
    probe: jest.Mock;
    analyzeLoudness: jest.Mock;
    normalizeToTarget: jest.Mock;
    transcodeToOpus: jest.Mock;
  };

  beforeAll(async () => {
    toolchain = {
      probe: jest.fn().mockResolvedValue(FLAC_PROBE),
      analyzeLoudness: jest
        .fn()
        .mockResolvedValue({ integratedLufs: -9.1, truePeakDb: 0.2 }),
      normalizeToTarget: jest.fn().mockResolvedValue(undefined),
      transcodeToOpus: jest.fn().mockResolvedValue(undefined),
    };

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
    storage = app.get(MediaStorageService);

    const passwordService = app.get(PasswordService);
    const listenerPasswordHash = await passwordService.hash('listener123');

    await prisma.user.create({
      data: {
        email: listenerEmail,
        passwordHash: listenerPasswordHash,
        nickname: 'Metadata E2E Listener',
        role: Role.LISTENER,
      },
    });

    const artist = await prisma.artist.create({
      data: { name: `Metadata E2E Artist ${suffix}` },
    });
    artistId = artist.id;

    adminToken = await login('admin@lumi.app', 'admin123');
  });

  async function login(email: string, password: string) {
    const response = await request(app.getHttpServer())
      .post('/api/auth/login')
      .send({ email, password })
      .expect(200);

    return (response.body as { accessToken: string }).accessToken;
  }

  async function createDraftTrack(): Promise<string> {
    const track = await prisma.track.create({
      data: {
        title: `Metadata E2E Draft ${suffix}-${storedKeys.length}`,
        duration: 0,
        status: TrackStatus.DRAFT,
        artistId,
      },
    });

    return track.id;
  }

  function uploadAudio(
    trackId: string,
    buffer: Buffer,
    filename = 'song.flac',
    contentType = 'audio/flac',
  ) {
    return request(app.getHttpServer())
      .post(`/api/admin/tracks/${trackId}/audio`)
      .set('Authorization', `Bearer ${adminToken}`)
      .attach('audio', buffer, { filename, contentType });
  }

  async function uploaded(
    trackId: string,
    ...args: [Buffer, string?, string?]
  ) {
    const response = await uploadAudio(trackId, ...args).expect(201);
    const body = response.body as UploadBody;

    storedKeys.push(body.asset.storageKey);

    return body;
  }

  function getJob(jobId: string) {
    return request(app.getHttpServer())
      .get(`/api/admin/media/jobs/${jobId}`)
      .set('Authorization', `Bearer ${adminToken}`);
  }

  afterAll(async () => {
    for (const key of storedKeys) {
      await fs.rm(storage.absolutePath(key), { force: true });
    }

    if (prisma) {
      // Каскадом видаляться asset-и, job-и та audit log.
      await prisma.track.deleteMany({ where: { artistId } });
      await prisma.artist.deleteMany({ where: { id: artistId } });
      await prisma.user.deleteMany({ where: { email: listenerEmail } });
    }

    if (app) {
      await app.close();
    }
  });

  describe('FLAC metadata', () => {
    it('stores duration, codec, sample rate, channels and tags on upload', async () => {
      const trackId = await createDraftTrack();
      const body = await uploaded(trackId, FLAC_BUFFER);

      // Метадані приїхали у відповіді разом з asset-ом.
      expect(body.asset.metadataStatus).toBe(MediaMetadataStatus.EXTRACTED);
      expect(body.asset.metadataError).toBeNull();
      expect(body.asset.durationSeconds).toBe(214.36);
      expect(body.asset.codec).toBe('flac');
      expect(body.asset.sampleRateHz).toBe(44100);
      expect(body.asset.channels).toBe(2);
      expect(body.asset.bitrateKbps).toBe(1014);
      expect(body.asset.tags).toEqual({
        title: 'Midnight Drive',
        artist: 'Lumi',
        album: 'Night Tapes',
      });

      // Тривалість треку взята з probe, файл у роботі конвеєра.
      expect(body.track.duration).toBe(214);
      expect(body.track.status).toBe(TrackStatus.UPLOADED);
      expect(body.job.status).toBe(ProcessingStatus.QUEUED);

      // І те саме в базі - метадані справді збережені в media record.
      const asset = await prisma.mediaAsset.findUniqueOrThrow({
        where: { id: body.asset.id },
      });

      expect(asset.durationSeconds).toBe(214.36);
      expect(asset.tags).toEqual({
        title: 'Midnight Drive',
        artist: 'Lumi',
        album: 'Night Tapes',
      });

      // Job віддає asset разом зі своїми полями.
      const jobResponse = await getJob(body.job.id).expect(200);
      const jobBody = jobResponse.body as { asset: { codec: string } };

      expect(jobBody.asset.codec).toBe('flac');
    });
  });

  describe('WAV metadata', () => {
    it('extracts PCM metadata and accepts a file without tags', async () => {
      toolchain.probe.mockResolvedValueOnce(WAV_PROBE);

      const trackId = await createDraftTrack();
      const body = await uploaded(
        trackId,
        WAV_BUFFER,
        'track.wav',
        'audio/wav',
      );

      expect(body.asset.metadataStatus).toBe(MediaMetadataStatus.EXTRACTED);
      expect(body.asset.durationSeconds).toBe(12.4);
      expect(body.asset.codec).toBe('pcm_s16le');
      expect(body.asset.sampleRateHz).toBe(48000);
      expect(body.asset.channels).toBe(1);
      expect(body.asset.bitrateKbps).toBe(768);
      // Тегів у файлі немає - у базі null, а не порожній об'єкт.
      expect(body.asset.tags).toBeNull();
      expect(body.track.duration).toBe(12);
    });
  });

  describe('corrupted audio', () => {
    it('fails the asset and the job and keeps the track in DRAFT', async () => {
      toolchain.probe.mockRejectedValueOnce(
        new Error(
          'Audio probe failed: Command failed: ffprobe -v error storage/audio/2026/10/broken.flac',
        ),
      );

      const trackId = await createDraftTrack();
      const body = await uploaded(trackId, FLAC_BUFFER);

      expect(body.asset.metadataStatus).toBe(MediaMetadataStatus.FAILED);
      expect(body.asset.metadataError).toBe(CORRUPTED_AUDIO_REASON);
      expect(body.asset.durationSeconds).toBeNull();

      // Job не чекає черги - стан уже відомий.
      expect(body.job.status).toBe(ProcessingStatus.FAILED);
      expect(body.job.failureReason).toBe(CORRUPTED_AUDIO_REASON);

      // Причина безпечна: без шляхів до файлів і без виводу ffprobe.
      expect(body.job.failureReason).not.toContain('/');
      expect(body.job.failureReason).not.toContain('\\');
      expect(body.job.failureReason).not.toContain('ffprobe');

      // Трек лишається чернеткою - Content Manager може завантажити інший файл.
      expect(body.track.status).toBe(TrackStatus.DRAFT);
      expect(body.track.audioUrl).toBeNull();

      const logs = await prisma.trackAuditLog.findMany({
        where: { trackId, action: 'MEDIA_PROBE_FAILED' },
      });

      expect(logs).toHaveLength(1);
      expect(logs[0].details).toBe(CORRUPTED_AUDIO_REASON);

      // Той самий стан видно через API статусу job-у.
      const jobResponse = await getJob(body.job.id).expect(200);
      const jobBody = jobResponse.body as {
        status: ProcessingStatus;
        failureReason: string;
        variants: unknown[];
      };

      expect(jobBody.status).toBe(ProcessingStatus.FAILED);
      expect(jobBody.failureReason).toBe(CORRUPTED_AUDIO_REASON);
      expect(jobBody.variants).toHaveLength(0);
    });

    it('does not transcode a file whose probe failed', async () => {
      // Перший виклик probe - під час upload, другий - під час ручного запуску.
      toolchain.probe
        .mockRejectedValueOnce(
          new Error('Audio probe failed: Command failed: ffprobe broken.flac'),
        )
        .mockRejectedValueOnce(
          new Error('Audio probe failed: Command failed: ffprobe broken.flac'),
        );

      const trackId = await createDraftTrack();
      const body = await uploaded(trackId, FLAC_BUFFER);

      toolchain.analyzeLoudness.mockClear();
      toolchain.normalizeToTarget.mockClear();
      toolchain.transcodeToOpus.mockClear();

      // Ручний запуск обробки: probe знову валиться - жодного транскодування.
      const runResponse = await request(app.getHttpServer())
        .post(`/api/admin/media/jobs/${body.job.id}/run`)
        .set('Authorization', `Bearer ${adminToken}`)
        .expect(201);

      const runBody = runResponse.body as {
        status: ProcessingStatus;
        failureReason: string;
        variants: unknown[];
      };

      expect(runBody.status).toBe(ProcessingStatus.FAILED);
      expect(runBody.failureReason).toBe(CORRUPTED_AUDIO_REASON);
      expect(runBody.variants).toHaveLength(0);

      expect(toolchain.analyzeLoudness).not.toHaveBeenCalled();
      expect(toolchain.normalizeToTarget).not.toHaveBeenCalled();
      expect(toolchain.transcodeToOpus).not.toHaveBeenCalled();

      expect(
        await prisma.mediaVariant.count({ where: { jobId: body.job.id } }),
      ).toBe(0);
    });
  });
});
