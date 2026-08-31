import { INestApplication } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';

import type { Server } from 'node:http';

import request from 'supertest';

import { AppModule } from '../src/app.module';
import { setupApp } from '../src/app.setup';

describe('HealthController (e2e)', () => {
  let app: INestApplication;

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();

    app = moduleFixture.createNestApplication();

    setupApp(app);

    await app.init();
  });

  afterAll(async () => {
    await app.close();
  });

  it('GET /api/health returns 200', async () => {
    /*
     * Nest повертає getHttpServer() як any.
     *
     * Спочатку переводимо в unknown,
     * а потім у конкретний Node Server.
     * Так ми не протягуємо any у Supertest.
     */
    const rawServer: unknown = app.getHttpServer();

    const httpServer = rawServer as Server;

    await request(httpServer)
      .get('/api/health')
      .expect(200)
      .expect('Content-Type', /json/);
  });
});
