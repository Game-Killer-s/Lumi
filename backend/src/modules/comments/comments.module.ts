import { Module } from '@nestjs/common';

import { AuthModule } from '../auth/auth.module';
import { CommentsController } from './comments.controller';
import { CommentsService } from './comments.service';

// Коментарі під треками (/api/tracks/:id/comments, /api/comments/:id).
@Module({
  imports: [AuthModule],
  controllers: [CommentsController],
  providers: [CommentsService],
})
export class CommentsModule {}
