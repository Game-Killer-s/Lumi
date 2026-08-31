import { BadRequestException, Injectable } from '@nestjs/common';

import { GoogleOAuthStrategy } from './google-oauth.strategy';

import { OAuthProviderStrategy } from './oauth-provider.strategy';

@Injectable()
export class OAuthProviderRegistry {
  private readonly providers: Map<string, OAuthProviderStrategy>;

  constructor(google: GoogleOAuthStrategy) {
    this.providers = new Map([[google.name, google]]);
  }

  get(name: string): OAuthProviderStrategy {
    const provider = this.providers.get(name.toLowerCase());

    if (!provider) {
      throw new BadRequestException('Unsupported OAuth provider');
    }

    return provider;
  }
}
