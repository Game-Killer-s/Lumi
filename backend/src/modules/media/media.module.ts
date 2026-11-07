import { Module } from '@nestjs/common';

import { CatalogModule } from '../catalog/catalog.module';
import { AudioToolchainService } from './audio-toolchain.service';
import { MediaMetadataService } from './media-metadata.service';
import { MediaProcessingController } from './media-processing.controller';
import { MediaProcessingJobService } from './media-processing-job.service';
import { MediaSecurityService } from './media-security.service';
import { MediaStorageService } from './media-storage.service';
import { MediaUploadController } from './media-upload.controller';
import { MediaUploadService } from './media-upload.service';

// Завантаження медіа (US1) + обробка аудіо (US2) + метадані (US3).
@Module({
  imports: [CatalogModule],
  controllers: [MediaUploadController, MediaProcessingController],
  providers: [
    MediaUploadService,
    MediaProcessingJobService,
    MediaStorageService,
    MediaSecurityService,
    MediaMetadataService,
    AudioToolchainService,
  ],
  exports: [MediaProcessingJobService, MediaStorageService],
})
export class MediaModule {}
