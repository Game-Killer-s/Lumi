import { BadRequestException, NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ProcessingStatus } from '@prisma/client';

import { PrismaService } from '../../prisma/prisma.service';
import { AudioToolchainService } from './audio-toolchain.service';
import { MediaMetadataService } from './media-metadata.service';
import { MediaStorageService } from './media-storage.service';
import {
  DEFAULT_TARGET_LUFS,
  MediaProcessingJobService,
  OPUS_BITRATES,
} from './media-processing-job.service';

describe('MediaProcessingJobService', () => {
  let service: MediaProcessingJobService;
  let prisma: any;
  let storage: any;
  let toolchain: any;
  let metadata: any;

  const jobId = 'job-1';
  const assetId = 'asset-1';
  const sourceStorageKey = 'audio/2026/10/source.flac';

  // Метадані, які повертає probe (ffprobe підмінений разом із сервісом).
  const extractedMetadata = {
    durationSeconds: 214.36,
    codec: 'flac',
    sampleRateHz: 44100,
    channels: 2,
    bitrateKbps: 1014,
    tags: { title: 'Source' },
  };

  const job = {
    id: jobId,
    assetId,
    status: ProcessingStatus.QUEUED,
    attempts: 0,
    targetLufs: -14,
    failureReason: null,
    asset: { id: assetId, storageKey: sourceStorageKey },
  };

  let keyCounter = 0;

  function createService(targetLufs: number | undefined = -14) {
    const configService = { get: jest.fn().mockReturnValue(targetLufs) };

    return new MediaProcessingJobService(
      prisma as PrismaService,
      storage as MediaStorageService,
      toolchain as AudioToolchainService,
      metadata as MediaMetadataService,
      configService as unknown as ConfigService,
    );
  }

  beforeEach(() => {
    keyCounter = 0;

    prisma = {
      mediaProcessingJob: {
        findUnique: jest.fn().mockResolvedValue(job),
        create: jest
          .fn()
          .mockImplementation(({ data }) => ({ id: jobId, ...data })),
        update: jest
          .fn()
          .mockImplementation(({ data }) => ({ id: jobId, ...data })),
      },
      mediaVariant: {
        deleteMany: jest.fn().mockResolvedValue({ count: 0 }),
        createMany: jest
          .fn()
          .mockResolvedValue({ count: OPUS_BITRATES.length }),
      },
      // Probe одразу пише метадані у запис asset.
      mediaAsset: {
        update: jest
          .fn()
          .mockImplementation(({ data }) => ({ id: assetId, ...data })),
      },
    };

    storage = {
      absolutePath: jest
        .fn()
        .mockImplementation((key: string) => `/storage/${key}`),
      buildStorageKey: jest
        .fn()
        .mockImplementation(
          (kind: string, extension: string) =>
            `${kind.toLowerCase()}/generated-${++keyCounter}${extension}`,
        ),
      ensureDirFor: jest.fn().mockResolvedValue(undefined),
      statSize: jest.fn().mockResolvedValue(4096),
    };

    // ffmpeg на машині розробника може бути відсутній - тому весь
    // зовнішній інструментарій тут підмінений.
    toolchain = {
      analyzeLoudness: jest
        .fn()
        .mockResolvedValue({ integratedLufs: -8.7, truePeakDb: 0.5 }),
      normalizeToTarget: jest.fn().mockResolvedValue(undefined),
      transcodeToOpus: jest.fn().mockResolvedValue(undefined),
    };

    metadata = {
      inspect: jest.fn().mockResolvedValue({
        ok: true,
        metadata: extractedMetadata,
      }),
    };

    service = createService();
  });

  describe('queue', () => {
    it('builds queued job data with the configured target LUFS', () => {
      expect(service.buildQueuedJobData(assetId)).toEqual({
        assetId,
        targetLufs: -14,
        status: ProcessingStatus.QUEUED,
      });
    });

    it('falls back to the default target when the config is empty', () => {
      const fallbackService = createService(undefined);

      expect(fallbackService.buildQueuedJobData(assetId).targetLufs).toBe(
        DEFAULT_TARGET_LUFS,
      );
    });

    it('stores a new job in QUEUED state', async () => {
      await service.enqueue(assetId);

      expect(prisma.mediaProcessingJob.create).toHaveBeenCalledWith({
        data: {
          assetId,
          targetLufs: -14,
          status: ProcessingStatus.QUEUED,
        },
      });
    });
  });

  describe('getStatus', () => {
    it('returns the job together with its asset and variants', async () => {
      await service.getStatus(jobId);

      expect(prisma.mediaProcessingJob.findUnique).toHaveBeenCalledWith({
        where: { id: jobId },
        include: {
          asset: true,
          variants: { orderBy: { bitrateKbps: 'asc' } },
        },
      });
    });

    it('throws not found for an unknown job', async () => {
      prisma.mediaProcessingJob.findUnique.mockResolvedValue(null);

      await expect(service.getStatus('nope')).rejects.toThrow(
        NotFoundException,
      );
    });
  });

  describe('run', () => {
    it('moves the job to PROCESSING and counts the attempt', async () => {
      await service.run(jobId);

      expect(prisma.mediaProcessingJob.update).toHaveBeenNthCalledWith(1, {
        where: { id: jobId },
        data: {
          status: ProcessingStatus.PROCESSING,
          attempts: { increment: 1 },
          failureReason: null,
        },
      });
    });

    it('normalizes to the target LUFS before transcoding', async () => {
      await service.run(jobId);

      expect(toolchain.analyzeLoudness).toHaveBeenCalledWith(
        `/storage/${sourceStorageKey}`,
      );

      expect(toolchain.normalizeToTarget).toHaveBeenCalledWith(
        `/storage/${sourceStorageKey}`,
        '/storage/audio/generated-1.flac',
        -14,
      );

      // транскодування ведеться вже з нормалізованого файлу
      expect(toolchain.transcodeToOpus).toHaveBeenCalledWith(
        '/storage/audio/generated-1.flac',
        '/storage/audio/generated-2.opus',
        96,
      );

      expect(storage.ensureDirFor).toHaveBeenCalledWith(
        'audio/generated-1.flac',
      );
    });

    it('produces Opus variants for 96, 160 and 256 kbps', async () => {
      await service.run(jobId);

      expect(toolchain.transcodeToOpus).toHaveBeenCalledTimes(
        OPUS_BITRATES.length,
      );

      const bitrates = toolchain.transcodeToOpus.mock.calls.map(
        (call: unknown[]) => call[2],
      );

      expect(bitrates).toEqual([96, 160, 256]);

      const variants = prisma.mediaVariant.createMany.mock.calls[0][0].data;

      expect(variants).toHaveLength(3);
      expect(variants.map((variant: any) => variant.bitrateKbps)).toEqual([
        96, 160, 256,
      ]);
      expect(
        variants.every(
          (variant: any) =>
            variant.codec === 'opus' &&
            variant.jobId === jobId &&
            variant.sizeBytes === 4096,
        ),
      ).toBe(true);
    });

    it('replaces previous variants so a retry does not duplicate them', async () => {
      await service.run(jobId);

      expect(prisma.mediaVariant.deleteMany).toHaveBeenCalledWith({
        where: { jobId },
      });

      const deleteOrder =
        prisma.mediaVariant.deleteMany.mock.invocationCallOrder[0];
      const createOrder =
        prisma.mediaVariant.createMany.mock.invocationCallOrder[0];

      expect(deleteOrder).toBeLessThan(createOrder);
    });

    it('marks the job READY with the measured loudness', async () => {
      const result = await service.run(jobId);

      expect(prisma.mediaProcessingJob.update).toHaveBeenNthCalledWith(
        2,
        expect.objectContaining({
          where: { id: jobId },
          data: expect.objectContaining({
            status: ProcessingStatus.READY,
            measuredLufs: -8.7,
            failureReason: null,
          }),
        }),
      );

      expect(result.status).toBe(ProcessingStatus.READY);
    });

    it('rejects a job that is already being processed', async () => {
      prisma.mediaProcessingJob.findUnique.mockResolvedValue({
        ...job,
        status: ProcessingStatus.PROCESSING,
      });

      await expect(service.run(jobId)).rejects.toThrow(BadRequestException);
      expect(prisma.mediaProcessingJob.update).not.toHaveBeenCalled();
    });

    it('throws not found for an unknown job', async () => {
      prisma.mediaProcessingJob.findUnique.mockResolvedValue(null);

      await expect(service.run('nope')).rejects.toThrow(NotFoundException);
    });
  });

  describe('metadata probe (US3)', () => {
    it('stores the extracted metadata on the asset before transcoding', async () => {
      await service.run(jobId);

      expect(metadata.inspect).toHaveBeenCalledWith(sourceStorageKey);

      expect(prisma.mediaAsset.update).toHaveBeenCalledWith({
        where: { id: assetId },
        data: expect.objectContaining({
          metadataStatus: 'EXTRACTED',
          metadataError: null,
          durationSeconds: 214.36,
          codec: 'flac',
          sampleRateHz: 44100,
          channels: 2,
        }),
      });
    });

    it('validates the probe result before normalization and transcoding', async () => {
      await service.run(jobId);

      const probeOrder = metadata.inspect.mock.invocationCallOrder[0];
      const loudnessOrder =
        toolchain.analyzeLoudness.mock.invocationCallOrder[0];
      const transcodeOrder =
        toolchain.transcodeToOpus.mock.invocationCallOrder[0];

      expect(probeOrder).toBeLessThan(loudnessOrder);
      expect(loudnessOrder).toBeLessThan(transcodeOrder);
    });

    it('fails the job with a safe reason when the file cannot be probed', async () => {
      metadata.inspect.mockResolvedValue({
        ok: false,
        reason:
          'Audio file could not be read: it may be corrupted or truncated',
      });

      const result = await service.run(jobId);

      expect(result.status).toBe(ProcessingStatus.FAILED);
      expect(result.failureReason).toBe(
        'Audio file could not be read: it may be corrupted or truncated',
      );

      // Пошкоджений файл не доходить ні до нормалізації, ні до Opus.
      expect(toolchain.analyzeLoudness).not.toHaveBeenCalled();
      expect(toolchain.normalizeToTarget).not.toHaveBeenCalled();
      expect(toolchain.transcodeToOpus).not.toHaveBeenCalled();
      expect(prisma.mediaVariant.createMany).not.toHaveBeenCalled();

      expect(prisma.mediaAsset.update).toHaveBeenCalledWith({
        where: { id: assetId },
        data: {
          metadataStatus: 'FAILED',
          metadataError:
            'Audio file could not be read: it may be corrupted or truncated',
        },
      });
    });
  });

  describe('failure handling', () => {
    it('marks the job FAILED instead of throwing when ffmpeg fails', async () => {
      toolchain.normalizeToTarget.mockRejectedValue(
        new Error('Media processing failed: ffmpeg is not installed'),
      );

      const result = await service.run(jobId);

      expect(result.status).toBe(ProcessingStatus.FAILED);
      expect(result.failureReason).toContain('ffmpeg is not installed');

      expect(prisma.mediaProcessingJob.update).toHaveBeenNthCalledWith(
        2,
        expect.objectContaining({
          where: { id: jobId },
          data: expect.objectContaining({
            status: ProcessingStatus.FAILED,
            failureReason: expect.stringContaining('ffmpeg is not installed'),
          }),
        }),
      );

      expect(prisma.mediaVariant.createMany).not.toHaveBeenCalled();
    });

    it('does not transcode when the loudness analysis fails', async () => {
      toolchain.analyzeLoudness.mockRejectedValue(
        new Error('Media processing failed: cannot read source'),
      );

      const result = await service.run(jobId);

      expect(result.status).toBe(ProcessingStatus.FAILED);
      expect(toolchain.normalizeToTarget).not.toHaveBeenCalled();
      expect(toolchain.transcodeToOpus).not.toHaveBeenCalled();
    });
  });

  describe('retry', () => {
    it('requeues a FAILED job and processes it again', async () => {
      prisma.mediaProcessingJob.findUnique.mockResolvedValue({
        ...job,
        status: ProcessingStatus.FAILED,
        failureReason: 'Media processing failed: boom',
        attempts: 1,
      });

      const result = await service.retry(jobId);

      expect(prisma.mediaProcessingJob.update).toHaveBeenNthCalledWith(1, {
        where: { id: jobId },
        data: { status: ProcessingStatus.QUEUED, failureReason: null },
      });

      expect(result.status).toBe(ProcessingStatus.READY);
      expect(prisma.mediaProcessingJob.update).toHaveBeenCalledTimes(3);
      expect(toolchain.normalizeToTarget).toHaveBeenCalledTimes(1);
    });

    it('refuses to retry a job that is not FAILED', async () => {
      prisma.mediaProcessingJob.findUnique.mockResolvedValue({
        ...job,
        status: ProcessingStatus.READY,
      });

      await expect(service.retry(jobId)).rejects.toThrow(BadRequestException);
      expect(toolchain.analyzeLoudness).not.toHaveBeenCalled();
    });

    it('throws not found for an unknown job', async () => {
      prisma.mediaProcessingJob.findUnique.mockResolvedValue(null);

      await expect(service.retry('nope')).rejects.toThrow(NotFoundException);
    });
  });
});
