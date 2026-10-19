import { NestFactory } from '@nestjs/core';
import { ConfigService } from '@nestjs/config';
import { NestExpressApplication } from '@nestjs/platform-express';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { resolve } from 'node:path';

import { AppModule } from './app.module';
import { setupApp } from './app.setup';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create<NestExpressApplication>(AppModule);
  const configService = app.get(ConfigService);

  setupApp(app);

  // Медіафайли (аудіо та обкладинки) віддаються статикою під /media/*.
  // У БД зберігається шлях виду /media/audio/2026/10/<uuid>.flac
  // (див. mediaPublicUrl), тому префікс тут має збігатися.
  // Статика не потрапляє під global prefix 'api' - це окремий middleware.
  app.useStaticAssets(
    resolve(configService.get<string>('storage.uploadDir') ?? 'storage'),
    { prefix: '/media/', index: false },
  );

  const swaggerConfig = new DocumentBuilder()
    .setTitle('Lumi API')
    .setDescription('Lumi backend API documentation')
    .setVersion('1.0')
    .addBearerAuth(
      {
        type: 'http',
        scheme: 'bearer',
        bearerFormat: 'JWT',
      },
      'access-token',
    )
    .build();

  const document = SwaggerModule.createDocument(app, swaggerConfig);
  SwaggerModule.setup('api/docs', app, document);

  const port = configService.get<number>('port') ?? 3000;

  await app.listen(port);

  console.log(`Lumi API is running on http://localhost:${port}/api`);
  console.log(`Swagger is available on http://localhost:${port}/api/docs`);
}

void bootstrap();
