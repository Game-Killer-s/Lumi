import { Body, Controller, Param, Post } from '@nestjs/common';
import { ApiTags } from '@nestjs/swagger';
import { Role } from '@prisma/client';

import { Auth } from '../auth/decorators/auth.decorator';
import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { ContentLifecycleService } from './content-lifecycle.service';
import { BlockTrackDto } from './dto/block-track.dto';
import { UploadTrackDto } from './dto/upload-track.dto';

/**
 * Admin endpoints керування життєвим циклом треку.
 *
 * - upload / processing / moderation / publish - CONTENT_MANAGER або ADMIN;
 * - block / unblock - лише ADMIN.
 */
@ApiTags('admin/content-lifecycle')
@Controller('admin/tracks')
export class ContentLifecycleController {
  constructor(private readonly lifecycleService: ContentLifecycleService) {}

  @Auth(Role.CONTENT_MANAGER, Role.ADMIN)
  @Post(':id/upload')
  upload(
    @Param('id') id: string,
    @Body() dto: UploadTrackDto,
    @CurrentUser('sub') actorId: string,
  ) {
    return this.lifecycleService.markUploaded(id, dto.audioUrl, actorId);
  }

  @Auth(Role.CONTENT_MANAGER, Role.ADMIN)
  @Post(':id/processing')
  processing(@Param('id') id: string, @CurrentUser('sub') actorId: string) {
    return this.lifecycleService.startProcessing(id, actorId);
  }

  @Auth(Role.CONTENT_MANAGER, Role.ADMIN)
  @Post(':id/moderation')
  moderation(@Param('id') id: string, @CurrentUser('sub') actorId: string) {
    return this.lifecycleService.markReadyForModeration(id, actorId);
  }

  @Auth(Role.CONTENT_MANAGER, Role.ADMIN)
  @Post(':id/publish')
  publish(@Param('id') id: string, @CurrentUser('sub') actorId: string) {
    return this.lifecycleService.publish(id, actorId);
  }

  @Auth(Role.ADMIN)
  @Post(':id/block')
  block(
    @Param('id') id: string,
    @Body() dto: BlockTrackDto,
    @CurrentUser('sub') actorId: string,
  ) {
    return this.lifecycleService.block(id, dto.reason, actorId);
  }

  @Auth(Role.ADMIN)
  @Post(':id/unblock')
  unblock(@Param('id') id: string, @CurrentUser('sub') actorId: string) {
    return this.lifecycleService.unblock(id, actorId);
  }
}
