import { BadGatewayException, Injectable } from '@nestjs/common';

import { ConfigService } from '@nestjs/config';

import type {
  OAuthIdentity,
  OAuthProviderStrategy,
} from './oauth-provider.strategy';

interface GoogleTokenResponse {
  access_token?: string;
  token_type?: string;
  expires_in?: number;
  scope?: string;
  id_token?: string;
}

interface GoogleUserInfo {
  sub?: string;
  email?: string;
  email_verified?: boolean;
  name?: string;
  picture?: string;
}

@Injectable()
export class GoogleOAuthStrategy implements OAuthProviderStrategy {
  readonly name = 'google';

  constructor(private readonly config: ConfigService) {}

  // ============================================================
  // Authorization URL
  // ============================================================

  getAuthorizationUrl(state: string): string {
    const url = new URL('https://accounts.google.com/o/oauth2/v2/auth');

    url.searchParams.set('client_id', this.clientId);

    url.searchParams.set('redirect_uri', this.callbackUrl);

    url.searchParams.set('response_type', 'code');

    url.searchParams.set('scope', 'openid email profile');

    url.searchParams.set('state', state);

    url.searchParams.set('prompt', 'select_account');

    return url.toString();
  }

  // ============================================================
  // Exchange Google authorization code
  // ============================================================

  async exchangeCode(code: string): Promise<OAuthIdentity> {
    /*
     * Міняємо authorization code
     * на Google access token.
     */
    const body = new URLSearchParams({
      client_id: this.clientId,

      client_secret: this.clientSecret,

      code,

      grant_type: 'authorization_code',

      redirect_uri: this.callbackUrl,
    });

    const tokenResponse = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',

      headers: {
        'content-type': 'application/x-www-form-urlencoded',
      },

      body,
    });

    if (!tokenResponse.ok) {
      throw new BadGatewayException('OAuth token exchange failed');
    }

    /*
     * Не розриваємо "as Type"
     * на окрему конструкцію.
     */
    const tokens = (await tokenResponse.json()) as GoogleTokenResponse;

    if (!tokens.access_token) {
      throw new BadGatewayException(
        'OAuth provider did not return access token',
      );
    }

    // ==========================================================
    // Google user info
    // ==========================================================

    const userResponse = await fetch(
      'https://openidconnect.googleapis.com/v1/userinfo',
      {
        headers: {
          authorization: `Bearer ${tokens.access_token}`,
        },
      },
    );

    if (!userResponse.ok) {
      throw new BadGatewayException('OAuth user lookup failed');
    }

    const profile = (await userResponse.json()) as GoogleUserInfo;

    if (!profile.sub || !profile.email) {
      throw new BadGatewayException('Invalid OAuth user profile');
    }

    return {
      provider: this.name,

      subject: profile.sub,

      email: profile.email.trim().toLowerCase(),

      emailVerified: profile.email_verified === true,

      displayName: profile.name,

      avatarUrl: profile.picture,
    };
  }

  // ============================================================
  // Config
  // ============================================================

  private get clientId(): string {
    return this.config.getOrThrow<string>('oauth.google.clientId');
  }

  private get clientSecret(): string {
    return this.config.getOrThrow<string>('oauth.google.clientSecret');
  }

  private get callbackUrl(): string {
    return this.config.getOrThrow<string>('oauth.google.callbackUrl');
  }
}
