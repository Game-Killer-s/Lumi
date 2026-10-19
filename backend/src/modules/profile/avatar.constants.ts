import { BadRequestException } from '@nestjs/common';
import type { MulterOptions } from '@nestjs/platform-express/multer/interfaces/multer-options.interface';

import { extensionForMedia, MEDIA_TEMP_DIR } from '../media/media.constants';

export const MAX_AVATAR_UPLOAD_BYTES = 5 * 1024 * 1024; // 5 MB
export const AVATAR_MIN_DIMENSION = 64;
export const AVATAR_MAX_DIMENSION = 4096;

export const AVATAR_UPLOAD_OPTIONS: MulterOptions = {
  dest: MEDIA_TEMP_DIR,
  limits: { fileSize: MAX_AVATAR_UPLOAD_BYTES, files: 1 },
  fileFilter: (_request, file, callback) => {
    if (!extensionForMedia('AVATAR', file.originalname, file.mimetype)) {
      callback(
        new BadRequestException(`Unsupported avatar format: ${file.mimetype}`),
        false,
      );

      return;
    }

    callback(null, true);
  },
};
