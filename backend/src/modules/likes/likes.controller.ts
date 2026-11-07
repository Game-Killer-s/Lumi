import {
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  Post,
  Query,
} from '@nestjs/common';
import { ApiOkResponse, ApiOperation, ApiTags } from '@nestjs/swagger';

import { Auth } from '../auth/decorators/auth.decorator';
import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { LikesQueryDto } from './dto/likes-query.dto';
import { LikesService } from './likes.service';

// Персональні лайки — тому @Auth на всьому контролері
// (shared-кеш для цих даних не використовується, див. cache.policy.ts).
@ApiTags('likes')
@Controller('likes')
export class LikesController {
  constructor(private readonly likesService: LikesService) {}

  // GET /api/likes?page=&limit= — улюблені треки поточного користувача
  @Auth()
  @Get()
  @ApiOperation({ summary: 'Get tracks liked by the current user' })
  @ApiOkResponse({
    description: 'TrackListResponse { tracks, total, page, limit }',
  })
  getLikedTracks(
    @CurrentUser('sub') userId: string,
    @Query() query: LikesQueryDto,
  ) {
    return this.likesService.getLikedTracks(userId, query.page, query.limit);
  }

  // POST /api/likes/:trackId — додати трек в улюблене (ідемпотентно)
  @Auth()
  @Post(':trackId')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Like a track' })
  @ApiOkResponse({ description: '{ liked: true }' })
  like(@CurrentUser('sub') userId: string, @Param('trackId') trackId: string) {
    return this.likesService.like(userId, trackId);
  }

  // DELETE /api/likes/:trackId — прибрати трек з улюбленого (ідемпотентно)
  @Auth()
  @Delete(':trackId')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Unlike a track' })
  @ApiOkResponse({ description: '{ liked: false }' })
  unlike(
    @CurrentUser('sub') userId: string,
    @Param('trackId') trackId: string,
  ) {
    return this.likesService.unlike(userId, trackId);
  }
}
