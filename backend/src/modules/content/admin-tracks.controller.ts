import { Body, Controller, Param, Patch } from '@nestjs/common';
import { ApiTags } from '@nestjs/swagger';
import { Role } from '@prisma/client';

import { Auth } from '../auth/decorators/auth.decorator';
import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { AdminTracksService } from './admin-tracks.service';
import { AdminTrackUpdateDto } from './dto/admin-track-update.dto';
import { ReplaceCoverDto } from './dto/replace-cover.dto';

/**
 * Admin endpoints редагування треків.
 * Доступ: CONTENT_MANAGER або ADMIN.
 */
@ApiTags('admin/tracks')
@Controller('admin/tracks')
export class AdminTracksController {
  constructor(private readonly adminTracksService: AdminTracksService) {}

  @Auth(Role.CONTENT_MANAGER, Role.ADMIN)
  @Patch(':id')
  updateMetadata(
    @Param('id') id: string,
    @Body() dto: AdminTrackUpdateDto,
    @CurrentUser('sub') actorId: string,
  ) {
    return this.adminTracksService.updateMetadata(id, dto, actorId);
  }

  @Auth(Role.CONTENT_MANAGER, Role.ADMIN)
  @Patch(':id/cover')
  replaceCover(
    @Param('id') id: string,
    @Body() dto: ReplaceCoverDto,
    @CurrentUser('sub') actorId: string,
  ) {
    return this.adminTracksService.replaceCover(id, dto.coverUrl, actorId);
  }
}
