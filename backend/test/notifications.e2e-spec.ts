import { INestApplication } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import request from 'supertest';

import { AppModule } from '../src/app.module';
import { setupApp } from '../src/app.setup';
import { TokenService } from '../src/modules/auth/services/token.service';
import { PrismaService } from '../src/prisma/prisma.service';

interface NotificationRow {
  id: string;
  userId: string;
  type: string;
  title: string;
  body: string;
  isRead: boolean;
  readAt: Date | null;
  createdAt: Date;
}

interface NotificationWhere {
  userId?: string;
  id?: string;
  isRead?: boolean;
}

interface NotificationItemBody {
  id: string;
  isRead: boolean;
  readAt: string | null;
}

interface NotificationListBody {
  items: NotificationItemBody[];
  total: number;
  page: number;
  limit: number;
  unread: number;
}

interface UnreadCountBody {
  unread: number;
}

interface PreferencesBody {
  new_release: boolean;
  artist_update: boolean;
  promo: boolean;
  system: boolean;
}

const USER_ID = 'u1';
const OTHER_USER_ID = 'u2';

// Сповіщення тримаємо в памʼяті — для e2e база не потрібна.
let rows: NotificationRow[] = [];

let settings: Record<string, boolean> = {};

function seed(): void {
  rows = [
    {
      id: 'n1',
      userId: USER_ID,
      type: 'new_release',
      title: 'Старий реліз',
      body: '',
      isRead: false,
      readAt: null,
      createdAt: new Date('2026-09-20T10:00:00.000Z'),
    },
    {
      id: 'n2',
      userId: USER_ID,
      type: 'new_release',
      title: 'Свіжий реліз',
      body: '',
      isRead: false,
      readAt: null,
      createdAt: new Date('2026-09-21T10:00:00.000Z'),
    },
    {
      id: 'n3',
      userId: USER_ID,
      type: 'subscription',
      title: 'Підписка',
      body: '',
      isRead: true,
      readAt: new Date('2026-09-19T09:00:00.000Z'),
      createdAt: new Date('2026-09-19T08:00:00.000Z'),
    },
    {
      id: 'n4',
      userId: OTHER_USER_ID,
      type: 'new_release',
      title: 'Чужий реліз',
      body: '',
      isRead: false,
      readAt: null,
      createdAt: new Date('2026-09-21T12:00:00.000Z'),
    },
  ];

  settings = {
    newReleases: true,
    artistUpdates: true,
    platformUpdates: true,
    systemNotifications: true,
    pushEnabled: true,
    emailEnabled: false,
  };
}

function matches(row: NotificationRow, where: NotificationWhere): boolean {
  if (where.userId && row.userId !== where.userId) {
    return false;
  }

  if (where.id && row.id !== where.id) {
    return false;
  }

  if (where.isRead !== undefined && row.isRead !== where.isRead) {
    return false;
  }

  return true;
}

const sessions = {
  s1: {
    id: 's1',
    userId: USER_ID,
    revokedAt: null,
    expiresAt: new Date(Date.now() + 60_000),
    user: { isBlocked: false, role: 'LISTENER' as const },
  },
  s2: {
    id: 's2',
    userId: OTHER_USER_ID,
    revokedAt: null,
    expiresAt: new Date(Date.now() + 60_000),
    user: { isBlocked: false, role: 'LISTENER' as const },
  },
};

const prismaStub = {
  client: {
    authSession: {
      findUnique: (args: { where: { id: string } }) =>
        Promise.resolve(sessions[args.where.id as 's1' | 's2'] ?? null),
    },
    userNotification: {
      findMany: (args: {
        where: NotificationWhere;
        skip?: number;
        take?: number;
      }) => {
        const from = args.skip ?? 0;

        return Promise.resolve(
          rows
            .filter((row) => matches(row, args.where))
            .sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime())
            .slice(from, from + (args.take ?? rows.length)),
        );
      },
      count: (args: { where: NotificationWhere }) =>
        Promise.resolve(rows.filter((row) => matches(row, args.where)).length),
      findFirst: (args: { where: NotificationWhere }) =>
        Promise.resolve(rows.find((row) => matches(row, args.where)) ?? null),
      update: (args: {
        where: { id: string };
        data: Partial<NotificationRow>;
      }) => {
        const row = rows.find((item) => item.id === args.where.id);
        Object.assign(row as NotificationRow, args.data);
        return Promise.resolve(row);
      },
      updateMany: (args: {
        where: NotificationWhere;
        data: Partial<NotificationRow>;
      }) => {
        const updated = rows.filter((row) => matches(row, args.where));
        updated.forEach((row) => Object.assign(row, args.data));
        return Promise.resolve({ count: updated.length });
      },
    },
    notificationSettings: {
      findUnique: () => Promise.resolve(settings),
      create: () => Promise.resolve(settings),
      update: (args: { data: Record<string, boolean> }) => {
        Object.assign(settings, args.data);
        return Promise.resolve({ ...settings });
      },
    },
  },
};

describe('Notifications (e2e)', () => {
  let app: INestApplication;
  let ownToken: string;
  let otherToken: string;

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    })
      .overrideProvider(PrismaService)
      .useValue(prismaStub)
      .compile();

    app = moduleFixture.createNestApplication();
    setupApp(app);

    await app.init();

    const tokens = moduleFixture.get(TokenService);

    ownToken = await tokens.signAccessToken({
      sub: USER_ID,
      sessionId: 's1',
      role: 'LISTENER',
      type: 'access',
    });

    otherToken = await tokens.signAccessToken({
      sub: OTHER_USER_ID,
      sessionId: 's2',
      role: 'LISTENER',
      type: 'access',
    });
  });

  beforeEach(() => {
    seed();
  });

  afterAll(async () => {
    await app.close();
  });

  it('returns only my notifications, newest first, with pagination', async () => {
    const response = await request(app.getHttpServer())
      .get('/api/notifications?page=1&limit=2')
      .set('Authorization', `Bearer ${ownToken}`)
      .expect(200);

    const body = response.body as NotificationListBody;
    const ids = body.items.map((item) => item.id);

    expect(ids).toEqual(['n2', 'n1']);
    expect(ids).not.toContain('n4');
    expect(body.total).toBe(3);
    expect(body.unread).toBe(2);
    expect(body.page).toBe(1);
    expect(body.limit).toBe(2);
  });

  it('counts unread notifications of the current user only', async () => {
    const own = await request(app.getHttpServer())
      .get('/api/notifications/unread-count')
      .set('Authorization', `Bearer ${ownToken}`)
      .expect(200);

    expect(own.body as UnreadCountBody).toEqual({ unread: 2 });

    const other = await request(app.getHttpServer())
      .get('/api/notifications/unread-count')
      .set('Authorization', `Bearer ${otherToken}`)
      .expect(200);

    expect(other.body as UnreadCountBody).toEqual({ unread: 1 });
  });

  it('does not allow marking a notification of another user as read', async () => {
    await request(app.getHttpServer())
      .patch('/api/notifications/n4/read')
      .set('Authorization', `Bearer ${ownToken}`)
      .expect(404);

    const foreign = rows.find((row) => row.id === 'n4');

    expect(foreign?.isRead).toBe(false);
    expect(foreign?.readAt).toBeNull();
  });

  it('stamps readAt once and keeps it on a repeated mark-read', async () => {
    const first = await request(app.getHttpServer())
      .patch('/api/notifications/n1/read')
      .set('Authorization', `Bearer ${ownToken}`)
      .expect(200);

    const firstBody = first.body as NotificationItemBody;

    expect(firstBody.isRead).toBe(true);
    expect(firstBody.readAt).toEqual(expect.any(String));

    const second = await request(app.getHttpServer())
      .patch('/api/notifications/n1/read')
      .set('Authorization', `Bearer ${ownToken}`)
      .expect(200);

    expect((second.body as NotificationItemBody).readAt).toBe(
      firstBody.readAt,
    );
  });

  it('marks all my notifications as read and leaves foreign ones unread', async () => {
    const response = await request(app.getHttpServer())
      .patch('/api/notifications/read-all')
      .set('Authorization', `Bearer ${ownToken}`)
      .expect(200);

    expect((response.body as { updated: number }).updated).toBe(2);

    const own = await request(app.getHttpServer())
      .get('/api/notifications/unread-count')
      .set('Authorization', `Bearer ${ownToken}`)
      .expect(200);

    expect(own.body as UnreadCountBody).toEqual({ unread: 0 });
    expect(rows.find((row) => row.id === 'n4')?.isRead).toBe(false);
  });

  it('returns my notification preferences with defaults', async () => {
    const response = await request(app.getHttpServer())
      .get('/api/notifications/preferences')
      .set('Authorization', `Bearer ${ownToken}`)
      .expect(200);

    expect(response.body as PreferencesBody).toEqual({
      new_release: true,
      artist_update: true,
      promo: true,
      system: true,
    });
  });

  it('updates preferences by type keys and rejects unknown keys', async () => {
    const updated = await request(app.getHttpServer())
      .patch('/api/notifications/preferences')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ new_release: false })
      .expect(200);

    const body = updated.body as PreferencesBody;

    expect(body.new_release).toBe(false);
    expect(body.system).toBe(true);

    await request(app.getHttpServer())
      .patch('/api/notifications/preferences')
      .set('Authorization', `Bearer ${ownToken}`)
      .send({ unknown_key: false })
      .expect(400);
  });

  it('requires a token to read notifications', async () => {
    await request(app.getHttpServer()).get('/api/notifications').expect(401);
  });
});
