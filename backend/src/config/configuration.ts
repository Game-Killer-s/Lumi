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
  database: {
    url: process.env.DATABASE_URL || 'postgresql://postgres:postgres@localhost:5432/lumi?schema=public',
  },
});
