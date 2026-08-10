import { Request } from 'express';

import { AccessTokenPayload } from './token-payload.type';

export type AuthenticatedRequest = Request & {
  user?: AccessTokenPayload;
};