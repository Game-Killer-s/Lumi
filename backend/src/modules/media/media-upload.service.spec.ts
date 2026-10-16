import { BadRequestException, NotFoundException } from '@nestjs/common';
import { TrackStatus } from '@prisma/client';

import { PrismaService } from '../../prisma/prisma.service';
import { CatalogCacheService } from '../catalog/catalog-cache.service';
import { MediaMetadataService } from './media-metadata.service';
import { MediaProcessingJobService } from './media-processing-job.service';
import { MediaSecurityService } from './media-security.service';
import { MediaStorageService } from './media-storage.service';
import { MediaUploadService } from './media-upload.service';

describe('MediaUploadService', () => {
  let service: MediaUploadService;
  let prisma: any;
  let storage: any;
  let security: any;
  let jobs: any;
  let metadata: any;
  let cache: any;
  let tx: any;

  const trackId = 'track-1';
  const actorId = 'user-1';

  const draftTrack = { id: trackId, title: 'Old', status: TrackStatus.DRAFT };
  const publishedTrack = {
    id: trackId,
    title: 'Old',
    status: TrackStatus.PUBLISHED,
  };

  const audioFile = {
    originalname: 'my song.flac',
    mimetype: 'audio/flac',
    size: 4096,
    path: '/tmp/lumi-media-uploads/upload-1',
  };

  const coverFile = {
    originalname: 'cover.png',
    mimetype: 'image/png',
    size: 1024,
    path: '/tmp/lumi-media-uploads/upload-2',
  };

  beforeEach(() => {
    tx = {
      mediaAsset: {
        create: jest.fn().mockImplementation(({ data }) => ({
          id: 'asset-1',
          ...data,
        })),
      },
      mediaProcessingJob: {
        create: jest.fn().mockImplementation(({ data }) => ({
          id: 'job-1',
          ...data,
        })),
      },
      track: {
        update: jest
          .fn()
          .mockImplementation(({ data }) => ({ id: trackId, ...data })),
      },
      trackAuditLog: {
        create: jest.fn().mockResolvedValue({ id: 'log-1' }),
      },
    };

    prisma = {
      track: { findUnique: jest.fn().mockResolvedValue(draftTrack) },
      transaction: jest
        .fn()
        .mockImplementation((callback: (client: unknown) => unknown) =>
          callback(tx),
        ),
    };

    storage = {
      buildStorageKey: jest
        .fn()
        .mockReturnValue('audio/2026/10/generated.flac'),
      moveIntoStorage: jest.fn().mockResolvedValue(undefined),
      remove: jest.fn().mockResolvedValue(undefined),
      removeTemp: jest.fn().mockResolvedValue(undefined),
      checksum: jest.fn().mockResolvedValue('sha256-value'),
    };

    security = { scan: jest.fn().mockResolvedValue({ clean: true }) };

    jobs = {
      buildQueuedJobData: jest.fn().mockReturnValue({
        assetId: 'asset-1',
        targetLufs: -14,
        status: 'QUEUED',
      }),
      buildFailedJobData: jest.fn().mockImplementation((assetId, reason) => ({
        assetId,
        targetLufs: -14,
        status: 'FAILED',
        failureReason: reason,
      })),
    };

    // probe за замовчуванням успішний - ffprobe підмінений.
    metadata = {
      inspect: jest.fn().mockResolvedValue({
        ok: true,
        metadata: {
          durationSeconds: 214.36,
          codec: 'flac',
          sampleRateHz: 44100,
          channels: 2,
          bitrateKbps: 1014,
          tags: { title: 'Midnight Drive', artist: 'Lumi' },
        },
      }),
    };

    cache = { invalidateCatalog: jest.fn() };

    service = new MediaUploadService(
      prisma as PrismaService,
      storage as MediaStorageService,
      security as MediaSecurityService,
      jobs as MediaProcessingJobService,
      metadata as MediaMetadataService,
      cache as CatalogCacheService,
    );
  });

  describe('audio upload', () => {
    it('runs the security pre-check before storing anything', async () => {
      await service.uploadAudio(trackId, audioFile, {}, actorId);

      expect(security.scan).toHaveBeenCalledWith(audioFile.path, 'AUDIO');

      const scanOrder = security.scan.mock.invocationCallOrder[0];
      const moveOrder = storage.moveIntoStorage.mock.invocationCallOrder[0];

      expect(scanOrder).toBeLessThan(moveOrder);
    });

    it('stores the file under a generated key instead of the client name', async () => {
      await service.uploadAudio(trackId, audioFile, {}, actorId);

      expect(storage.buildStorageKey).toHaveBeenCalledWith('AUDIO', '.flac');
      expect(storage.moveIntoStorage).toHaveBeenCalledWith(
        audioFile.path,
        'audio/2026/10/generated.flac',
      );

      const assetData = tx.mediaAsset.create.mock.calls[0][0].data;

      expect(assetData.storageKey).toBe('audio/2026/10/generated.flac');
      expect(assetData.storageKey).not.toContain('my song');
      expect(assetData.kind).toBe('AUDIO');
      expect(assetData.scanStatus).toBe('CLEAN');
      // оригінальна назва лишається тільки як мета.
      expect(assetData.originalName).toBe('my song.flac');
    });

    it('creates the asset, queue job, track update and audit log atomically', async () => {
      await service.uploadAudio(
        trackId,
        audioFile,
        { title: 'New Title', duration: 214, lyrics: 'la la' },
        actorId,
      );

      expect(prisma.transaction).toHaveBeenCalledTimes(1);

      expect(tx.mediaProcessingJob.create).toHaveBeenCalledWith({
        data: jobs.buildQueuedJobData.mock.results[0].value,
      });

      expect(tx.track.update).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: trackId },
          data: expect.objectContaining({
            status: TrackStatus.UPLOADED,
            audioUrl: '/media/audio/2026/10/generated.flac',
            title: 'New Title',
            duration: 214,
            lyrics: 'la la',
          }),
        }),
      );

      expect(tx.trackAuditLog.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            actorId,
            action: 'MEDIA_UPLOADED:DRAFT->UPLOADED',
          }),
        }),
      );

      expect(cache.invalidateCatalog).toHaveBeenCalled();
    });

    it('removes the stored file when the database write fails', async () => {
      prisma.transaction.mockRejectedValue(new Error('db is down'));

      await expect(
        service.uploadAudio(trackId, audioFile, {}, actorId),
      ).rejects.toThrow('db is down');

      expect(storage.remove).toHaveBeenCalledWith(
        'audio/2026/10/generated.flac',
      );
      expect(cache.invalidateCatalog).not.toHaveBeenCalled();
    });
  });

  describe('audio metadata (US3)', () => {
    it('stores the probed metadata on the asset record', async () => {
      await service.uploadAudio(trackId, audioFile, {}, actorId);

      expect(metadata.inspect).toHaveBeenCalledWith(
        'audio/2026/10/generated.flac',
      );

      const assetData = tx.mediaAsset.create.mock.calls[0][0].data;

      expect(assetData).toEqual(
        expect.objectContaining({
          metadataStatus: 'EXTRACTED',
          metadataError: null,
          durationSeconds: 214.36,
          codec: 'flac',
          sampleRateHz: 44100,
          channels: 2,
          bitrateKbps: 1014,
          tags: { title: 'Midnight Drive', artist: 'Lumi' },
        }),
      );

      // Трек отримує тривалість з probe, якщо її не вказали у формі.
      expect(tx.track.update).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: trackId },
          data: expect.objectContaining({ duration: 214 }),
        }),
      );
    });

    it('prefers the duration sent in the multipart form', async () => {
      metadata.inspect.mockResolvedValue({
        ok: true,
        metadata: {
          durationSeconds: 100.7,
          codec: 'flac',
          sampleRateHz: 48000,
          channels: 1,
          bitrateKbps: 900,
          tags: null,
        },
      });

      await service.uploadAudio(trackId, audioFile, { duration: 214 }, actorId);

      expect(tx.track.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({ duration: 214 }),
        }),
      );
    });

    it('keeps the track in DRAFT and fails the job for a corrupted file', async () => {
      const reason =
        'Audio file could not be read: it may be corrupted or truncated';

      metadata.inspect.mockResolvedValue({ ok: false, reason });

      const result = await service.uploadAudio(trackId, audioFile, {}, actorId);

      // Файл лишається у сховищі для розбору, але asset і job - FAILED.
      expect(storage.remove).not.toHaveBeenCalled();

      expect(tx.mediaAsset.create.mock.calls[0][0].data).toEqual(
        expect.objectContaining({
          metadataStatus: 'FAILED',
          metadataError: reason,
        }),
      );

      expect(jobs.buildQueuedJobData).not.toHaveBeenCalled();
      expect(jobs.buildFailedJobData).toHaveBeenCalledWith('asset-1', reason);

      // Трек не публікуємо у конвеєр: можна завантажити інший файл.
      expect(tx.track.update).not.toHaveBeenCalled();
      expect(result.track).toBe(draftTrack);

      expect(tx.trackAuditLog.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            action: 'MEDIA_PROBE_FAILED',
            details: reason,
          }),
        }),
      );

      expect(cache.invalidateCatalog).toHaveBeenCalled();
    });
  });

  describe('rejections', () => {
    it('rejects an upload without a file', async () => {
      await expect(
        service.uploadAudio(trackId, undefined, {}, actorId),
      ).rejects.toThrow(BadRequestException);

      expect(security.scan).not.toHaveBeenCalled();
    });

    it('rejects an unsupported audio format', async () => {
      await expect(
        service.uploadAudio(
          trackId,
          { ...audioFile, originalname: 'song.mp3', mimetype: 'audio/mpeg' },
          {},
          actorId,
        ),
      ).rejects.toThrow(BadRequestException);

      expect(security.scan).not.toHaveBeenCalled();
      expect(prisma.transaction).not.toHaveBeenCalled();
    });

    it('rejects a file that failed the antivirus pre-check', async () => {
      security.scan.mockResolvedValue({
        clean: false,
        reason: 'Antivirus signature detected: EICAR',
      });

      await expect(
        service.uploadAudio(trackId, audioFile, {}, actorId),
      ).rejects.toThrow(/Security pre-check failed/);

      expect(storage.removeTemp).toHaveBeenCalledWith(audioFile.path);
      expect(storage.moveIntoStorage).not.toHaveBeenCalled();
      expect(prisma.transaction).not.toHaveBeenCalled();
    });

    it('rejects audio for a track that is not a draft', async () => {
      prisma.track.findUnique.mockResolvedValue(publishedTrack);

      await expect(
        service.uploadAudio(trackId, audioFile, {}, actorId),
      ).rejects.toThrow(/DRAFT track/);

      expect(security.scan).not.toHaveBeenCalled();
    });

    it('throws not found for an unknown track', async () => {
      prisma.track.findUnique.mockResolvedValue(null);

      await expect(
        service.uploadAudio(trackId, audioFile, {}, actorId),
      ).rejects.toThrow(NotFoundException);
    });
  });

  describe('cover upload', () => {
    it('sets coverUrl without creating a processing job', async () => {
      prisma.track.findUnique.mockResolvedValue(publishedTrack);
      storage.buildStorageKey.mockReturnValue('cover/2026/10/generated.jpg');

      await service.uploadCover(
        trackId,
        coverFile,
        { title: 'Front cover' },
        actorId,
      );

      expect(storage.buildStorageKey).toHaveBeenCalledWith('COVER', '.png');

      expect(tx.track.update).toHaveBeenCalledWith({
        where: { id: trackId },
        data: { coverUrl: '/media/cover/2026/10/generated.jpg' },
      });

      expect(tx.mediaProcessingJob.create).not.toHaveBeenCalled();
      expect(cache.invalidateCatalog).toHaveBeenCalled();
    });

    it('rejects a cover in an unsupported format', async () => {
      await expect(
        service.uploadCover(
          trackId,
          { ...coverFile, originalname: 'cover.gif', mimetype: 'image/gif' },
          {},
          actorId,
        ),
      ).rejects.toThrow(BadRequestException);

      expect(security.scan).not.toHaveBeenCalled();
    });
  });
});
