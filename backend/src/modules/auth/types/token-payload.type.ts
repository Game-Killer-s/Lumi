import { Role } from '@prisma/client';

export interface AccessTokenPayload {
  sub: string;
  sessionId: string;
  role: Role;
  type: 'access';
}

export interface RefreshTokenPayload {
  sub: string;
  sessionId: string;
  familyId: string;
  jti: string;
  type: 'refresh';
}
