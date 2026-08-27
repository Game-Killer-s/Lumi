import { INestApplication } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import { Role, TrackStatus } from '@prisma/client';
import request from 'supertest';

import { AppModule } from '../src/app.module';
import { setupApp } from '../src/app.setup';
import { PrismaService } from '../src/prisma/prisma.service';
import { PasswordService } from '../src/modules/auth/services/password.service';

/** Тип тіла відповіді для тестів треку */
interface TrackBody {
  id: string;
  title: string;
  duration: number;
  coverUrl: string | null;
  status: TrackStatus;
  blockReason: string | null;
  audioUrl: string | null;
}

/**
 * E2E тести адміністрування контенту.
 *
 * Потрібна робоча база даних (PostgreSQL) із застосованими міграціями.
 * Тестові дані створюються в beforeAll і видаляються в afterAll.
 */
describe('Content admin (e2e)', () => {
  let app: INestApplication;
  let prisma: PrismaService;
  let passwordService: PasswordService;

  let adminToken: string;
  let listenerToken: string;

  let artistId: string;
  let albumId: string;
  let trackId: string;
  let draftTrackId: string;

  const suffix = Date.now();
  const listenerEmail = `listener-${suffix}@lumi.app`;

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();

    app = moduleFixture.createNestApplication();
    setupApp(app);
    await app.init();

    prisma = app.get(PrismaService);
    passwordService = app.get(PasswordService);

    // Створюємо звичайного слухача (для перевірки permissions)
    const listenerPasswordHash = await passwordService.hash('listener123');

    await prisma.user.create({
      data: {
        email: listenerEmail,
        passwordHash: listenerPasswordHash,
        nickname: 'E2E Listener',
        role: Role.LISTENER,
      },
    });

    // Тестові дані: виконавець, альбом і два треки
    const artist = await prisma.artist.create({
      data: {
        name: `E2E Artist ${suffix}`,
      },
    });
    artistId = artist.id;

    const genre = await prisma.genre.findFirstOrThrow();

    const album = await prisma.album.create({
      data: {
        title: `E2E Album ${suffix}`,
        artistId: artist.id,
      },
    });
    albumId = album.id;

    const track = await prisma.track.create({
      data: {
        title: `E2E Track ${suffix}`,
        duration: 200,
        status: TrackStatus.PUBLISHED,
        artistId: artist.id,
        genreId: genre.id,
        albumId: album.id,
        audioUrl: 'https://cdn.lumi.app/e2e.mp3',
      },
    });
    trackId = track.id;

    const draftTrack = await prisma.track.create({
      data: {
        title: `E2E Draft ${suffix}`,
        duration: 120,
        status: TrackStatus.DRAFT,
        artistId: artist.id,
      },
    });
    draftTrackId = draftTrack.id;

    adminToken = await login('admin@lumi.app', 'admin123');

    listenerToken = await login(listenerEmail, 'listener123');
  });

  async function login(email: string, password: string) {
    const response = await request(app.getHttpServer())
      .post('/api/auth/login')
      .send({ login: email, password })
      .expect(200);

    return (response.body as { accessToken: string }).accessToken;
  }

  afterAll(async () => {
    await prisma.track.deleteMany({
      where: {
        id: {
          in: [trackId, draftTrackId],
        },
      },
    });

    await prisma.album.deleteMany({
      where: {
        id: albumId,
      },
    });

    await prisma.artist.deleteMany({
      where: {
        id: artistId,
      },
    });

    await prisma.user.deleteMany({
      where: {
        email: listenerEmail,
      },
    });

    await app.close();
  });

  describe('permissions', () => {
    it('returns 401 without a token', async () => {
      await request(app.getHttpServer())
        .patch(`/api/admin/tracks/${trackId}`)
        .send({ title: 'Hack' })
        .expect(401);
    });

    it('returns 403 for a LISTENER on metadata update', async () => {
      await request(app.getHttpServer())
        .patch(`/api/admin/tracks/${trackId}`)
        .set('Authorization', `Bearer ${listenerToken}`)
        .send({ title: 'Hack' })
        .expect(403);
    });

    it('returns 403 for a LISTENER on lifecycle publish', async () => {
      await request(app.getHttpServer())
        .post(`/api/admin/tracks/${draftTrackId}/publish`)
        .set('Authorization', `Bearer ${listenerToken}`)
        .expect(403);
    });

    it('returns 403 for a LISTENER on block', async () => {
      await request(app.getHttpServer())
        .post(`/api/admin/tracks/${trackId}/block`)
        .set('Authorization', `Bearer ${listenerToken}`)
        .send({ reason: 'Abuse' })
        .expect(403);
    });
  });

  describe('metadata update', () => {
    it('updates track metadata for an admin', async () => {
      const response = await request(app.getHttpServer())
        .patch(`/api/admin/tracks/${trackId}`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          title: `E2E Track Updated ${suffix}`,
          duration: 250,
        })
        .expect(200);

      const body = response.body as TrackBody;

      expect(body.title).toBe(`E2E Track Updated ${suffix}`);
      expect(body.duration).toBe(250);
    });

    it('rejects unknown artist relation', async () => {
      await request(app.getHttpServer())
        .patch(`/api/admin/tracks/${trackId}`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          artistId: '00000000-0000-0000-0000-000000000000',
        })
        .expect(400);
    });

    it('rejects album that belongs to another artist', async () => {
      const otherArtist = await prisma.artist.create({
        data: {
          name: `E2E Other Artist ${suffix}`,
        },
      });

      const foreignAlbum = await prisma.album.create({
        data: {
          title: `E2E Foreign Album ${suffix}`,
          artistId: otherArtist.id,
        },
      });

      await request(app.getHttpServer())
        .patch(`/api/admin/tracks/${trackId}`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ albumId: foreignAlbum.id })
        .expect(400);

      await prisma.album.delete({
        where: {
          id: foreignAlbum.id,
        },
      });

      await prisma.artist.delete({
        where: {
          id: otherArtist.id,
        },
      });
    });

    it('rejects unknown genre relation', async () => {
      await request(app.getHttpServer())
        .patch(`/api/admin/tracks/${trackId}`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          genreId: '00000000-0000-0000-0000-000000000000',
        })
        .expect(400);
    });

    it('replaces cover image for an admin', async () => {
      const response = await request(app.getHttpServer())
        .patch(`/api/admin/tracks/${trackId}/cover`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          coverUrl: 'https://cdn.lumi.app/covers/e2e.jpg',
        })
        .expect(200);

      const body = response.body as TrackBody;

      expect(body.coverUrl).toBe('https://cdn.lumi.app/covers/e2e.jpg');
    });

    it('writes audit log for metadata change', async () => {
      const logs = await prisma.trackAuditLog.findMany({
        where: {
          trackId,
          action: 'METADATA_UPDATED',
        },
      });

      expect(logs.length).toBeGreaterThan(0);
      expect(logs[0].actorId).toBeDefined();
      expect(logs[0].details).toContain('title');
    });
  });

  describe('content lifecycle', () => {
    it('runs upload -> processing -> moderation -> publish', async () => {
      const upload = await request(app.getHttpServer())
        .post(`/api/admin/tracks/${draftTrackId}/upload`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          audioUrl: 'https://cdn.lumi.app/draft.mp3',
        })
        .expect(201);

      expect((upload.body as TrackBody).status).toBe(TrackStatus.UPLOADED);

      const processing = await request(app.getHttpServer())
        .post(`/api/admin/tracks/${draftTrackId}/processing`)
        .set('Authorization', `Bearer ${adminToken}`)
        .expect(201);

      expect((processing.body as TrackBody).status).toBe(
        TrackStatus.PROCESSING,
      );

      const moderation = await request(app.getHttpServer())
        .post(`/api/admin/tracks/${draftTrackId}/moderation`)
        .set('Authorization', `Bearer ${adminToken}`)
        .expect(201);

      expect((moderation.body as TrackBody).status).toBe(
        TrackStatus.ON_MODERATION,
      );

      const published = await request(app.getHttpServer())
        .post(`/api/admin/tracks/${draftTrackId}/publish`)
        .set('Authorization', `Bearer ${adminToken}`)
        .expect(201);

      expect((published.body as TrackBody).status).toBe(TrackStatus.PUBLISHED);
    });

    it('does not publish a track that has no audio (no ready variants)', async () => {
      const noAudioTrack = await prisma.track.create({
        data: {
          title: `E2E No Audio ${suffix}`,
          duration: 100,
          status: TrackStatus.PROCESSING,
          artistId: artistId,
        },
      });

      await request(app.getHttpServer())
        .post(`/api/admin/tracks/${noAudioTrack.id}/moderation`)
        .set('Authorization', `Bearer ${adminToken}`)
        .expect(400);

      await prisma.track.delete({
        where: {
          id: noAudioTrack.id,
        },
      });
    });

    it('forbids invalid lifecycle transition (publish while UPLOADED)', async () => {
      const invalidTrack = await prisma.track.create({
        data: {
          title: `E2E Invalid ${suffix}`,
          duration: 100,
          status: TrackStatus.UPLOADED,
          artistId: artistId,
        },
      });

      await request(app.getHttpServer())
        .post(`/api/admin/tracks/${invalidTrack.id}/publish`)
        .set('Authorization', `Bearer ${adminToken}`)
        .expect(400);

      await prisma.track.delete({
        where: {
          id: invalidTrack.id,
        },
      });
    });
  });

  describe('block/unblock', () => {
    it('requires a reason for block', async () => {
      await request(app.getHttpServer())
        .post(`/api/admin/tracks/${trackId}/block`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({})
        .expect(400);
    });

    it('blocks a track with reason', async () => {
      const response = await request(app.getHttpServer())
        .post(`/api/admin/tracks/${trackId}/block`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ reason: 'Copyright violation' })
        .expect(201);

      const body = response.body as TrackBody;

      expect(body.status).toBe(TrackStatus.BLOCKED);
      expect(body.blockReason).toBe('Copyright violation');
    });

    it('hides blocked track from listener endpoints', async () => {
      // catalog detail
      await request(app.getHttpServer())
        .get(`/api/tracks/${trackId}`)
        .expect(404);

      // stream
      await request(app.getHttpServer())
        .get(`/api/tracks/stream/${trackId}`)
        .expect(404);

      // search
      const search = await request(app.getHttpServer())
        .get(`/api/tracks/search`)
        .query({ q: `E2E Track Updated ${suffix}` })
        .expect(200);

      const searchBody = search.body as TrackBody[];
      expect(searchBody.some((track) => track.id === trackId)).toBe(false);

      // catalog list
      const catalog = await request(app.getHttpServer())
        .get('/api/tracks')
        .expect(200);

      const catalogBody = catalog.body as TrackBody[];
      expect(catalogBody.some((track) => track.id === trackId)).toBe(false);
    });

    it('writes audit log with actor and reason on block', async () => {
      const logs = await prisma.trackAuditLog.findMany({
        where: {
          trackId,
          action: 'STATUS_CHANGED:PUBLISHED->BLOCKED',
        },
      });

      expect(logs.length).toBeGreaterThan(0);
      expect(logs[0].reason).toBe('Copyright violation');
      expect(logs[0].createdAt).toBeDefined();
    });

    it('unblocks a track and restores PUBLISHED state', async () => {
      const response = await request(app.getHttpServer())
        .post(`/api/admin/tracks/${trackId}/unblock`)
        .set('Authorization', `Bearer ${adminToken}`)
        .expect(201);

      const body = response.body as TrackBody;

      expect(body.status).toBe(TrackStatus.PUBLISHED);
    });

    it('shows unblocked track in listener endpoints again', async () => {
      const detail = await request(app.getHttpServer())
        .get(`/api/tracks/${trackId}`)
        .expect(200);

      const detailBody = detail.body as TrackBody;
      expect(detailBody.id).toBe(trackId);

      const stream = await request(app.getHttpServer())
        .get(`/api/tracks/stream/${trackId}`)
        .expect(200);

      const streamBody = stream.body as TrackBody;
      expect(streamBody.audioUrl).toBeDefined();
    });
  });

  describe('listener catalog visibility', () => {
    it('returns only PUBLISHED tracks from catalog list', async () => {
      const catalog = await request(app.getHttpServer())
        .get('/api/tracks')
        .expect(200);

      const catalogBody = catalog.body as TrackBody[];

      expect(
        catalogBody.every((track) => track.status === TrackStatus.PUBLISHED),
      ).toBe(true);
    });
  });
});
