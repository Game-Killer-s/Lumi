// Seed script - заповнює базу даних початковими (довідковими) даними.
// Запуск: npm run prisma:seed

import { PrismaClient } from '@prisma/client';

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
      where: { name: genre.name },
      update: {},
      create: genre,
    });
  }
  console.log('Жанри додано:', genres.length);

  // ============================================================
  // 2. Виконавці
  // ============================================================
  const artists = [
    { name: 'Luna Waves', description: 'Електронна музика для вечірнього настрою' },
    { name: 'Neon Sky', description: 'Синтвейв і ретро-електроніка' },
    { name: 'Midnight Jazz Club', description: 'Джазові композиції' },
    { name: 'Rock Legends', description: 'Класичний рок' },
  ];

  const createdArtists: { id: string; name: string }[] = [];
  for (const artist of artists) {

    // Перевіряємо, чи вже є такий виконавець
    const existing = await prisma.artist.findFirst({
      where: { name: artist.name },
    });

    if (existing) {
      createdArtists.push(existing);
      continue;
    }

    const created = await prisma.artist.create({
      data: artist,
    });
    createdArtists.push(created);
  }
  console.log('Виконавців додано:', createdArtists.length);


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

  for (const track of tracks) {
    const artist = await prisma.artist.findFirst({
      where: { name: track.artistName },
    });
    const genre = await prisma.genre.findFirst({
      where: { name: track.genreName },
    });

    if (!artist || !genre) {
      console.log('Пропускаємо трек (немає виконавця або жанру):', track.title);
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
  }
  console.log('Треків додано:', tracks.length);

  // ============================================================
  // 4. Адміністратор (початковий користувач)
  // ============================================================
  // УВАГА: пароль "admin123" - це приклад. В реальному проєкті
  // пароль має бути захешований (bcrypt/argon2).
  await prisma.user.upsert({
    where: { email: 'admin@lumi.app' },
    update: {},
    create: {
      email: 'admin@lumi.app',
      passwordHash: 'admin123',
      nickname: 'Admin',
      role: 'ADMIN',
    },
  });
  console.log('Адміністратора додано: admin@lumi.app');

  console.log('Наповнення бази даних завершено!');
}

main()
  .catch((e) => {
    console.error('Помилка під час наповнення бази:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
