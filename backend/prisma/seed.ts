// Seed script - заповнює базу даних початковими (довідковими) даними.
// Запуск: npm run prisma:seed

import { PrismaClient } from '@prisma/client';
import * as argon2 from 'argon2';

const prisma = new PrismaClient();

async function main() {
  console.log('Починаємо наповнення бази даних...');

  // ============================================================
  // 1. Жанри музики
  // ============================================================

  const genres = [
    { name: 'Lo-Fi', color: '#7C4DFF' },
    { name: 'Rock', color: '#FF5252' },
    { name: 'Electronic', color: '#40C4FF' },
    { name: 'Pop', color: '#FF4081' },
    { name: 'Hip-Hop', color: '#FFD740' },
    { name: 'Jazz', color: '#69F0AE' },
    { name: 'Classical', color: '#B388FF' },
    { name: 'Techno', color: '#18FFFF' },
  ];

  for (const genre of genres) {
    await prisma.genre.upsert({
      where: {
        name: genre.name,
      },
      update: {
        color: genre.color,
      },
      create: genre,
    });
  }

  console.log('Жанри додано/оновлено:', genres.length);

  // ============================================================
  // 2. Виконавці
  // ============================================================

  const artists = [
    {
      name: 'Luna Waves',
      description: 'Електронна музика для вечірнього настрою',
    },
    {
      name: 'Neon Sky',
      description: 'Синтвейв і ретро-електроніка',
    },
    {
      name: 'Midnight Jazz Club',
      description: 'Джазові композиції',
    },
    {
      name: 'Rock Legends',
      description: 'Класичний рок',
    },
  ];

  const createdArtists: {
    id: string;
    name: string;
  }[] = [];

  for (const artist of artists) {
    const existing = await prisma.artist.findFirst({
      where: {
        name: artist.name,
      },
    });

    if (existing) {
      // Оновлюємо опис, щоб seed залишався актуальним.
      const updated = await prisma.artist.update({
        where: {
          id: existing.id,
        },
        data: {
          description: artist.description,
        },
      });

      createdArtists.push(updated);
      continue;
    }

    const created = await prisma.artist.create({
      data: artist,
    });

    createdArtists.push(created);
  }

  console.log(
    'Виконавців додано/оновлено:',
    createdArtists.length,
  );

  // ============================================================
  // 3. Треки
  // ============================================================

  const tracks = [
    {
      title: 'Midnight Drive',
      duration: 214,
      artistName: 'Luna Waves',
      genreName: 'Electronic',
    },
    {
      title: 'Neon Dreams',
      duration: 187,
      artistName: 'Neon Sky',
      genreName: 'Electronic',
    },
    {
      title: 'Coffee & Rain',
      duration: 245,
      artistName: 'Midnight Jazz Club',
      genreName: 'Jazz',
    },
    {
      title: 'Thunder Road',
      duration: 268,
      artistName: 'Rock Legends',
      genreName: 'Rock',
    },
    {
      title: 'City Lights',
      duration: 201,
      artistName: 'Luna Waves',
      genreName: 'Electronic',
    },
    {
      title: 'Sunset Boulevard',
      duration: 233,
      artistName: 'Neon Sky',
      genreName: 'Electronic',
    },
  ];

  let tracksCreated = 0;
  let tracksUpdated = 0;

  for (const track of tracks) {
    const artist = await prisma.artist.findFirst({
      where: {
        name: track.artistName,
      },
    });

    const genre = await prisma.genre.findFirst({
      where: {
        name: track.genreName,
      },
    });

    if (!artist || !genre) {
      console.log(
        'Пропускаємо трек (немає виконавця або жанру):',
        track.title,
      );

      continue;
    }

    // Не створюємо дублікати при повторному запуску seed.
    const existingTrack = await prisma.track.findFirst({
      where: {
        title: track.title,
        artistId: artist.id,
      },
    });

    if (existingTrack) {
      await prisma.track.update({
        where: {
          id: existingTrack.id,
        },
        data: {
          duration: track.duration,
          genreId: genre.id,
          status: 'PUBLISHED',
        },
      });

      tracksUpdated++;
      continue;
    }

    await prisma.track.create({
      data: {
        title: track.title,
        duration: track.duration,
        artistId: artist.id,
        genreId: genre.id,
        status: 'PUBLISHED',
      },
    });

    tracksCreated++;
  }

  console.log(
    `Треки: створено ${tracksCreated}, оновлено ${tracksUpdated}`,
  );

  // ============================================================
  // 4. Тарифні плани підписки
  // ============================================================

  const plans = [
    {
      code: 'free',
      title: 'Безкоштовний',
      description: 'Каталог зі звуком у стандартній якості',
      priceCents: 0,
      periodDays: 30,
    },
    {
      code: 'premium',
      title: 'Premium',
      description: 'HQ-аудіопотік без реклами та офлайн-режим',
      priceCents: 14900,
      periodDays: 30,
    },
    {
      code: 'family',
      title: 'Сімейний',
      description: 'Premium для 6 акаунтів',
      priceCents: 34900,
      periodDays: 30,
    },
  ];

  for (const plan of plans) {
    await prisma.subscriptionPlan.upsert({
      where: {
        code: plan.code,
      },
      update: {
        title: plan.title,
        description: plan.description,
        priceCents: plan.priceCents,
        periodDays: plan.periodDays,
        isActive: true,
      },
      create: {
        ...plan,
        currency: 'UAH',
      },
    });
  }

  console.log('Тарифні плани додано/оновлено:', plans.length);

  // ============================================================
  // 5. Тимчасовий адміністратор для development
  // ============================================================

  /*
   * TODO(auth):
   * Видалити цей bootstrap admin після реалізації
   * нормального механізму створення та керування
   * адміністраторами.
   *
   * У production цей користувач через seed
   * взагалі не створюється.
   */

  if (process.env.NODE_ENV !== 'production') {
    const adminPasswordHash = await argon2.hash(
      'admin123',
      {
        type: argon2.argon2id,
        memoryCost: 19_456,
        timeCost: 2,
        parallelism: 1,
      },
    );

    await prisma.user.upsert({
      where: {
        email: 'admin@lumi.app',
      },

      // Важливо:
      // якщо admin уже був створений старим seed,
      // його plaintext passwordHash буде замінено
      // нормальним Argon2id hash.
      update: {
        passwordHash: adminPasswordHash,
        nickname: 'Admin',
        role: 'ADMIN',
        isBlocked: false,
      },

      create: {
        email: 'admin@lumi.app',
        passwordHash: adminPasswordHash,
        nickname: 'Admin',
        role: 'ADMIN',
        isBlocked: false,
      },
    });

    console.log(
      'Development admin додано/оновлено: admin@lumi.app',
    );
  } else {
    console.log(
      'Production mode: development admin не створюється.',
    );
  }

  // ============================================================
  // Завершення
  // ============================================================

  console.log('Наповнення бази даних завершено!');
}

main()
  .catch((error) => {
    console.error(
      'Помилка під час наповнення бази:',
      error,
    );

    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });