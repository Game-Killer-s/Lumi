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
import { randomUUID } from 'node:crypto';
import { promises as fs } from 'node:fs';
import { dirname } from 'node:path';
import request from 'supertest';

import { AppModule } from '../src/app.module';
import { setupApp } from '../src/app.setup';
import { PrismaService } from '../src/prisma/prisma.service';
import { PasswordService } from '../src/modules/auth/services/password.service';
import { AudioToolchainService } from '../src/modules/media/audio-toolchain.service';
import { MediaStorageService } from '../src/modules/media/media-storage.service';

/** Тип тіла відповіді для job-у обробки */
interface JobBody {
  id: string;
  status: ProcessingStatus;
  targetLufs: number;
  measuredLufs: number | null;
  attempts: number;
  failureReason: string | null;
  variants: {
    codec: string;
    bitrateKbps: number;
    storageKey: string;
    sizeBytes: number | null;
  }[];
}

/**
 * E2E тести обробки аудіо: нормалізація до цільового LUFS та генерація
 * Opus-варіантів 96/160/256 kbps.
 *
 * ffmpeg підмінений моком AudioToolchainService - тести не залежать від
 * наявності бінарника на машині, але перевіряють порядок викликів,
 * параметри та збережені метадані.
 */
describe('Media processing (e2e)', () => {
  let app: INestApplication;
  let prisma: PrismaService;
  let storage: MediaStorageService;

  let adminToken: string;
  let listenerToken: string;

  let artistId: string;
  let trackId: string;

  const suffix = Date.now();
  const listenerEmail = `processing-listener-${suffix}@lumi.app`;

  // Файли, які згенерував (мок) конвеєр - прибираємо в afterAll.
  const generatedFiles = new Set<string>();
  const sourceKeys: string[] = [];

  let toolchain: {
    probe: jest.Mock;
    analyzeLoudness: jest.Mock;
    normalizeToTarget: jest.Mock;
    transcodeToOpus: jest.Mock;
  };

  beforeAll(async () => {
    toolchain = {
      // Крок probe (US3): файл-джерело розпізнається як FLAC 44.1 kHz стерео.
      probe: jest.fn().mockResolvedValue({
        streams: [
          {
            codec_type: 'audio',
            codec_name: 'flac',
            sample_rate: '44100',
            channels: 2,
            bit_rate: '1014000',
            duration: '200.000000',
            tags: { title: 'Processing E2E' },
          },
        ],
        format: {
          duration: '200.000000',
          bit_rate: '1014000',
          tags: { title: 'Processing E2E', artist: 'Lumi' },
        },
      }),
      analyzeLoudness: jest
        .fn()
        .mockResolvedValue({ integratedLufs: -8.4, truePeakDb: 0.3 }),
      normalizeToTarget: jest
        .fn()
        .mockImplementation(async (_input: string, output: string) => {
          generatedFiles.add(output);
          await fs.writeFile(output, Buffer.alloc(4096, 1));
        }),
      transcodeToOpus: jest
        .fn()
        .mockImplementation(
          async (_input: string, output: string, bitrateKbps: number) => {
            generatedFiles.add(output);
            await fs.writeFile(output, Buffer.alloc(bitrateKbps * 32, 2));
          },
        ),
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
        nickname: 'Processing E2E Listener',
        role: Role.LISTENER,
      },
    });

    const artist = await prisma.artist.create({
      data: { name: `Processing E2E Artist ${suffix}` },
    });
    artistId = artist.id;

    const track = await prisma.track.create({
      data: {
        title: `Processing E2E Track ${suffix}`,
        duration: 200,
        status: TrackStatus.UPLOADED,
        artistId,
      },
    });
    trackId = track.id;

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

  // Створюємо asset із реальним файлом-джерелом і job у потрібному стані.
  async function createJob(status: ProcessingStatus = ProcessingStatus.QUEUED) {
    const storageKey = `audio/e2e/${randomUUID()}.flac`;
    const sourcePath = storage.absolutePath(storageKey);

    await fs.mkdir(dirname(sourcePath), { recursive: true });
    await fs.writeFile(sourcePath, Buffer.alloc(2048, 5));
    sourceKeys.push(storageKey);

    const asset = await prisma.mediaAsset.create({
      data: {
        trackId,
        kind: MediaAssetKind.AUDIO,
        storageKey,
        originalName: 'source.flac',
        mimeType: 'audio/flac',
        extension: '.flac',
        sizeBytes: 2048,
        checksum: `e2e-${randomUUID()}`,
        scanStatus: MediaScanStatus.CLEAN,
      },
    });

    const job = await prisma.mediaProcessingJob.create({
      data: { assetId: asset.id, status, targetLufs: -14 },
    });

    return { job, asset, sourcePath };
  }

  function runJob(jobId: string, token: string = adminToken) {
    return request(app.getHttpServer())
      .post(`/api/admin/media/jobs/${jobId}/run`)
      .set('Authorization', `Bearer ${token}`);
  }

  function retryJob(jobId: string, token: string = adminToken) {
    return request(app.getHttpServer())
      .post(`/api/admin/media/jobs/${jobId}/retry`)
      .set('Authorization', `Bearer ${token}`);
  }

  function getJob(jobId: string, token: string = adminToken) {
    return request(app.getHttpServer())
      .get(`/api/admin/media/jobs/${jobId}`)
      .set('Authorization', `Bearer ${token}`);
  }

  afterAll(async () => {
    for (const file of generatedFiles) {
      await fs.rm(file, { force: true });
    }

    for (const key of sourceKeys) {
      await fs.rm(storage.absolutePath(key), { force: true });
    }

    if (prisma) {
      // Каскадом видаляться asset-и, job-и та варіанти.
      await prisma.track.deleteMany({ where: { artistId } });
      await prisma.artist.deleteMany({ where: { id: artistId } });
      await prisma.user.deleteMany({ where: { email: listenerEmail } });
    }

    if (app) {
      await app.close();
    }
  });

  describe('access control', () => {
    it('rejects anonymous access', async () => {
      const { job } = await createJob();

      await request(app.getHttpServer())
        .get(`/api/admin/media/jobs/${job.id}`)
        .expect(401);

      await request(app.getHttpServer())
        .post(`/api/admin/media/jobs/${job.id}/run`)
        .expect(401);
    });

    it('rejects a listener', async () => {
      const { job } = await createJob();

      await getJob(job.id, listenerToken).expect(403);
      await runJob(job.id, listenerToken).expect(403);
    });

    it('returns 404 for an unknown job', async () => {
      await getJob('00000000-0000-0000-0000-000000000000').expect(404);
    });
  });

  describe('normalization and Opus transcoding', () => {
    it('normalizes to the target LUFS and produces three Opus variants', async () => {
      const { job, asset: sourceAsset, sourcePath } = await createJob();

      const response = await runJob(job.id).expect(201);
      const body = response.body as JobBody;

      expect(body.status).toBe(ProcessingStatus.READY);
      expect(body.attempts).toBe(1);
      expect(body.failureReason).toBeNull();
      expect(body.measuredLufs).toBe(-8.4);

      // 0. Крок probe: метадані файлу-джерела збережені на asset (US3).
      expect(toolchain.probe).toHaveBeenCalledWith(sourcePath);

      const storedAsset = await prisma.mediaAsset.findUniqueOrThrow({
        where: { id: sourceAsset.id },
      });

      expect(storedAsset.metadataStatus).toBe(MediaMetadataStatus.EXTRACTED);
      expect(storedAsset.codec).toBe('flac');
      expect(storedAsset.durationSeconds).toBe(200);
      expect(storedAsset.sampleRateHz).toBe(44100);
      expect(storedAsset.channels).toBe(2);

      // 1. Аналіз гучності вихідного файлу.
      expect(toolchain.analyzeLoudness).toHaveBeenCalledWith(sourcePath);

      // 2. Нормалізація до цільового LUFS (з job-у).
      const [, normalizedPath, targetLufs] =
        toolchain.normalizeToTarget.mock.calls[0];

      expect(targetLufs).toBe(job.targetLufs);
      expect(targetLufs).toBe(-14);

      // Нормалізований файл справді створено.
      expect((await fs.stat(normalizedPath as string)).size).toBeGreaterThan(0);

      // 3. Opus 96 / 160 / 256 kbps - саме з нормалізованого файлу.
      const bitrates = toolchain.transcodeToOpus.mock.calls.map(
        (call: unknown[]) => call[2],
      );

      expect(bitrates).toEqual([96, 160, 256]);
      expect(
        toolchain.transcodeToOpus.mock.calls.every(
          (call: unknown[]) => call[0] === normalizedPath,
        ),
      ).toBe(true);

      // 4. Метадані варіантів збережено з розмірами файлів.
      expect(body.variants.map((variant) => variant.bitrateKbps)).toEqual([
        96, 160, 256,
      ]);
      expect(body.variants.every((variant) => variant.codec === 'opus')).toBe(
        true,
      );
      expect(
        body.variants.every((variant) => (variant.sizeBytes ?? 0) > 0),
      ).toBe(true);

      const stored = await prisma.mediaVariant.findMany({
        where: { jobId: job.id },
        orderBy: { bitrateKbps: 'asc' },
      });

      expect(stored).toHaveLength(3);
      expect(stored.map((variant) => variant.sizeBytes)).toEqual([
        96 * 32,
        160 * 32,
        256 * 32,
      ]);

      expect(
        stored.every((variant) => variant.storageKey.endsWith('.opus')),
      ).toBe(true);
    });

    it('exposes the finished job with its variants', async () => {
      const { job } = await createJob();

      await runJob(job.id).expect(201);

      const response = await getJob(job.id).expect(200);
      const body = response.body as JobBody & { asset: { id: string } };

      expect(body.status).toBe(ProcessingStatus.READY);
      expect(body.asset.id).toBeDefined();
      expect(body.variants.map((variant) => variant.bitrateKbps)).toEqual([
        96, 160, 256,
      ]);
    });

    it('refuses to run a job that is already processing', async () => {
      const { job } = await createJob(ProcessingStatus.PROCESSING);

      await runJob(job.id).expect(400);
    });
  });

  describe('failure and retry', () => {
    it('marks a failed run as FAILED and then lets it be retried', async () => {
      const { job } = await createJob();

      toolchain.normalizeToTarget.mockImplementationOnce(() =>
        Promise.reject(new Error('ffmpeg is not installed')),
      );

      const failed = await runJob(job.id).expect(201);
      const failedBody = failed.body as JobBody;

      expect(failedBody.status).toBe(ProcessingStatus.FAILED);
      expect(failedBody.failureReason).toContain('ffmpeg is not installed');
      expect(failedBody.attempts).toBe(1);
      expect(failedBody.variants).toHaveLength(0);

      const storedFailed = await prisma.mediaProcessingJob.findUniqueOrThrow({
        where: { id: job.id },
      });

      expect(storedFailed.status).toBe(ProcessingStatus.FAILED);
      expect(
        await prisma.mediaVariant.count({ where: { jobId: job.id } }),
      ).toBe(0);

      // Повторний запуск після FAILED проходить конвеєр заново.
      const retried = await retryJob(job.id).expect(201);
      const retriedBody = retried.body as JobBody;

      expect(retriedBody.status).toBe(ProcessingStatus.READY);
      expect(retriedBody.attempts).toBe(2);
      expect(retriedBody.failureReason).toBeNull();
      expect(
        retriedBody.variants.map((variant) => variant.bitrateKbps),
      ).toEqual([96, 160, 256]);
    });

    it('refuses to retry a job that is not failed', async () => {
      const { job } = await createJob();

      await retryJob(job.id).expect(400);

      expect(
        await prisma.mediaVariant.count({ where: { jobId: job.id } }),
      ).toBe(0);
    });

    it('returns 404 when retrying an unknown job', async () => {
      await retryJob('00000000-0000-0000-0000-000000000000').expect(404);
    });
  });
});
