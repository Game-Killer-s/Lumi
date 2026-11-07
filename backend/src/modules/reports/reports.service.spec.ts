import { BadRequestException, NotFoundException } from '@nestjs/common';
import { TrackStatus } from '@prisma/client';

import { PrismaService } from '../../prisma/prisma.service';
import { CreateReportDto } from './dto/create-report.dto';
import { ReportsService } from './reports.service';

// Скарги з плеєра (ТЗ 6.3.11, 6.7.2): трек або коментар під треком.
describe('ReportsService', () => {
  const userId = 'user-1';
  const trackId = 'track-1';
  const commentId = 'comment-1';

  let prisma: {
    track: { findFirst: jest.Mock };
    comment: { findFirst: jest.Mock };
    report: { create: jest.Mock };
  };
  let service: ReportsService;

  const dto = (overrides: Partial<CreateReportDto> = {}): CreateReportDto => ({
    target: 'track',
    trackId,
    reason: 'copyright',
    ...overrides,
  });

  beforeEach(() => {
    prisma = {
      track: { findFirst: jest.fn() },
      comment: { findFirst: jest.fn() },
      report: { create: jest.fn() },
    };

    service = new ReportsService(prisma as unknown as PrismaService);

    prisma.track.findFirst.mockResolvedValue({ id: trackId });
    prisma.report.create.mockResolvedValue({ id: 'report-1', status: 'NEW' });
  });

  it('зберігає скаргу на трек і повертає id зі статусом NEW', async () => {
    const result = await service.create(userId, dto());

    expect(prisma.track.findFirst).toHaveBeenCalledWith({
      where: { id: trackId, status: TrackStatus.PUBLISHED },
      select: { id: true },
    });
    expect(prisma.report.create).toHaveBeenCalledWith({
      data: {
        userId,
        trackId,
        target: 'track',
        targetId: trackId,
        reason: 'copyright',
        comment: null,
      },
      select: { id: true, status: true },
    });
    expect(result).toEqual({ id: 'report-1', status: 'NEW' });
  });

  it('не створює скаргу, якщо трек не знайдено або не опубліковано', async () => {
    prisma.track.findFirst.mockResolvedValue(null);

    await expect(service.create(userId, dto())).rejects.toBeInstanceOf(
      NotFoundException,
    );
    expect(prisma.report.create).not.toHaveBeenCalled();
  });

  it('для скарги на коментар вимагає targetId', async () => {
    await expect(
      service.create(userId, dto({ target: 'comment' })),
    ).rejects.toBeInstanceOf(BadRequestException);
    expect(prisma.report.create).not.toHaveBeenCalled();
  });

  it('перевіряє, що коментар належить саме цьому треку', async () => {
    prisma.comment.findFirst.mockResolvedValue(null);

    await expect(
      service.create(userId, dto({ target: 'comment', targetId: commentId })),
    ).rejects.toBeInstanceOf(NotFoundException);
    expect(prisma.comment.findFirst).toHaveBeenCalledWith({
      where: { id: commentId, trackId },
      select: { id: true },
    });
    expect(prisma.report.create).not.toHaveBeenCalled();
  });

  it('зберігає скаргу на коментар разом із поясненням', async () => {
    prisma.comment.findFirst.mockResolvedValue({ id: commentId });

    await service.create(
      userId,
      dto({
        target: 'comment',
        targetId: commentId,
        reason: 'spam',
        comment: 'спам у коментарях',
      }),
    );

    expect(prisma.report.create).toHaveBeenCalledWith({
      data: {
        userId,
        trackId,
        target: 'comment',
        targetId: commentId,
        reason: 'spam',
        comment: 'спам у коментарях',
      },
      select: { id: true, status: true },
    });
  });
});
