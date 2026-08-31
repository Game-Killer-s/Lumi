import { Body, Controller, Get, Param, Post, Query, Res } from '@nestjs/common';

import { Throttle } from '@nestjs/throttler';

import type { Response } from 'express';

import { OAuthService } from './services/oauth.service';

import { OAuthStartDto } from './dto/oauth-start.dto';

import { OAuthExchangeDto } from './dto/oauth-exchange.dto';

import { Public } from './decorators/public.decorator';

import { Auth } from './decorators/auth.decorator';

import { CurrentUser } from './decorators/current-user.decorator';

@Controller('auth/oauth')
export class OAuthController {
  constructor(private readonly oauth: OAuthService) {}

  @Public()
  @Get(':provider/start')
  @Throttle({
    default: {
      limit: 10,
      ttl: 60_000,
    },
  })
  start(
    @Param('provider')
    provider: string,

    @Query()
    dto: OAuthStartDto,
  ) {
    return this.oauth.startLogin(provider, dto.returnTo);
  }

  /*
   * Explicit account linking.
   * Тільки authenticated user.
   */
  @Get(':provider/link/start')
  @Auth()
  @Throttle({
    default: {
      limit: 5,
      ttl: 60_000,
    },
  })
  link(
    @Param('provider')
    provider: string,

    @Query()
    dto: OAuthStartDto,

    @CurrentUser('sub')
    userId: string,
  ) {
    return this.oauth.startLink(provider, dto.returnTo, userId);
  }

  @Public()
  @Get(':provider/callback')
  @Throttle({
    default: {
      limit: 30,
      ttl: 60_000,
    },
  })
  async callback(
    @Param('provider')
    provider: string,

    @Query('state')
    state: string,

    @Query('code')
    code: string,

    @Res()
    response: Response,
  ): Promise<void> {
    const result = await this.oauth.callback(provider, state, code);

    response.redirect(result.redirectUrl);
  }

  @Public()
  @Post('exchange')
  @Throttle({
    default: {
      limit: 10,
      ttl: 60_000,
    },
  })
  exchange(
    @Body()
    dto: OAuthExchangeDto,
  ) {
    return this.oauth.exchangeHandoff(dto.code);
  }
}
