export default () => ({
  port: parseInt(process.env.PORT as string, 10) || 3000,
  nodeEnv: process.env.NODE_ENV || 'development',
});
