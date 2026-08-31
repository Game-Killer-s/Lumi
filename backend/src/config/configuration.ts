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

  security: {
    twoFactorEncryptionKey: process.env.TWO_FACTOR_ENCRYPTION_KEY,

    hmacKey: process.env.SECURITY_HMAC_KEY,

    twoFactorIssuer: process.env.TWO_FACTOR_ISSUER ?? 'Lumi',
  },

  oauth: {
    allowedReturnUrls: (process.env.OAUTH_ALLOWED_RETURN_URLS ?? '')
      .split(',')
      .map((value) => value.trim())
      .filter(Boolean),

    google: {
      clientId: process.env.GOOGLE_OAUTH_CLIENT_ID,

      clientSecret: process.env.GOOGLE_OAUTH_CLIENT_SECRET,

      callbackUrl: process.env.GOOGLE_OAUTH_CALLBACK_URL,
    },
  },
});
