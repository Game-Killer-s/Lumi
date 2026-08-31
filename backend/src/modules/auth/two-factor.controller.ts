import { Body, Controller, HttpCode, HttpStatus, Post } from '@nestjs/common';

import { Throttle } from '@nestjs/throttler';

import { Auth } from './decorators/auth.decorator';
import { Public } from './decorators/public.decorator';
import { CurrentUser } from './decorators/current-user.decorator';

import { TwoFactorService } from './services/two-factor.service';

import { AuthService } from './auth.service';

import { TwoFactorCodeDto } from './dto/two-factor-code.dto';

import { TwoFactorChallengeDto } from './dto/two-factor-challenge.dto';

import { DisableTwoFactorDto } from './dto/disable-two-factor.dto';

@Controller('auth/2fa')
export class TwoFactorController {
  constructor(
    private readonly twoFactor: TwoFactorService,

    private readonly authService: AuthService,
  ) {}

  @Post('enroll')
  @Auth()
  @Throttle({
    default: {
      limit: 5,
      ttl: 60_000,
    },
  })
  enroll(
    @CurrentUser('sub')
    userId: string,
  ) {
    return this.twoFactor.enroll(userId);
  }

  @Post('enroll/verify')
  @Auth()
  @Throttle({
    default: {
      limit: 5,
      ttl: 60_000,
    },
  })
  verifyEnrollment(
    @CurrentUser('sub')
    userId: string,

    @Body()
    dto: TwoFactorCodeDto,
  ) {
    return this.twoFactor.verifyEnrollment(userId, dto.code);
  }

  /*
   * Public, бо access JWT ще
   * не видавався.
   */
  @Public()
  @Post('challenge/verify')
  @HttpCode(HttpStatus.OK)
  @Throttle({
    default: {
      limit: 10,
      ttl: 60_000,
    },
  })
  verifyChallenge(
    @Body()
    dto: TwoFactorChallengeDto,
  ) {
    return this.authService.completeTwoFactorLogin(dto.challengeId, dto.code);
  }

  @Post('disable')
  @Auth()
  @HttpCode(HttpStatus.NO_CONTENT)
  @Throttle({
    default: {
      limit: 5,
      ttl: 60_000,
    },
  })
  async disable(
    @CurrentUser('sub')
    userId: string,

    @Body()
    dto: DisableTwoFactorDto,
  ): Promise<void> {
    await this.twoFactor.disable(userId, dto.password, dto.code);
  }
}
