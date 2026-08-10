import {
  createParamDecorator,
  ExecutionContext,
  UnauthorizedException,
} from '@nestjs/common';

import { AuthenticatedRequest } from '../types/authenticated-request.type';
import { AccessTokenPayload } from '../types/token-payload.type';

export const CurrentUser = createParamDecorator(
  (
    property: keyof AccessTokenPayload | undefined,
    context: ExecutionContext,
  ) => {
    const request =
      context.switchToHttp().getRequest<AuthenticatedRequest>();

    if (!request.user) {
      throw new UnauthorizedException();
    }

    return property
      ? request.user[property]
      : request.user;
  },
);