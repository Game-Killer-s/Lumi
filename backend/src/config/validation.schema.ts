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
});
