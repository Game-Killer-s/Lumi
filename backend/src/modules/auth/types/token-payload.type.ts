export interface AccessTokenPayload {
  sub: string;
  sessionId: string;
  role: string;
  type: 'access';
}

export interface RefreshTokenPayload {
  sub: string;
  sessionId: string;
  familyId: string;
  type: 'refresh';
}