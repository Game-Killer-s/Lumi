import { BadRequestException } from '@nestjs/common';
import { TrackStatus } from '@prisma/client';

import { CatalogCacheService } from '../catalog/catalog-cache.service';
import { PrismaService } from '../../prisma/prisma.service';
import {
  ContentLifecycleService,
  LIFECYCLE_TRANSITIONS,
} from './content-lifecycle.service';

/** Мок PrismaService, типізований через jest.Mock */
type PrismaMock = {
  track: {
    findUnique: jest.Mock;
    update: jest.Mock;
  };
  trackAuditLog: {
    create: jest.Mock;
  };
};

/** Мок CatalogCacheService */
type CacheMock = {
  invalidateCatalog: jest.Mock;
};

describe('ContentLifecycleService', () => {
  let service: ContentLifecycleService;
  let prisma: PrismaMock;
  let cache: CacheMock;

  const trackId = 'track-1';
  const actorId = 'user-1';

  beforeEach(() => {
    prisma = {
      track: {
        findUnique: jest.fn(),
        update: jest.fn(),
      },
      trackAuditLog: {
        create: jest.fn(),
      },
    };

    cache = {
      invalidateCatalog: jest.fn(),
    };

    service = new ContentLifecycleService(
      prisma as unknown as PrismaService,
      cache as unknown as CatalogCacheService,
    );
  });

  function mockTrack(
    status: TrackStatus,
    overrides: Record<string, unknown> = {},
  ) {
    prisma.track.findUnique.mockResolvedValue({
      id: trackId,
      title: 'Test',
      duration: 200,
      status,
      audioUrl: null,
      blockReason: null,
      ...overrides,
    });
  }

  describe('transition matrix', () => {
    const allStates = Object.values(TrackStatus);

    for (const from of allStates) {
      for (const to of allStates) {
        it(`${from} -> ${to}`, () => {
          const allowed = LIFECYCLE_TRANSITIONS[from].includes(to);

          if (allowed) {
            expect(() => service.assertValidTransition(from, to)).not.toThrow();
          } else {
            expect(() => service.assertValidTransition(from, to)).toThrow(
              BadRequestException,
            );
          }
        });
      }
    }
  });

  describe('upload -> processing', () => {
    it('marks track as UPLOADED and saves audioUrl', async () => {
      mockTrack(TrackStatus.DRAFT);

      await service.markUploaded(
        trackId,
        'https://cdn.lumi.app/a.mp3',
        actorId,
      );

      expect(prisma.track.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            status: TrackStatus.UPLOADED,
            audioUrl: 'https://cdn.lumi.app/a.mp3',
          }),
        }),
      );

      expect(prisma.trackAuditLog.create).toHaveBeenCalled();
      expect(cache.invalidateCatalog).toHaveBeenCalled();
    });

    it('rejects upload without audioUrl', async () => {
      mockTrack(TrackStatus.DRAFT);

      await expect(service.markUploaded(trackId, '', actorId)).rejects.toThrow(
        BadRequestException,
      );
    });

    it('allows UPLOADED -> PROCESSING', async () => {
      mockTrack(TrackStatus.UPLOADED);

      await service.startProcessing(trackId, actorId);

      expect(prisma.track.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            status: TrackStatus.PROCESSING,
          }),
        }),
      );
    });
  });

  describe('processing -> moderation (ready variants)', () => {
    it('allows only when track has ready variants (audioUrl)', async () => {
      mockTrack(TrackStatus.PROCESSING, {
        audioUrl: 'https://cdn.lumi.app/a.mp3',
      });

      await service.markReadyForModeration(trackId, actorId);

      expect(prisma.track.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            status: TrackStatus.ON_MODERATION,
          }),
        }),
      );
    });

    it('rejects when variants are not ready (no audioUrl)', async () => {
      mockTrack(TrackStatus.PROCESSING, {
        audioUrl: null,
      });

      await expect(
        service.markReadyForModeration(trackId, actorId),
      ).rejects.toThrow(BadRequestException);
    });
  });

  describe('moderation -> published', () => {
    it('publishes track', async () => {
      mockTrack(TrackStatus.ON_MODERATION);

      await service.publish(trackId, actorId);

      expect(prisma.track.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            status: TrackStatus.PUBLISHED,
          }),
        }),
      );
    });
  });

  describe('block/unblock policy', () => {
    it('blocks only with reason', async () => {
      mockTrack(TrackStatus.PUBLISHED);

      await service.block(trackId, 'Copyright violation', actorId);

      expect(prisma.track.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            status: TrackStatus.BLOCKED,
            blockReason: 'Copyright violation',
          }),
        }),
      );

      expect(prisma.trackAuditLog.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            reason: 'Copyright violation',
          }),
        }),
      );
    });

    it('rejects block without reason', async () => {
      mockTrack(TrackStatus.PUBLISHED);

      await expect(service.block(trackId, '', actorId)).rejects.toThrow(
        BadRequestException,
      );
    });

    it('rejects block when track is not PUBLISHED', async () => {
      mockTrack(TrackStatus.ON_MODERATION);

      await expect(service.block(trackId, 'reason', actorId)).rejects.toThrow(
        BadRequestException,
      );
    });

    it('unblock restores previous publication state (PUBLISHED)', async () => {
      mockTrack(TrackStatus.BLOCKED);

      await service.unblock(trackId, actorId);

      expect(prisma.track.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            status: TrackStatus.PUBLISHED,
          }),
        }),
      );
    });

    it('rejects unblock of a non-blocked track', async () => {
      mockTrack(TrackStatus.PUBLISHED);

      await expect(service.unblock(trackId, actorId)).rejects.toThrow(
        BadRequestException,
      );
    });
  });

  describe('invalid transitions are forbidden', () => {
    it('rejects PROCESSING -> PUBLISHED', async () => {
      mockTrack(TrackStatus.PROCESSING);

      await expect(service.publish(trackId, actorId)).rejects.toThrow(
        BadRequestException,
      );
    });

    it('rejects UPLOADED -> ON_MODERATION', async () => {
      mockTrack(TrackStatus.UPLOADED);

      await expect(
        service.markReadyForModeration(trackId, actorId),
      ).rejects.toThrow(BadRequestException);
    });
  });

  describe('audit log and cache', () => {
    it('invalidates catalog cache after every lifecycle change', async () => {
      mockTrack(TrackStatus.DRAFT);
      prisma.track.update.mockResolvedValue({});

      await service.markUploaded(
        trackId,
        'https://cdn.lumi.app/a.mp3',
        actorId,
      );

      expect(cache.invalidateCatalog).toHaveBeenCalled();
    });

    it('writes audit log with actor and reason', async () => {
      mockTrack(TrackStatus.PUBLISHED);
      prisma.track.update.mockResolvedValue({});

      await service.block(trackId, 'Abuse', actorId);

      expect(prisma.trackAuditLog.create).toHaveBeenCalledWith({
        data: {
          trackId,
          actorId,
          action: 'STATUS_CHANGED:PUBLISHED->BLOCKED',
          reason: 'Abuse',
        },
      });
    });
  });
});
