export default () => ({
  port: Number.parseInt(process.env.PORT ?? '3000', 10),
  nodeEnv: process.env.NODE_ENV ?? 'development',

  jwt: {
    accessSecret: process.env.JWT_ACCESS_SECRET,
    refreshSecret: process.env.JWT_REFRESH_SECRET,

    accessTtlSeconds: Number.parseInt(
      process.env.JWT_ACCESS_TTL_SECONDS ?? '900',
      10,
    ),

    refreshTtlSeconds: Number.parseInt(
      process.env.JWT_REFRESH_TTL_SECONDS ?? '2592000',
      10,
    ),
    issuer: process.env.JWT_ISSUER ?? 'lumi-api',
    audience: process.env.JWT_AUDIENCE ?? 'lumi-android',
  },
  database: {
    url:
      process.env.DATABASE_URL ||
      'postgresql://postgres:postgres@localhost:5432/lumi?schema=public',
  },

  cache: {
    staleWindowMs: Number.parseInt(
      process.env.CACHE_STALE_WINDOW_MS ?? '60000',
      10,
    ),
  },

  google: {
    clientId: process.env.GOOGLE_CLIENT_ID || '',
  },

  mail: {
    host: process.env.MAIL_HOST || '',
    port: Number.parseInt(process.env.MAIL_PORT ?? '587', 10),
    user: process.env.MAIL_USER || '',
    pass: process.env.MAIL_PASS || '',
    from: process.env.MAIL_FROM || 'Lumi <no-reply@lumi.app>',
  },

  metrics: {
    slowRequestThresholdMs: Number.parseInt(
      process.env.SLOW_REQUEST_THRESHOLD_MS ?? '500',
      10,
    ),

    slowQueryThresholdMs: Number.parseInt(
      process.env.SLOW_QUERY_THRESHOLD_MS ?? '100',
      10,
    ),
  },

  payments: {
    provider: process.env.PAYMENT_PROVIDER ?? 'card',

    // Ним платіжний шлюз підписує webhook-и.
    webhookSecret:
      process.env.PAYMENT_WEBHOOK_SECRET || 'lumi-webhook-secret',
  },
});
