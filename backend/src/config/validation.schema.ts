import * as Joi from 'joi';

export const validationSchema = Joi.object({
  PORT: Joi.number().port().default(3000),

  NODE_ENV: Joi.string()
    .valid('development', 'production', 'test')
    .default('development'),

  JWT_ACCESS_SECRET: Joi.string().min(32).required(),
  JWT_REFRESH_SECRET: Joi.string().min(32).required(),

  JWT_ACCESS_TTL_SECONDS: Joi.number().integer().positive().default(900),

  JWT_REFRESH_TTL_SECONDS: Joi.number().integer().positive().default(2592000),

  JWT_ISSUER: Joi.string().default('lumi-api'),
  JWT_AUDIENCE: Joi.string().default('lumi-android'),
  DATABASE_URL: Joi.string().required(),
  CACHE_STALE_WINDOW_MS: Joi.number().integer().min(1000).default(60000),
  SLOW_REQUEST_THRESHOLD_MS: Joi.number().integer().min(1).default(500),
  SLOW_QUERY_THRESHOLD_MS: Joi.number().integer().min(1).default(100),

  // Google Sign-In
  GOOGLE_CLIENT_ID: Joi.string().allow('').default(''),

  // Email sending (used for password reset)
  MAIL_HOST: Joi.string().allow('').default(''),
  MAIL_PORT: Joi.number().port().default(587),
  MAIL_USER: Joi.string().allow('').default(''),
  MAIL_PASS: Joi.string().allow('').default(''),
  MAIL_FROM: Joi.string().allow('').default('Lumi <no-reply@lumi.app>'),

  // Медіа: каталог для завантажених файлів і цільова гучність.
  UPLOAD_DIR: Joi.string().default('storage'),
  MEDIA_TARGET_LUFS: Joi.number().min(-30).max(-5).default(-14),

  // Платежі (Stripe / LiqPay)
  PAYMENT_PROVIDER: Joi.string()
    .valid('card', 'stripe', 'liqpay')
    .default('card'),

  PAYMENT_WEBHOOK_SECRET: Joi.string().min(8).default('lumi-webhook-secret'),

  // Presence "слухає зараз".
  CURRENT_LISTENING_TTL_SECONDS: Joi.number().integer().min(60).default(1800),
  PRESENCE_RATE_LIMIT_MAX: Joi.number().integer().min(1).default(30),
  PRESENCE_RATE_LIMIT_WINDOW_MS: Joi.number()
    .integer()
    .min(1000)
    .default(60000),
});
