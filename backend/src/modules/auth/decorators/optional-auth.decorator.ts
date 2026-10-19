import { SetMetadata } from '@nestjs/common';

export const IS_OPTIONAL_AUTH_KEY = 'isOptionalAuth';

// Публічний endpoint, який додатково розпізнає валідний access token.
export const OptionalAuth = () => SetMetadata(IS_OPTIONAL_AUTH_KEY, true);
