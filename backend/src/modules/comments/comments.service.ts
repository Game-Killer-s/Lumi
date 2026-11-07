import {
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { Prisma, Role, TrackStatus } from '@prisma/client';

import { PrismaService } from '../../prisma/prisma.service';

// Коментарі під треком (ТЗ 6.3.11). Формат відповіді збігається з Android-
// моделями Comment/CommentListResponse: { comments, total, page, limit }.
@Injectable()
export class CommentsService {
  constructor(private readonly prisma: PrismaService) {}

  async list(trackId: string, page: number, limit: number) {
    const where: Prisma.CommentWhereInput = { trackId, isHidden: false };

    const [comments, total] = await Promise.all([
      this.prisma.comment.findMany({
        where,
        orderBy: { createdAt: 'desc' },
        skip: (page - 1) * limit,
        take: limit,
        select: this.commentSelect(),
      }),
      this.prisma.comment.count({ where }),
    ]);

    return { comments, total, page, limit };
  }

  async create(userId: string, trackId: string, text: string) {
    // Коментувати можна лише опублікований трек.
    const track = await this.prisma.track.findFirst({
      where: { id: trackId, status: TrackStatus.PUBLISHED },
      select: { id: true },
    });

    if (!track) {
      throw new NotFoundException('Track not found or not published');
    }

    return this.prisma.comment.create({
      data: { userId, trackId, text },
      select: this.commentSelect(),
    });
  }

  async remove(userId: string, role: Role, commentId: string) {
    const comment = await this.prisma.comment.findUnique({
      where: { id: commentId },
      select: { id: true, userId: true },
    });

    if (!comment) {
      throw new NotFoundException('Comment not found');
    }

    // Свій коментар може видалити автор; чужі — лише модератор/адмін (ТЗ 6.7.2).
    const canModerate = role === Role.ADMIN || role === Role.CONTENT_MANAGER;

    if (comment.userId !== userId && !canModerate) {
      throw new ForbiddenException('You can delete only your own comments');
    }

    await this.prisma.comment.delete({ where: { id: commentId } });

    return { deleted: true };
  }

  /** Поля коментаря, які читає клієнт (без email та інших даних автора). */
  private commentSelect(): Prisma.CommentSelect {
    return {
      id: true,
      text: true,
      userId: true,
      trackId: true,
      isHidden: true,
      createdAt: true,
      user: {
        select: {
          id: true,
          nickname: true,
          avatarUrl: true,
        },
      },
    };
  }
}
