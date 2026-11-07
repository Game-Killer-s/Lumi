import { INestApplication, Logger } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import { promises as fs } from 'node:fs';
import request from 'supertest';

import { AppModule } from '../src/app.module';
import { setupApp } from '../src/app.setup';
import { PasswordService } from '../src/modules/auth/services/password.service';
import { TokenService } from '../src/modules/auth/services/token.service';
import { MediaStorageService } from '../src/modules/media/media-storage.service';
import { PresenceRateLimiter } from '../src/modules/profile/presence-rate-limiter';
import { PrismaService } from '../src/prisma/prisma.service';

interface UserRow {
  id: string;
  email: string;
  passwordHash: string;
  nickname: string;
  role: 'LISTENER' | 'ADMIN';
  avatarUrl: string | null;
  isPublic: boolean;
  showCurrentListening: boolean;
  isBlocked: boolean;
  createdAt: Date;
  updatedAt: Date;
}

interface TrackRow {
  id: string;
  title: string;
  duration: number;
  coverUrl: string | null;
  status: 'PUBLISHED' | 'DRAFT' | 'BLOCKED';
  artist: { name: string };
}

interface PresenceRow {
  userId: string;
  trackId: string;
  updatedAt: Date;
}

interface SessionRow {
  id: string;
  userId: string;
  revokedAt: Date | null;
  expiresAt: Date;
}

interface ProfileBody {
  id: string;
  nickname: string;
  email: string;
  avatarUrl: string | null;
  role: string;
  isPublic: boolean;
  showCurrentListening: boolean;
  createdAt: string;
}

let users: Record<string, UserRow>;
let sessions: Record<string, SessionRow>;
let tracks: Record<string, TrackRow>;
let presence: Record<string, PresenceRow>;

function project(
  row: Record<string, unknown>,
  select?: Record<string, boolean>,
): Record<string, unknown> {
  if (!select) {
    return { ...row };
  }

  return Object.fromEntries(
    Object.keys(select)
      .filter((key) => select[key])
      .map((key) => [key, row[key]]),
  );
}

interface UserWhere {
  isPublic?: boolean;
  isBlocked?: boolean;
  nickname?: { contains: string };
}

function matchesUserWhere(user: UserRow, where?: UserWhere): boolean {
  if (!where) {
    return true;
  }

  if (where.isPublic !== undefined && user.isPublic !== where.isPublic) {
    return false;
  }

  if (where.isBlocked !== undefined && user.isBlocked !== where.isBlocked) {
    return false;
  }

  if (where.nickname) {
    return user.nickname
      .toLowerCase()
      .includes(where.nickname.contains.toLowerCase());
  }

  return true;
}

const userStub = {
  findUnique: (args: {
    where: { id?: string; email?: string };
    select?: Record<string, boolean>;
  }) => {
    const row = args.where.id
      ? users[args.where.id]
      : Object.values(users).find((user) => user.email === args.where.email);

    return Promise.resolve(
      row
        ? project(row as unknown as Record<string, unknown>, args.select)
        : null,
    );
  },
  findFirst: (args: {
    where: {
      nickname: { equals: string; mode: string };
      NOT: { id: string };
    };
    select?: Record<string, boolean>;
  }) => {
    const wanted = args.where.nickname.equals.toLowerCase();
    const row = Object.values(users).find(
      (user) =>
        user.id !== args.where.NOT.id && user.nickname.toLowerCase() === wanted,
    );

    return Promise.resolve(
      row
        ? project(row as unknown as Record<string, unknown>, args.select)
        : null,
    );
  },
  findMany: (args: {
    where?: UserWhere;
    select?: Record<string, boolean>;
    skip?: number;
    take?: number;
  }) => {
    const rows = Object.values(users)
      .filter((user) => matchesUserWhere(user, args.where))
      .sort((a, b) => a.nickname.localeCompare(b.nickname))
      .slice(args.skip ?? 0, (args.skip ?? 0) + (args.take ?? 1000));

    return Promise.resolve(
      rows.map((row) =>
        project(row as unknown as Record<string, unknown>, args.select),
      ),
    );
  },
  count: (args: { where?: UserWhere }) =>
    Promise.resolve(
      Object.values(users).filter((user) => matchesUserWhere(user, args.where))
        .length,
    ),
  update: (args: {
    where: { id: string };
    data: Partial<UserRow>;
    select?: Record<string, boolean>;
  }) => {
    const row = users[args.where.id];

    if (!row) {
      const error = new Error('User not found') as Error & { code: string };
      error.code = 'P2025';

      return Promise.reject(error);
    }

    Object.assign(row, args.data, { updatedAt: new Date() });

    return Promise.resolve(
      project(row as unknown as Record<string, unknown>, args.select),
    );
  },
};

const clientStub = {
  authSession: {
    findUnique: (args: { where: { id: string } }) => {
      const session = sessions[args.where.id];

      if (!session) {
        return Promise.resolve(null);
      }

      const user = users[session.userId];

      return Promise.resolve({
        ...session,
        user: { isBlocked: user.isBlocked, role: user.role },
      });
    },
    updateMany: (args: {
      where: {
        userId: string;
        id: string | { not: string };
        revokedAt: null;
      };
      data: { revokedAt: Date };
    }) => {
      const affected = Object.values(sessions).filter((session) => {
        const idMatches =
          typeof args.where.id === 'string'
            ? session.id === args.where.id
            : session.id !== args.where.id.not;

        return (
          session.userId === args.where.userId &&
          idMatches &&
          session.revokedAt === null
        );
      });

      affected.forEach((session) => Object.assign(session, args.data));

      return Promise.resolve({ count: affected.length });
    },
  },
};

const trackStub = {
  findUnique: (args: { where: { id: string } }) =>
    Promise.resolve(tracks[args.where.id] ?? null),
};

const currentListeningStub = {
  findUnique: (args: { where: { userId: string } }) => {
    const row = presence[args.where.userId];

    if (!row) {
      return Promise.resolve(null);
    }

    return Promise.resolve({
      ...row,
      track: tracks[row.trackId],
    });
  },
  upsert: (args: {
    where: { userId: string };
    create: PresenceRow;
    update: Partial<PresenceRow>;
  }) => {
    const existing = presence[args.where.userId];

    if (existing) {
      Object.assign(existing, args.update);
    } else {
      presence[args.where.userId] = { ...args.create };
    }

    return Promise.resolve(presence[args.where.userId]);
  },
  deleteMany: (args: { where: { userId: string } }) => {
    const existed = Boolean(presence[args.where.userId]);

    delete presence[args.where.userId];

    return Promise.resolve({ count: existed ? 1 : 0 });
  },
};

const prismaStub = {
  user: userStub,
  track: trackStub,
  currentListening: currentListeningStub,
  client: clientStub,
  transaction: (callback: (tx: unknown) => Promise<unknown>) =>
    callback({
      user: userStub,
      authSession: clientStub.authSession,
      currentListening: currentListeningStub,
    }),
};

const passwordStub = {
  hash: (password: string) => Promise.resolve(`hashed:${password}`),
  verify: (passwordHash: string, password: string) =>
    Promise.resolve(passwordHash === `hashed:${password}`),
};

const storageStub = {
  buildStorageKey: jest.fn(() => 'avatar/2026/10/new.png'),
  moveIntoStorage: jest.fn((tempPath: string) =>
    fs.rm(tempPath, { force: true }),
  ),
  remove: jest.fn(() => Promise.resolve()),
  removeTemp: jest.fn((tempPath: string) => fs.rm(tempPath, { force: true })),
};

function pngBuffer(width: number, height: number): Buffer {
  const buffer = Buffer.alloc(33);
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]).copy(buffer, 0);
  buffer.writeUInt32BE(13, 8);
  buffer.write('IHDR', 12, 'latin1');
  buffer.writeUInt32BE(width, 16);
  buffer.writeUInt32BE(height, 20);

  return buffer;
}

describe('Profile (e2e)', () => {
  let app: INestApplication;
  let ownToken: string;
  let secondSessionToken: string;
  let otherUserToken: string;
  let adminToken: string;

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    })
      .overrideProvider(PrismaService)
      .useValue(prismaStub)
      .overrideProvider(MediaStorageService)
      .useValue(storageStub)
      .overrideProvider(PasswordService)
      .useValue(passwordStub)
      .compile();

    app = moduleFixture.createNestApplication();
    setupApp(app);

    await app.init();

    const tokens = moduleFixture.get(TokenService);

    ownToken = await tokens.signAccessToken({
      sub: 'u1',
      sessionId: 's1',
      role: 'LISTENER',
      type: 'access',
    });

    secondSessionToken = await tokens.signAccessToken({
      sub: 'u1',
      sessionId: 's2',
      role: 'LISTENER',
      type: 'access',
    });

    otherUserToken = await tokens.signAccessToken({
      sub: 'u2',
      sessionId: 's3',
      role: 'LISTENER',
      type: 'access',
    });

    adminToken = await tokens.signAccessToken({
      sub: 'u3',
      sessionId: 's4',
      role: 'ADMIN',
      type: 'access',
    });
  });

  beforeEach(() => {
    users = {
      u1: {
        id: 'u1',
        email: 'vasyl@example.com',
        passwordHash: 'hashed:OldPassword123',
        nickname: 'Vasyl',
        role: 'LISTENER',
        avatarUrl: null,
        isPublic: true,
        showCurrentListening: true,
        isBlocked: false,
        createdAt: new Date('2026-10-01T10:00:00.000Z'),
        updatedAt: new Date('2026-10-01T10:00:00.000Z'),
      },
      u2: {
        id: 'u2',
        email: 'other@example.com',
        passwordHash: 'hashed:OtherPassword123',
        nickname: 'Other',
        role: 'LISTENER',
        avatarUrl: null,
        isPublic: true,
        showCurrentListening: true,
        isBlocked: false,
        createdAt: new Date('2026-10-02T10:00:00.000Z'),
        updatedAt: new Date('2026-10-02T10:00:00.000Z'),
      },
      u3: {
        id: 'u3',
        email: 'admin@example.com',
        passwordHash: 'hashed:AdminPassword123',
        nickname: 'Admin',
        role: 'ADMIN',
        avatarUrl: null,
        isPublic: true,
        showCurrentListening: true,
        isBlocked: false,
        createdAt: new Date('2026-10-03T10:00:00.000Z'),
        updatedAt: new Date('2026-10-03T10:00:00.000Z'),
      },
    };

    tracks = {
      t1: {
        id: 't1',
        title: 'After Hours',
        duration: 240,
        coverUrl: null,
        status: 'PUBLISHED',
        artist: { name: 'The Weeknd' },
      },
      t2: {
        id: 't2',
        title: 'Draft Track',
        duration: 180,
        coverUrl: null,
        status: 'DRAFT',
        artist: { name: 'Nobody' },
      },
    };

    presence = {};

    sessions = {
      s1: {
        id: 's1',
        userId: 'u1',
        revokedAt: null,
        expiresAt: new Date(Date.now() + 60_000),
      },
      s2: {
        id: 's2',
        userId: 'u1',
        revokedAt: null,
        expiresAt: new Date(Date.now() + 60_000),
      },
      s3: {
        id: 's3',
        userId: 'u2',
        revokedAt: null,
        expiresAt: new Date(Date.now() + 60_000),
      },
      s4: {
        id: 's4',
        userId: 'u3',
        revokedAt: null,
        expiresAt: new Date(Date.now() + 60_000),
      },
    };

    jest.clearAllMocks();
    app.get(PresenceRateLimiter).reset();
    storageStub.buildStorageKey.mockReturnValue('avatar/2026/10/new.png');
    storageStub.moveIntoStorage.mockImplementation((tempPath: string) =>
      fs.rm(tempPath, { force: true }),
    );
    storageStub.remove.mockResolvedValue(undefined);
    storageStub.removeTemp.mockImplementation((tempPath: string) =>
      fs.rm(tempPath, { force: true }),
    );
  });

  afterAll(async () => {
    await app.close();
  });

  it('returns only the current user profile', async () => {
    const response = await request(app.getHttpServer())
      .get('/api/profile')
      .set('Authorization', `Bearer ${ownToken}`)
      .expect(200);

    expect(response.body as ProfileBody).toEqual({
      id: 'u1',
      nickname: 'Vasyl',
      email: 'vasyl@example.com',
      avatarUrl: null,
      role: 'LISTENER',
      isPublic: true,
      showCurrentListening: true,
      createdAt: '2026-10-01T10:00:00.000Z',
    });
    expect(response.body).not.toHaveProperty('passwordHash');
  });

  it('requires a token to read the profile', async () => {
    await request(app.getHttpServer()).get('/api/profile').expect(401);
  });

  it('normalizes nickname and email on update', async () => {
    const response = await request(app.getHttpServer())
      .patch('/api/profile')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ nickname: '  New   Name ', email: ' New@Example.com ' })
      .expect(200);

    const body = response.body as ProfileBody;

    expect(body.nickname).toBe('New Name');
    expect(body.email).toBe('new@example.com');
    expect(users.u1.nickname).toBe('New Name');
    expect(users.u1.email).toBe('new@example.com');
  });

  it('returns 409 for a nickname of another user', async () => {
    users.u2.nickname = 'Taken';

    await request(app.getHttpServer())
      .patch('/api/profile')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ nickname: ' taken ' })
      .expect(409);

    expect(users.u1.nickname).toBe('Vasyl');
  });

  it('returns 409 for an email of another user', async () => {
    await request(app.getHttpServer())
      .patch('/api/profile')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ email: 'other@example.com' })
      .expect(409);

    expect(users.u1.email).toBe('vasyl@example.com');
  });

  it('rejects an invalid email and unknown fields', async () => {
    await request(app.getHttpServer())
      .patch('/api/profile')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ email: 'not-an-email' })
      .expect(400);

    await request(app.getHttpServer())
      .patch('/api/profile')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ isBlocked: true })
      .expect(400);

    await request(app.getHttpServer())
      .patch('/api/profile')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({})
      .expect(400);
  });

  it('does not change the password without the correct current one', async () => {
    await request(app.getHttpServer())
      .patch('/api/profile/password')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({
        currentPassword: 'WrongPassword123',
        newPassword: 'NewPassword123',
      })
      .expect(400);

    expect(users.u1.passwordHash).toBe('hashed:OldPassword123');
    expect(sessions.s2.revokedAt).toBeNull();
  });

  it('changes the password and revokes other sessions', async () => {
    await request(app.getHttpServer())
      .patch('/api/profile/password')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({
        currentPassword: 'OldPassword123',
        newPassword: 'NewPassword123',
      })
      .expect(204);

    expect(users.u1.passwordHash).toBe('hashed:NewPassword123');
    expect(sessions.s1.revokedAt).toBeNull();
    expect(sessions.s2.revokedAt).toBeInstanceOf(Date);

    await request(app.getHttpServer())
      .get('/api/profile')
      .set('Authorization', `Bearer ${secondSessionToken}`)
      .expect(401);

    await request(app.getHttpServer())
      .get('/api/profile')
      .set('Authorization', `Bearer ${ownToken}`)
      .expect(200);
  });

  it('rejects a new password that does not meet the policy', async () => {
    await request(app.getHttpServer())
      .patch('/api/profile/password')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ currentPassword: 'OldPassword123', newPassword: 'short' })
      .expect(400);
  });

  it('keeps other users profiles inaccessible', async () => {
    const response = await request(app.getHttpServer())
      .get('/api/profile')
      .set('Authorization', `Bearer ${otherUserToken}`)
      .expect(200);

    expect((response.body as ProfileBody).id).toBe('u2');
  });

  it('uploads an avatar and removes the previous file', async () => {
    users.u1.avatarUrl = '/media/avatar/2026/10/old.png';

    const response = await request(app.getHttpServer())
      .post('/api/profile/avatar')
      .set('Authorization', `Bearer ${ownToken}`)
      .attach('avatar', pngBuffer(128, 128), {
        filename: 'avatar.png',
        contentType: 'image/png',
      })
      .expect(200);

    expect((response.body as ProfileBody).avatarUrl).toBe(
      '/media/avatar/2026/10/new.png',
    );
    expect(users.u1.avatarUrl).toBe('/media/avatar/2026/10/new.png');
    expect(storageStub.moveIntoStorage).toHaveBeenCalledWith(
      expect.any(String),
      'avatar/2026/10/new.png',
    );
    expect(storageStub.remove).toHaveBeenCalledWith('avatar/2026/10/old.png');
  });

  it('rejects an avatar that is too small', async () => {
    await request(app.getHttpServer())
      .post('/api/profile/avatar')
      .set('Authorization', `Bearer ${ownToken}`)
      .attach('avatar', pngBuffer(32, 32), {
        filename: 'avatar.png',
        contentType: 'image/png',
      })
      .expect(400);

    expect(users.u1.avatarUrl).toBeNull();
    expect(storageStub.removeTemp).toHaveBeenCalled();
  });

  it('rejects an unsupported avatar format', async () => {
    await request(app.getHttpServer())
      .post('/api/profile/avatar')
      .set('Authorization', `Bearer ${ownToken}`)
      .attach('avatar', Buffer.from('GIF89a'), {
        filename: 'avatar.gif',
        contentType: 'image/gif',
      })
      .expect(400);
  });

  it('rejects a fake PNG and a missing file', async () => {
    await request(app.getHttpServer())
      .post('/api/profile/avatar')
      .set('Authorization', `Bearer ${ownToken}`)
      .attach('avatar', Buffer.from('not an image'), {
        filename: 'avatar.png',
        contentType: 'image/png',
      })
      .expect(400);

    await request(app.getHttpServer())
      .post('/api/profile/avatar')
      .set('Authorization', `Bearer ${ownToken}`)
      .expect(400);
  });

  it('requires a token to upload an avatar', async () => {
    await request(app.getHttpServer())
      .post('/api/profile/avatar')
      .attach('avatar', pngBuffer(128, 128), {
        filename: 'avatar.png',
        contentType: 'image/png',
      })
      .expect(401);
  });

  it('returns only the public profile projection to strangers', async () => {
    const response = await request(app.getHttpServer())
      .get('/api/profiles/u1')
      .expect(200);

    expect(response.body).toEqual({
      id: 'u1',
      nickname: 'Vasyl',
      avatarUrl: null,
      createdAt: '2026-10-01T10:00:00.000Z',
    });
    expect(response.body).not.toHaveProperty('email');
    expect(response.body).not.toHaveProperty('role');
    expect(response.body).not.toHaveProperty('isPublic');
    expect(response.body).not.toHaveProperty('showCurrentListening');
    expect(response.body).not.toHaveProperty('isBlocked');
    expect(response.body).not.toHaveProperty('passwordHash');
  });

  it('returns private fields to the owner via optional auth', async () => {
    const response = await request(app.getHttpServer())
      .get('/api/profiles/u1')
      .set('Authorization', `Bearer ${ownToken}`)
      .expect(200);

    expect(response.body).toHaveProperty('email', 'vasyl@example.com');
    expect(response.body).toHaveProperty('role', 'LISTENER');
    expect(response.body).toHaveProperty('isPublic', true);
  });

  it('returns private fields to an admin and logs the access', async () => {
    const logSpy = jest
      .spyOn(Logger.prototype, 'log')
      .mockImplementation(() => undefined);

    const response = await request(app.getHttpServer())
      .get('/api/profiles/u1')
      .set('Authorization', `Bearer ${adminToken}`)
      .expect(200);

    expect(response.body).toHaveProperty('email', 'vasyl@example.com');
    expect(logSpy).toHaveBeenCalledWith(
      'Admin profile access: actor=u3, target=u1',
    );
    expect(JSON.stringify(logSpy.mock.calls)).not.toContain(
      'vasyl@example.com',
    );
  });

  it('hides a private profile from strangers but not from owner/admin', async () => {
    users.u1.isPublic = false;

    await request(app.getHttpServer()).get('/api/profiles/u1').expect(404);

    await request(app.getHttpServer())
      .get('/api/profiles/u1')
      .set('Authorization', `Bearer ${otherUserToken}`)
      .expect(404);

    const owner = await request(app.getHttpServer())
      .get('/api/profiles/u1')
      .set('Authorization', `Bearer ${ownToken}`)
      .expect(200);

    expect(owner.body).toHaveProperty('email', 'vasyl@example.com');

    const admin = await request(app.getHttpServer())
      .get('/api/profiles/u1')
      .set('Authorization', `Bearer ${adminToken}`)
      .expect(200);

    expect(admin.body).toHaveProperty('email', 'vasyl@example.com');
  });

  it('hides blocked profiles from public lookup', async () => {
    users.u1.isBlocked = true;

    await request(app.getHttpServer()).get('/api/profiles/u1').expect(404);
  });

  it('searches only public and non-blocked profiles', async () => {
    users.u2.nickname = 'Vasyl Hidden';
    users.u2.isPublic = false;
    users.u3.nickname = 'Vasyl Blocked';
    users.u3.isBlocked = true;

    const response = await request(app.getHttpServer())
      .get('/api/profiles/search')
      .query({ q: 'vasyl' })
      .expect(200);

    expect(response.body).toEqual({
      items: [
        {
          id: 'u1',
          nickname: 'Vasyl',
          avatarUrl: null,
          createdAt: '2026-10-01T10:00:00.000Z',
        },
      ],
      page: 1,
      limit: 20,
      total: 1,
    });
    expect(JSON.stringify(response.body)).not.toContain('email');

    await request(app.getHttpServer())
      .get('/api/profiles/search')
      .query({ q: '   ' })
      .expect(400);
  });

  it('updates privacy flags and validates the privacy DTO', async () => {
    const response = await request(app.getHttpServer())
      .patch('/api/profile/privacy')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ isPublic: false, showCurrentListening: false })
      .expect(200);

    expect(response.body).toMatchObject({
      isPublic: false,
      showCurrentListening: false,
    });
    expect(users.u1.isPublic).toBe(false);
    expect(users.u1.showCurrentListening).toBe(false);

    await request(app.getHttpServer())
      .patch('/api/profile/privacy')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ isPublic: 'no' })
      .expect(400);

    await request(app.getHttpServer())
      .patch('/api/profile/privacy')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ isBlocked: false })
      .expect(400);
  });

  it('publishes current listening and exposes it publicly', async () => {
    const update = await request(app.getHttpServer())
      .put('/api/profile/current-listening')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ trackId: 't1' })
      .expect(200);

    expect(update.body).toMatchObject({
      trackId: 't1',
      visible: true,
    });
    expect(update.body).toHaveProperty('updatedAt');
    expect(update.body).toHaveProperty('expiresAt');

    const response = await request(app.getHttpServer())
      .get('/api/profiles/u1/current-listening')
      .expect(200);

    expect(response.body).toMatchObject({
      track: {
        id: 't1',
        title: 'After Hours',
        duration: 240,
        coverUrl: null,
        artistName: 'The Weeknd',
      },
    });
    expect(response.body).toHaveProperty('updatedAt');
    expect(response.body).toHaveProperty('expiresAt');
  });

  it('rejects missing and unpublished tracks in presence updates', async () => {
    await request(app.getHttpServer())
      .put('/api/profile/current-listening')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ trackId: 'missing' })
      .expect(404);

    await request(app.getHttpServer())
      .put('/api/profile/current-listening')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ trackId: 't2' })
      .expect(404);

    await request(app.getHttpServer())
      .put('/api/profile/current-listening')
      .send({ trackId: 't1' })
      .expect(401);
  });

  it('hides presence when showCurrentListening is disabled', async () => {
    await request(app.getHttpServer())
      .put('/api/profile/current-listening')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ trackId: 't1' })
      .expect(200);

    await request(app.getHttpServer())
      .patch('/api/profile/privacy')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ showCurrentListening: false })
      .expect(200);

    expect(presence.u1).toBeUndefined();

    const update = await request(app.getHttpServer())
      .put('/api/profile/current-listening')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ trackId: 't1' })
      .expect(200);

    expect(update.body.visible).toBe(false);

    await request(app.getHttpServer())
      .get('/api/profiles/u1/current-listening')
      .expect(404);

    await request(app.getHttpServer())
      .get('/api/profiles/u1/current-listening')
      .set('Authorization', `Bearer ${otherUserToken}`)
      .expect(404);

    await request(app.getHttpServer())
      .get('/api/profiles/u1/current-listening')
      .set('Authorization', `Bearer ${ownToken}`)
      .expect(200);
  });

  it('hides presence for a private profile but keeps it visible to the owner', async () => {
    await request(app.getHttpServer())
      .patch('/api/profile/privacy')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ isPublic: false })
      .expect(200);

    await request(app.getHttpServer())
      .put('/api/profile/current-listening')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ trackId: 't1' })
      .expect(200);

    await request(app.getHttpServer())
      .get('/api/profiles/u1/current-listening')
      .expect(404);

    await request(app.getHttpServer())
      .get('/api/profiles/u1/current-listening')
      .set('Authorization', `Bearer ${otherUserToken}`)
      .expect(404);

    await request(app.getHttpServer())
      .get('/api/profiles/u1/current-listening')
      .set('Authorization', `Bearer ${ownToken}`)
      .expect(200);
  });

  it('expires presence by TTL and removes the stale row', async () => {
    await request(app.getHttpServer())
      .put('/api/profile/current-listening')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ trackId: 't1' })
      .expect(200);

    presence.u1.updatedAt = new Date(Date.now() - 31 * 60 * 1000);

    await request(app.getHttpServer())
      .get('/api/profiles/u1/current-listening')
      .expect(404);

    expect(presence.u1).toBeUndefined();
  });

  it('clears presence on demand', async () => {
    await request(app.getHttpServer())
      .put('/api/profile/current-listening')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ trackId: 't1' })
      .expect(200);

    await request(app.getHttpServer())
      .delete('/api/profile/current-listening')
      .set('Authorization', `Bearer ${ownToken}`)
      .expect(204);

    expect(presence.u1).toBeUndefined();
  });

  it('clears presence on logout', async () => {
    await request(app.getHttpServer())
      .put('/api/profile/current-listening')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ trackId: 't1' })
      .expect(200);

    await request(app.getHttpServer())
      .post('/api/auth/logout')
      .set('Authorization', `Bearer ${ownToken}`)
      .expect(204);

    expect(presence.u1).toBeUndefined();
    expect(sessions.s1.revokedAt).toBeInstanceOf(Date);
  });

  it('clears presence when a banned user is rejected by the guard', async () => {
    await request(app.getHttpServer())
      .put('/api/profile/current-listening')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ trackId: 't1' })
      .expect(200);

    users.u1.isBlocked = true;

    await request(app.getHttpServer())
      .get('/api/profile')
      .set('Authorization', `Bearer ${ownToken}`)
      .expect(401);

    expect(presence.u1).toBeUndefined();
  });

  it('rate limits presence updates', async () => {
    for (let index = 0; index < 30; index += 1) {
      await request(app.getHttpServer())
        .put('/api/profile/current-listening')
        .set('Authorization', `Bearer ${ownToken}`)
        .send({ trackId: 't1' })
        .expect(200);
    }

    await request(app.getHttpServer())
      .put('/api/profile/current-listening')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ trackId: 't1' })
      .expect(429);
  });
});
