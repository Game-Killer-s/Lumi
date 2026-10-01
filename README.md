# Lumi — Playback Queue (NestJS)

Модуль для перегляду поточної черги відтворення.

## Реалізовано

- список треків;
- `currentTrackId` для підсвічування поточної композиції;
- автоматичне оновлення черги через WebSocket після зміни поточного треку;
- загальна кількість треків `total`;
- додавання та видалення треків;
- unit та e2e tests.

## HTTP API

```text
GET  /queue
POST /queue/current/:trackId
POST /queue/tracks
POST /queue/tracks/:trackId/remove
```

## WebSocket

Namespace:

```text
/queue
```

Events:

```text
queue:get
queue:current
queue:updated
```

Коли поточний трек змінюється через `queue:current`, сервер відправляє
`queue:updated` усім підключеним клієнтам.

## Запуск

```bash
npm install
npm run build
npm test
npm run test:e2e
npm run start:dev
```
