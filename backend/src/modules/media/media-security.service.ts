import { Injectable, Logger } from '@nestjs/common';

import {
  EICAR_MARKER,
  matchesMediaSignature,
  MediaKind,
} from './media.constants';
import { MediaStorageService } from './media-storage.service';

export interface MediaScanResult {
  clean: boolean;
  reason?: string;
}

/**
 * Antivirus/security pre-check, який виконується ДО обробки файлу.
 *
 * 1. Антивірусна евристика: шукаємо сигнатуру EICAR (тестовий "вірус",
 *    який використовують, щоб перевірити роботу антивіруса).
 * 2. Перевірка справжнього типу за магічними байтами - розширення і mime
 *    задає клієнт, тому перевіряємо вміст.
 *
 * Заражений файл не потрапляє у сховище: upload-сервіс його видаляє.
 */
@Injectable()
export class MediaSecurityService {
  private readonly logger = new Logger(MediaSecurityService.name);

  constructor(private readonly storage: MediaStorageService) {}

  async scan(filePath: string, kind: MediaKind): Promise<MediaScanResult> {
    const head = await this.storage.readHead(filePath);

    if (head.length === 0) {
      return { clean: false, reason: 'File is empty' };
    }

    if (head.includes(EICAR_MARKER)) {
      this.logger.warn(`Antivirus signature found in ${filePath}`);

      return {
        clean: false,
        reason: `Antivirus signature detected: ${EICAR_MARKER}`,
      };
    }

    if (!matchesMediaSignature(kind, head)) {
      return {
        clean: false,
        reason: `File content does not match ${kind.toLowerCase()} format`,
      };
    }

    return { clean: true };
  }
}
