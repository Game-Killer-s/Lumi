import { INestApplication, ValidationPipe } from '@nestjs/common';

import helmet from 'helmet';

export function setupApp(app: INestApplication): void {
  /*
   * Helmet ставимо першим.
   *
   * CSP у development відключено,
   * щоб не ламати Swagger UI.
   */
  app.use(
    helmet({
      contentSecurityPolicy:
        process.env.NODE_ENV === 'production' ? undefined : false,
    }),
  );

  app.setGlobalPrefix('api');

  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      transform: true,
      forbidNonWhitelisted: true,
    }),
  );
}
