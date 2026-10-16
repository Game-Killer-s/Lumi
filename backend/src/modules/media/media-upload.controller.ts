import {
  Body,
  Controller,
  Param,
  Post,
  UploadedFile,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { ApiBody, ApiConsumes, ApiTags } from '@nestjs/swagger';
import { Role } from '@prisma/client';

import { Auth } from '../auth/decorators/auth.decorator';
import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { UploadAudioDto } from './dto/upload-audio.dto';
import { UploadCoverDto } from './dto/upload-cover.dto';
import {
  AUDIO_UPLOAD_OPTIONS,
  COVER_UPLOAD_OPTIONS,
  UploadedMediaFile,
} from './media.constants';
import { MediaUploadService } from './media-upload.service';

/**
 * Адмінське завантаження медіа (multipart/form-data).
 * Доступ: CONTENT_MANAGER або ADMIN.
 */
@ApiTags('admin/media')
@Controller('admin/tracks')
export class MediaUploadController {
  constructor(private readonly mediaUploadService: MediaUploadService) {}

  @Auth(Role.CONTENT_MANAGER, Role.ADMIN)
  @Post(':id/audio')
  @ApiConsumes('multipart/form-data')
  @ApiBody({
    schema: {
      type: 'object',
      properties: {
        audio: { type: 'string', format: 'binary' },
        title: { type: 'string' },
        duration: { type: 'integer' },
        lyrics: { type: 'string' },
      },
    },
  })
  @UseInterceptors(FileInterceptor('audio', AUDIO_UPLOAD_OPTIONS))
  uploadAudio(
    @Param('id') id: string,
    @UploadedFile() file: UploadedMediaFile | undefined,
    @Body() dto: UploadAudioDto,
    @CurrentUser('sub') actorId: string,
  ) {
    return this.mediaUploadService.uploadAudio(id, file, dto, actorId);
  }

  @Auth(Role.CONTENT_MANAGER, Role.ADMIN)
  @Post(':id/cover')
  @ApiConsumes('multipart/form-data')
  @ApiBody({
    schema: {
      type: 'object',
      properties: {
        cover: { type: 'string', format: 'binary' },
        title: { type: 'string' },
      },
    },
  })
  @UseInterceptors(FileInterceptor('cover', COVER_UPLOAD_OPTIONS))
  uploadCover(
    @Param('id') id: string,
    @UploadedFile() file: UploadedMediaFile | undefined,
    @Body() dto: UploadCoverDto,
    @CurrentUser('sub') actorId: string,
  ) {
    return this.mediaUploadService.uploadCover(id, file, dto, actorId);
  }
}
