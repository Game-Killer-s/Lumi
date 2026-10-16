import { Controller, Get, Param, Post } from '@nestjs/common';
import { ApiTags } from '@nestjs/swagger';
import { Role } from '@prisma/client';

import { Auth } from '../auth/decorators/auth.decorator';
import { MediaProcessingJobService } from './media-processing-job.service';

/**
 * Адмінське керування обробкою аудіо.
 * Доступ: CONTENT_MANAGER або ADMIN.
 */
@ApiTags('admin/media')
@Controller('admin/media/jobs')
export class MediaProcessingController {
  constructor(
    private readonly processingJobsService: MediaProcessingJobService,
  ) {}

  @Auth(Role.CONTENT_MANAGER, Role.ADMIN)
  @Get(':id')
  status(@Param('id') id: string) {
    return this.processingJobsService.getStatus(id);
  }

  // Запуск нормалізації та генерації Opus 96/160/256.
  @Auth(Role.CONTENT_MANAGER, Role.ADMIN)
  @Post(':id/run')
  run(@Param('id') id: string) {
    return this.processingJobsService.run(id);
  }

  // Повторний запуск після FAILED.
  @Auth(Role.CONTENT_MANAGER, Role.ADMIN)
  @Post(':id/retry')
  retry(@Param('id') id: string) {
    return this.processingJobsService.retry(id);
  }
}
