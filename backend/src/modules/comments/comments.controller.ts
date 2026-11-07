import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  Post,
  Query,
} from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Role } from '@prisma/client';

import { Auth } from '../auth/decorators/auth.decorator';
import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { Public } from '../auth/decorators/public.decorator';
import { CommentsService } from './comments.service';
import { CreateCommentDto } from './dto/create-comment.dto';
import { ListCommentsQueryDto } from './dto/list-comments-query.dto';

// Коментарі під треком. Читання публічне, створення/видалення — з токеном.
@ApiTags('comments')
@Controller()
export class CommentsController {
  constructor(private readonly commentsService: CommentsService) {}

  // GET /api/tracks/:trackId/comments?page=&limit=
  @Public()
  @Get('tracks/:trackId/comments')
  @ApiOperation({ summary: 'List comments of a track' })
  list(
    @Param('trackId') trackId: string,
    @Query() query: ListCommentsQueryDto,
  ) {
    return this.commentsService.list(trackId, query.page, query.limit);
  }

  // POST /api/tracks/:trackId/comments — додати коментар
  @Auth()
  @Post('tracks/:trackId/comments')
  @ApiOperation({ summary: 'Add a comment to a track' })
  create(
    @CurrentUser('sub') userId: string,
    @Param('trackId') trackId: string,
    @Body() dto: CreateCommentDto,
  ) {
    return this.commentsService.create(userId, trackId, dto.text);
  }

  // DELETE /api/comments/:id — видалити свій коментар
  @Auth()
  @Delete('comments/:id')
  @ApiOperation({ summary: 'Delete a comment' })
  remove(
    @CurrentUser('sub') userId: string,
    @CurrentUser('role') role: Role,
    @Param('id') commentId: string,
  ) {
    return this.commentsService.remove(userId, role, commentId);
  }
}
