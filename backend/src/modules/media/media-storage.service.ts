import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createHash, randomUUID } from 'node:crypto';
import { createReadStream, mkdirSync, promises as fs } from 'node:fs';
import { dirname, join, resolve, sep } from 'node:path';

import { MEDIA_TEMP_DIR, MediaKind } from './media.constants';

/**
 * Робота з файлами завантажень.
 *
 * Multer стрімить файл у тимчасову папку, і лише після pre-check
 * ми переносимо його у постійне сховище під ключем без імені файлу
 * користувача (kind/рік/місяць/uuid.ext) - так неможливо підсунути
 * свій шлях або перезаписати чужий файл.
 */
@Injectable()
export class MediaStorageService {
  private readonly logger = new Logger(MediaStorageService.name);
  private readonly uploadDir: string;

  constructor(configService: ConfigService) {
    this.uploadDir = resolve(
      configService.get<string>('storage.uploadDir') ?? 'storage',
    );

    // Створюємо папки одразу, щоб перше завантаження не впало.
    mkdirSync(this.uploadDir, { recursive: true });
    mkdirSync(MEDIA_TEMP_DIR, { recursive: true });
  }

  get rootDir(): string {
    return this.uploadDir;
  }

  get tempDir(): string {
    return MEDIA_TEMP_DIR;
  }

  // Унікальний ключ у сховищі: жодних даних від користувача, крім типу файлу.
  buildStorageKey(kind: MediaKind, extension: string): string {
    const now = new Date();
    const month = String(now.getUTCMonth() + 1).padStart(2, '0');

    return [
      kind.toLowerCase(),
      String(now.getUTCFullYear()),
      month,
      `${randomUUID()}${extension}`,
    ].join('/');
  }

  absolutePath(storageKey: string): string {
    return join(this.uploadDir, storageKey.split('/').join(sep));
  }

  async ensureDirFor(storageKey: string): Promise<void> {
    await fs.mkdir(dirname(this.absolutePath(storageKey)), { recursive: true });
  }

  // Переносимо файл з тимчасової папки у сховище.
  async moveIntoStorage(tempPath: string, storageKey: string): Promise<void> {
    await this.ensureDirFor(storageKey);

    try {
      await fs.rename(tempPath, this.absolutePath(storageKey));
    } catch {
      // rename не працює між різними дисками - тоді копіюємо.
      await fs.copyFile(tempPath, this.absolutePath(storageKey));
      await this.removeTemp(tempPath);
    }
  }

  async remove(storageKey: string): Promise<void> {
    await fs.rm(this.absolutePath(storageKey), { force: true });
  }

  async removeTemp(tempPath: string): Promise<void> {
    await fs.rm(tempPath, { force: true });
  }

  // Читаємо тільки початок файлу - щоб не тягнути весь файл у пам'ять.
  async readHead(filePath: string, length = 512): Promise<Buffer> {
    const handle = await fs.open(filePath, 'r');

    try {
      const buffer = Buffer.alloc(length);
      const { bytesRead } = await handle.read(buffer, 0, length, 0);

      return buffer.subarray(0, bytesRead);
    } finally {
      await handle.close();
    }
  }

  // sha256 файлу - щоб потім можна було перевірити цілісність.
  checksum(storageKey: string): Promise<string> {
    return new Promise((resolveChecksum, rejectChecksum) => {
      const hash = createHash('sha256');

      createReadStream(this.absolutePath(storageKey))
        .on('error', rejectChecksum)
        .on('data', (chunk) => hash.update(chunk))
        .on('end', () => resolveChecksum(hash.digest('hex')));
    });
  }

  async statSize(storageKey: string): Promise<number | null> {
    try {
      const stats = await fs.stat(this.absolutePath(storageKey));

      return stats.size;
    } catch {
      this.logger.warn(`Cannot read size of ${storageKey}`);

      return null;
    }
  }
}
