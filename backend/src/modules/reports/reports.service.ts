import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { TrackStatus } from '@prisma/client';

import { PrismaService } from '../../prisma/prisma.service';
import { CreateReportDto } from './dto/create-report.dto';

// Скарги слухачів з плеєра (ТЗ 6.3.12).
// Відповідь { id, status } збігається з Android-моделлю ReportResponse.
@Injectable()
export class ReportsService {
  constructor(private readonly prisma: PrismaService) {}

  async create(userId: string, dto: CreateReportDto) {
    // Скаржитись можна лише на опублікований трек.
    const track = await this.prisma.track.findFirst({
      where: { id: dto.trackId, status: TrackStatus.PUBLISHED },
      select: { id: true },
    });

    if (!track) {
      throw new NotFoundException('Track not found or not published');
    }

    const target = dto.target ?? 'track';
    let targetId = dto.trackId;

    if (target === 'comment') {
      if (!dto.targetId) {
        throw new BadRequestException('targetId is required for comment report');
      }

      // Коментар має існувати і належати саме цьому треку.
      const comment = await this.prisma.comment.findFirst({
        where: { id: dto.targetId, trackId: dto.trackId },
        select: { id: true },
      });

      if (!comment) {
        throw new NotFoundException('Comment not found');
      }

      targetId = comment.id;
    }

    const report = await this.prisma.report.create({
      data: {
        userId,
        trackId: dto.trackId,
        target,
        targetId,
        reason: dto.reason,
        comment: dto.comment?.length ? dto.comment : null,
      },
      select: { id: true, status: true },
    });

    return { id: report.id, status: report.status };
  }
}
