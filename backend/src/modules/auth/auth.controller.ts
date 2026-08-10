import {
  Body,
  Controller,
  HttpCode,
  HttpStatus,
  Post,
} from '@nestjs/common';

import {
  ApiNoContentResponse,
  ApiOkResponse,
  ApiTags,
} from '@nestjs/swagger';

import { AuthService } from './auth.service';

import { Auth } from './decorators/auth.decorator';
import { CurrentUser } from './decorators/current-user.decorator';
import { Public } from './decorators/public.decorator';

import { LoginDto } from './dto/login.dto';
import { RefreshTokenDto } from './dto/refresh-token.dto';
import { TokenPairDto } from './dto/token-pair.dto';

@ApiTags('auth')
@Controller('auth')
export class AuthController {
  constructor(
    private readonly authService: AuthService,
  ) {}

  @Public()
  @Post('login')
  @HttpCode(HttpStatus.OK)
  @ApiOkResponse({
    type: TokenPairDto,
  })
  login(
    @Body() dto: LoginDto,
  ): Promise<TokenPairDto> {
    return this.authService.login(
      dto.login,
      dto.password,
    );
  }

  @Public()
  @Post('refresh')
  @HttpCode(HttpStatus.OK)
  @ApiOkResponse({
    type: TokenPairDto,
  })
  refresh(
    @Body() dto: RefreshTokenDto,
  ): Promise<TokenPairDto> {
    return this.authService.refresh(
      dto.refreshToken,
    );
  }

  @Post('logout')
  @Auth()
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiNoContentResponse({
    description: 'Current session revoked',
  })
  async logout(
    @CurrentUser('sub')
    userId: string,

    @CurrentUser('sessionId')
    sessionId: string,
  ): Promise<void> {
    await this.authService.logout(
      sessionId,
      userId,
    );
  }
}