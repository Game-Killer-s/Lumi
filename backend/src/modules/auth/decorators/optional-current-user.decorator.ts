import { createParamDecorator, ExecutionContext } from '@nestjs/common';

import { AuthenticatedRequest } from '../types/authenticated-request.type';
import { AccessTokenPayload } from '../types/token-payload.type';

// Для public endpoints з необов'язковою авторизацією.
export const OptionalCurrentUser = createParamDecorator(
  (
    property: keyof AccessTokenPayload | undefined,
    context: ExecutionContext,
  ):
    | AccessTokenPayload[keyof AccessTokenPayload]
    | AccessTokenPayload
    | null => {
    const request = context.switchToHttp().getRequest<AuthenticatedRequest>();
    const user = request.user;

    if (!user) {
      return null;
    }

    return property ? user[property] : user;
  },
);
