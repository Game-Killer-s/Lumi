import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import { AppModule } from '../src/app.module';

describe('Playback queue (e2e)', () => {
  let app: INestApplication;

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();

    app = moduleRef.createNestApplication();
    await app.init();
  });

  afterAll(async () => {
    await app.close();
  });

  it('returns the current playback queue', async () => {
    const response = await request(app.getHttpServer())
      .get('/queue')
      .expect(200);

    expect(response.body.total).toBe(3);
    expect(response.body.tracks).toHaveLength(3);
    expect(response.body.currentTrackId).toBe('track-1');
  });

  it('changes the current track', async () => {
    const response = await request(app.getHttpServer())
      .post('/queue/current/track-2')
      .expect(201);

    expect(response.body.currentTrackId).toBe('track-2');
  });
});
