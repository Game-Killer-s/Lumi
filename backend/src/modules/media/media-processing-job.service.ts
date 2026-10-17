import {
  BadRequestException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ProcessingStatus } from '@prisma/client';

import { PrismaService } from '../../prisma/prisma.service';
import { AudioToolchainService } from './audio-toolchain.service';
import {
  assetMetadataFields,
  MediaMetadataError,
  MediaMetadataService,
  toSafeFailureReason,
} from './media-metadata.service';
import { MediaStorageService } from './media-storage.service';

// Варіанти стиснення з ТЗ: Opus 96 / 160 / 256 kbps.
export const OPUS_BITRATES = [96, 160, 256];

// Якщо в .env нічого не задано - нормалізуємо до -14 LUFS.
export const DEFAULT_TARGET_LUFS = -14;

/**
 * Обробка завантаженого аудіо.
 *
 * Статуси: QUEUED -> PROCESSING -> READY | FAILED.
 * Кроки: probe і валідація файлу -> аналіз гучності -> нормалізація до
 * цільового LUFS -> генерація Opus варіантів (96/160/256) -> метадані.
 */
@Injectable()
export class MediaProcessingJobService {
  private readonly logger = new Logger(MediaProcessingJobService.name);
  private readonly targetLufs: number;

  constructor(
    private readonly prisma: PrismaService,
    private readonly storage: MediaStorageService,
    private readonly toolchain: AudioToolchainService,
    private readonly metadata: MediaMetadataService,
    configService: ConfigService,
  ) {
    this.targetLufs =
      configService.get<number>('media.targetLufs') ?? DEFAULT_TARGET_LUFS;
  }

  // Дані для створення job-у у черзі. Використовується і в транзакції
  // завантаження, тому винесено окремо.
  buildQueuedJobData(assetId: string) {
    return {
      assetId,
      targetLufs: this.targetLufs,
      status: ProcessingStatus.QUEUED,
    };
  }

  // Job для файлу, який не пройшов probe: черга не потрібна - стан уже
  // відомий, а причина збережена для Content Manager.
  buildFailedJobData(assetId: string, reason: string) {
    return {
      assetId,
      targetLufs: this.targetLufs,
      status: ProcessingStatus.FAILED,
      failureReason: reason,
    };
  }

  // Ставимо job у чергу.
  async enqueue(assetId: string) {
    return this.prisma.mediaProcessingJob.create({
      data: this.buildQueuedJobData(assetId),
    });
  }

  async getStatus(jobId: string) {
    const job = await this.prisma.mediaProcessingJob.findUnique({
      where: { id: jobId },
      include: {
        asset: true,
        variants: { orderBy: { bitrateKbps: 'asc' } },
      },
    });

    if (!job) {
      throw new NotFoundException('Processing job not found');
    }

    return job;
  }

  // Повторний запуск невдалого job-у (лічильник спроб росте).
  async retry(jobId: string) {
    const job = await this.prisma.mediaProcessingJob.findUnique({
      where: { id: jobId },
    });

    if (!job) {
      throw new NotFoundException('Processing job not found');
    }

    if (job.status !== ProcessingStatus.FAILED) {
      throw new BadRequestException('Only a FAILED job can be retried');
    }

    await this.prisma.mediaProcessingJob.update({
      where: { id: jobId },
      data: { status: ProcessingStatus.QUEUED, failureReason: null },
    });

    return this.run(jobId);
  }

  // Основний прохід обробки. Помилку не кидаємо назовні, а фіксуємо
  // у самому job-і (FAILED + failureReason) - щоб клієнт бачив стан.
  async run(jobId: string) {
    const job = await this.prisma.mediaProcessingJob.findUnique({
      where: { id: jobId },
      include: { asset: true },
    });

    if (!job) {
      throw new NotFoundException('Processing job not found');
    }

    if (job.status === ProcessingStatus.PROCESSING) {
      throw new BadRequestException('Job is already processing');
    }

    await this.prisma.mediaProcessingJob.update({
      where: { id: jobId },
      data: {
        status: ProcessingStatus.PROCESSING,
        attempts: { increment: 1 },
        failureReason: null,
      },
    });

    try {
      const result = await this.processAsset(
        job.id,
        job.assetId,
        job.asset.storageKey,
      );

      return await this.prisma.mediaProcessingJob.update({
        where: { id: jobId },
        data: {
          status: ProcessingStatus.READY,
          measuredLufs: result.measuredLufs,
          failureReason: null,
        },
        include: { variants: { orderBy: { bitrateKbps: 'asc' } } },
      });
    } catch (error) {
      // Назовні віддаємо безпечну причину: Content Manager бачить
      // зрозумілий текст, без шляхів до файлів і виводу ffmpeg.
      const reason = toSafeFailureReason(error);

      this.logger.error(`Processing job ${jobId} failed: ${reason}`);

      return this.prisma.mediaProcessingJob.update({
        where: { id: jobId },
        data: { status: ProcessingStatus.FAILED, failureReason: reason },
        include: { variants: { orderBy: { bitrateKbps: 'asc' } } },
      });
    }
  }

  /**
   * Перший крок конвеєра: probe і валідація вихідного файлу.
   * Метадані одразу лягають у MediaAsset, а пошкоджений файл дістає
   * FAILED і не витрачає час на нормалізацію.
   */
  private async inspectSource(assetId: string, storageKey: string) {
    const inspection = await this.metadata.inspect(storageKey);

    await this.prisma.mediaAsset.update({
      where: { id: assetId },
      data: assetMetadataFields(inspection),
    });

    if (!inspection.ok) {
      throw new MediaMetadataError('PROBE_FAILED', inspection.reason);
    }

    return inspection.metadata;
  }

  // Нормалізація + транскодування. Файли генеруємо ДО транзакції,
  // щоб не тримати транзакцію відкритою на час роботи ffmpeg.
  private async processAsset(
    jobId: string,
    assetId: string,
    sourceStorageKey: string,
  ) {
    await this.inspectSource(assetId, sourceStorageKey);

    const sourcePath = this.storage.absolutePath(sourceStorageKey);
    const analysis = await this.toolchain.analyzeLoudness(sourcePath);

    const normalizedKey = this.storage.buildStorageKey('AUDIO', '.flac');
    await this.storage.ensureDirFor(normalizedKey);

    await this.toolchain.normalizeToTarget(
      sourcePath,
      this.storage.absolutePath(normalizedKey),
      this.targetLufs,
    );

    const variants: {
      codec: string;
      bitrateKbps: number;
      storageKey: string;
      sizeBytes: number | null;
    }[] = [];

    for (const bitrateKbps of OPUS_BITRATES) {
      const storageKey = this.storage.buildStorageKey('AUDIO', '.opus');
      await this.storage.ensureDirFor(storageKey);

      await this.toolchain.transcodeToOpus(
        this.storage.absolutePath(normalizedKey),
        this.storage.absolutePath(storageKey),
        bitrateKbps,
      );

      variants.push({
        codec: 'opus',
        bitrateKbps,
        storageKey,
        sizeBytes: await this.storage.statSize(storageKey),
      });
    }

    await this.prisma.mediaVariant.deleteMany({ where: { jobId } });

    await this.prisma.mediaVariant.createMany({
      data: variants.map((variant) => ({ ...variant, jobId })),
    });

    return { measuredLufs: analysis.integratedLufs };
  }
}
