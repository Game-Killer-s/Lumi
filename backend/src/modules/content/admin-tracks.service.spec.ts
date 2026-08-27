import { BadRequestException, NotFoundException } from '@nestjs/common';

import { CatalogCacheService } from '../catalog/catalog-cache.service';
import { PrismaService } from '../../prisma/prisma.service';
import { AdminTracksService } from './admin-tracks.service';

/** Мок PrismaService, типізований через jest.Mock */
type PrismaMock = {
  track: {
    findUnique: jest.Mock;
    update: jest.Mock;
  };
  artist: {
    findUnique: jest.Mock;
  };
  album: {
    findUnique: jest.Mock;
  };
  genre: {
    findUnique: jest.Mock;
  };
  trackAuditLog: {
    create: jest.Mock;
  };
};

/** Мок CatalogCacheService */
type CacheMock = {
  invalidateCatalog: jest.Mock;
};

describe('AdminTracksService', () => {
  let service: AdminTracksService;
  let prisma: PrismaMock;
  let cache: CacheMock;

  const trackId = 'track-1';
  const actorId = 'user-1';

  const baseTrack = {
    id: trackId,
    title: 'Old Title',
    duration: 200,
    lyrics: null,
    coverUrl: null,
    audioUrl: null,
    status: 'PUBLISHED',
    artistId: 'artist-1',
    albumId: null,
    genreId: 'genre-1',
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  beforeEach(() => {
    prisma = {
      track: {
        findUnique: jest.fn().mockResolvedValue(baseTrack),
        update: jest.fn().mockImplementation(({ data }) => ({
          ...baseTrack,
          ...data,
          artistId: data.artist ? data.artist.connect.id : baseTrack.artistId,
        })),
      },
      artist: {
        findUnique: jest.fn(),
      },
      album: {
        findUnique: jest.fn(),
      },
      genre: {
        findUnique: jest.fn(),
      },
      trackAuditLog: {
        create: jest.fn(),
      },
    };

    cache = {
      invalidateCatalog: jest.fn(),
    };

    service = new AdminTracksService(
      prisma as unknown as PrismaService,
      cache as unknown as CatalogCacheService,
    );
  });

  describe('metadata update', () => {
    it('updates scalar fields and writes audit diff', async () => {
      await service.updateMetadata(
        trackId,
        {
          title: 'New Title',
          duration: 300,
        },
        actorId,
      );

      expect(prisma.track.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            title: 'New Title',
            duration: 300,
          }),
        }),
      );

      expect(prisma.trackAuditLog.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            action: 'METADATA_UPDATED',
            details: expect.stringContaining('New Title'),
          }),
        }),
      );

      expect(cache.invalidateCatalog).toHaveBeenCalled();
    });

    it('does not write audit log when nothing changed', async () => {
      await service.updateMetadata(trackId, { title: 'Old Title' }, actorId);

      expect(prisma.trackAuditLog.create).not.toHaveBeenCalled();
    });

    it('throws NotFoundException when track is missing', async () => {
      prisma.track.findUnique.mockResolvedValue(null);

      await expect(
        service.updateMetadata(trackId, { title: 'X' }, actorId),
      ).rejects.toThrow(NotFoundException);
    });
  });

  describe('relation validation', () => {
    it('rejects unknown artist', async () => {
      prisma.artist.findUnique.mockResolvedValue(null);

      await expect(
        service.updateMetadata(trackId, { artistId: 'missing' }, actorId),
      ).rejects.toThrow(BadRequestException);
    });

    it('connects artist when it exists', async () => {
      prisma.artist.findUnique.mockResolvedValue({ id: 'artist-2' });

      await service.updateMetadata(trackId, { artistId: 'artist-2' }, actorId);

      expect(prisma.track.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            artist: { connect: { id: 'artist-2' } },
          }),
        }),
      );
    });

    it('rejects unknown album', async () => {
      prisma.album.findUnique.mockResolvedValue(null);

      await expect(
        service.updateMetadata(trackId, { albumId: 'missing' }, actorId),
      ).rejects.toThrow(BadRequestException);
    });

    it('rejects album that belongs to another artist', async () => {
      prisma.album.findUnique.mockResolvedValue({
        id: 'album-1',
        artistId: 'another-artist',
      });

      await expect(
        service.updateMetadata(trackId, { albumId: 'album-1' }, actorId),
      ).rejects.toThrow(BadRequestException);
    });

    it('connects album that belongs to the track artist', async () => {
      prisma.album.findUnique.mockResolvedValue({
        id: 'album-1',
        artistId: 'artist-1',
      });

      await service.updateMetadata(trackId, { albumId: 'album-1' }, actorId);

      expect(prisma.track.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            album: { connect: { id: 'album-1' } },
          }),
        }),
      );
    });

    it('rejects unknown genre', async () => {
      prisma.genre.findUnique.mockResolvedValue(null);

      await expect(
        service.updateMetadata(trackId, { genreId: 'missing' }, actorId),
      ).rejects.toThrow(BadRequestException);
    });

    it('connects genre when it exists', async () => {
      prisma.genre.findUnique.mockResolvedValue({ id: 'genre-2' });

      await service.updateMetadata(trackId, { genreId: 'genre-2' }, actorId);

      expect(prisma.track.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            genre: { connect: { id: 'genre-2' } },
          }),
        }),
      );
    });
  });

  describe('cover replacement', () => {
    it('updates coverUrl and writes audit entry', async () => {
      await service.replaceCover(
        trackId,
        'https://cdn.lumi.app/new-cover.jpg',
        actorId,
      );

      expect(prisma.track.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            coverUrl: 'https://cdn.lumi.app/new-cover.jpg',
          }),
        }),
      );

      expect(prisma.trackAuditLog.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            action: 'COVER_UPDATED',
            details: expect.stringContaining('new-cover.jpg'),
          }),
        }),
      );

      expect(cache.invalidateCatalog).toHaveBeenCalled();
    });

    it('throws NotFoundException when track is missing', async () => {
      prisma.track.findUnique.mockResolvedValue(null);

      await expect(
        service.replaceCover(trackId, 'https://x/c.jpg', actorId),
      ).rejects.toThrow(NotFoundException);
    });
  });
});
