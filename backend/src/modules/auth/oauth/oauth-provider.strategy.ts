export interface OAuthIdentity {
  provider: string;

  subject: string;

  email: string;
  emailVerified: boolean;

  displayName?: string;
  avatarUrl?: string;
}

export interface OAuthProviderStrategy {
  readonly name: string;

  getAuthorizationUrl(state: string): string;

  exchangeCode(code: string): Promise<OAuthIdentity>;
}
