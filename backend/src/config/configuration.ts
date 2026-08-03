export default () => ({
  port: parseInt(process.env.PORT as string, 10) || 3000,
  nodeEnv: process.env.NODE_ENV || 'development',
  database: {
    url: process.env.DATABASE_URL || 'postgresql://postgres:postgres@localhost:5432/lumi?schema=public',
  },
});
