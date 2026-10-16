import {
  BadRequestException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { MediaAssetKind, MediaScanStatus, TrackStatus } from '@prisma/client';

import { PrismaService } from '../../prisma/prisma.service';
import { CatalogCacheService } from '../catalog/catalog-cache.service';
import { UploadAudioDto } from './dto/upload-audio.dto';
import { UploadCoverDto } from './dto/upload-cover.dto';
import {
  extensionForMedia,
  mediaPublicUrl,
  MediaKind,
  UploadedMediaFile,
} from './media.constants';
import { MediaProcessingJobService } from './media-processing-job.service';
import {
  assetMetadataFields,
  MediaMetadataService,
} from './media-metadata.service';
import { MediaSecurityService } from './media-security.service';
import { MediaStorageService } from './media-storage.service';

interface StoredFile {
  storageKey: string;
  checksum: string;
  extension: string;
  sizeBytes: number;
  mimeType: string;
  originalName: string;
}

/**
 * Завантаження аудіо та обкладинок (multipart).
 *
 * Порядок: перевірка формату -> security pre-check -> безпечний storage key
 * -> audio probe (метадані) -> атомарний запис (asset + processing job +
 * оновлення треку + audit log). Якщо запис у базу не вдався - файл зі
 * сховища прибираємо.
 */
@Injectable()
export class MediaUploadService {
  private readonly logger = new Logger(MediaUploadService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly storage: MediaStorageService,
    private readonly security: MediaSecurityService,
    private readonly jobs: MediaProcessingJobService,
    private readonly metadata: MediaMetadataService,
    private readonly cache: CatalogCacheService,
  ) {}

  // multipart: завантаження аудіо для чернетки треку.
  async uploadAudio(
    trackId: string,
    file: UploadedMediaFile | undefined,
    dto: UploadAudioDto,
    actorId: string,
  ) {
    const track = await this.findTrack(trackId);

    // Аудіо чіпляємо тільки до чернетки: DRAFT -> UPLOADED (як у життєвому циклі).
    if (track.status !== TrackStatus.DRAFT) {
      throw new BadRequestException(
        'Audio can be uploaded only for a DRAFT track',
      );
    }

    const stored = await this.storeFile(trackId, 'AUDIO', file);

    // Probe одразу після upload: тривалість, кодек, sample rate, канали й
    // embedded tags їдуть у той самий запис asset. Пошкоджений файл не
    // потрапляє у нормалізацію - job створюється вже FAILED, а трек
    // лишається чернеткою, щоб можна було завантажити інший файл.
    const inspection = await this.metadata.inspect(stored.storageKey);

    try {
      const result = await this.prisma.transaction(async (tx) => {
        const asset = await tx.mediaAsset.create({
          data: {
            trackId,
            kind: MediaAssetKind.AUDIO,
            title: dto.title ?? null,
            scanStatus: MediaScanStatus.CLEAN,
            ...this.assetFileFields(stored),
            ...assetMetadataFields(inspection),
          },
        });

        // Job у черзі - обробка запускається окремо. Нерозпізнаний файл
        // одразу отримує FAILED з причиною для Content Manager.
        const job = await tx.mediaProcessingJob.create({
          data: inspection.ok
            ? this.jobs.buildQueuedJobData(asset.id)
            : this.jobs.buildFailedJobData(asset.id, inspection.reason),
        });

        const updatedTrack = inspection.ok
          ? await tx.track.update({
              where: { id: trackId },
              data: {
                audioUrl: mediaPublicUrl(asset.storageKey),
                status: TrackStatus.UPLOADED,
                ...(dto.title ? { title: dto.title } : {}),
                // Якщо Content Manager не вказав тривалість вручну -
                // беремо її з probe.
                duration:
                  dto.duration ??
                  Math.round(inspection.metadata.durationSeconds),
                ...(dto.lyrics !== undefined ? { lyrics: dto.lyrics } : {}),
              },
            })
          : track;

        await tx.trackAuditLog.create({
          data: {
            trackId,
            actorId,
            action: inspection.ok
              ? `MEDIA_UPLOADED:${TrackStatus.DRAFT}->${TrackStatus.UPLOADED}`
              : 'MEDIA_PROBE_FAILED',
            details: inspection.ok ? stored.originalName : inspection.reason,
          },
        });

        return { track: updatedTrack, asset, job };
      });

      this.cache.invalidateCatalog();

      return result;
    } catch (error) {
      await this.storage.remove(stored.storageKey);

      throw error;
    }
  }

  // multipart: завантаження обкладинки (трек може бути в будь-якому статусі).
  async uploadCover(
    trackId: string,
    file: UploadedMediaFile | undefined,
    dto: UploadCoverDto,
    actorId: string,
  ) {
    const track = await this.findTrack(trackId);
    const stored = await this.storeFile(trackId, 'COVER', file);

    try {
      const result = await this.prisma.transaction(async (tx) => {
        const asset = await tx.mediaAsset.create({
          data: {
            trackId,
            kind: MediaAssetKind.COVER,
            title: dto.title ?? null,
            scanStatus: MediaScanStatus.CLEAN,
            ...this.assetFileFields(stored),
          },
        });

        const updatedTrack = await tx.track.update({
          where: { id: trackId },
          data: { coverUrl: mediaPublicUrl(asset.storageKey) },
        });

        await tx.trackAuditLog.create({
          data: {
            trackId,
            actorId,
            action: `MEDIA_UPLOADED:${track.status}->${track.status}`,
            details: stored.originalName,
          },
        });

        return { track: updatedTrack, asset };
      });

      this.cache.invalidateCatalog();

      return result;
    } catch (error) {
      await this.storage.remove(stored.storageKey);

      throw error;
    }
  }

  private assetFileFields(stored: StoredFile) {
    return {
      storageKey: stored.storageKey,
      originalName: stored.originalName,
      mimeType: stored.mimeType,
      extension: stored.extension,
      sizeBytes: stored.sizeBytes,
      checksum: stored.checksum,
    };
  }

  // Перевірка формату -> pre-check -> перенесення у сховище.
  private async storeFile(
    trackId: string,
    kind: MediaKind,
    file: UploadedMediaFile | undefined,
  ): Promise<StoredFile> {
    const label = kind === 'AUDIO' ? 'audio' : 'cover';

    if (!file) {
      throw new BadRequestException(`${label} file is required`);
    }

    const extension = extensionForMedia(kind, file.originalname, file.mimetype);

    if (!extension) {
      throw new BadRequestException(`Unsupported ${label} format`);
    }

    // Antivirus/security pre-check виконується ДО обробки файлу.
    const scan = await this.security.scan(file.path, kind);

    if (!scan.clean) {
      this.logger.warn(
        `Upload rejected for track ${trackId}: ${scan.reason ?? 'unknown'}`,
      );

      await this.storage.removeTemp(file.path);

      throw new BadRequestException(
        `Security pre-check failed: ${scan.reason ?? 'file rejected'}`,
      );
    }

    const storageKey = this.storage.buildStorageKey(kind, extension);
    await this.storage.moveIntoStorage(file.path, storageKey);

    return {
      storageKey,
      checksum: await this.storage.checksum(storageKey),
      extension,
      sizeBytes: file.size,
      mimeType: file.mimetype,
      originalName: file.originalname,
    };
  }

  private async findTrack(trackId: string) {
    const track = await this.prisma.track.findUnique({
      where: { id: trackId },
    });

    if (!track) {
      throw new NotFoundException('Track not found');
    }

    return track;
  }
}
