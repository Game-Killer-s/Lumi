import {
  Body,
  Controller,
  HttpCode,
  HttpStatus,
  Post,
} from '@nestjs/common';

import {
  ApiConflictResponse,
  ApiCreatedResponse,
  ApiNoContentResponse,
  ApiOkResponse,
  ApiTags,
} from '@nestjs/swagger';

import { AuthService } from './auth.service';

import { Auth } from './decorators/auth.decorator';
import { CurrentUser } from './decorators/current-user.decorator';
import { Public } from './decorators/public.decorator';

import { AuthResponseDto } from './dto/auth-response.dto';
import { ForgotPasswordDto } from './dto/forgot-password.dto';
import { GoogleLoginDto } from './dto/google-login.dto';
import { LoginDto } from './dto/login.dto';
import { RefreshTokenDto } from './dto/refresh-token.dto';
import { RegisterDto } from './dto/register.dto';
import { TokenPairDto } from './dto/token-pair.dto';

@ApiTags('auth')
@Controller('auth')
export class AuthController {
  constructor(
    private readonly authService: AuthService,
  ) {}

  @Public()
  @Post('register')
  @ApiCreatedResponse({
    type: AuthResponseDto,
    description: 'Account created, session issued',
  })
  @ApiConflictResponse({
    description: 'Email already exists',
  })
  register(
    @Body() dto: RegisterDto,
  ): Promise<AuthResponseDto> {
    return this.authService.register(
      dto.nickname,
      dto.email,
      dto.password,
    );
  }

  @Public()
  @Post('login')
  @HttpCode(HttpStatus.OK)
  @ApiOkResponse({
    type: AuthResponseDto,
  })
  login(
    @Body() dto: LoginDto,
  ): Promise<AuthResponseDto> {
    return this.authService.login(
      dto.email,
      dto.password,
    );
  }

  @Public()
  @Post('google')
  @HttpCode(HttpStatus.OK)
  @ApiOkResponse({
    type: AuthResponseDto,
    description: 'Google account verified, session issued',
  })
  google(
    @Body() dto: GoogleLoginDto,
  ): Promise<AuthResponseDto> {
    return this.authService.googleLogin(
      dto.idToken,
    );
  }

  @Public()
  @Post('forgot-password')
  @HttpCode(HttpStatus.OK)
  @ApiOkResponse({
    schema: {
      example: {
        message: 'Якщо акаунт існує — токен надіслано на пошту',
      },
    },
    description: 'Password reset token is sent to email (if account exists)',
  })
  async forgotPassword(
    @Body() dto: ForgotPasswordDto,
  ): Promise<{ message: string }> {
    await this.authService.forgotPassword(
      dto.email,
    );

    return {
      message:
        'Якщо акаунт існує — токен надіслано на пошту',
    };
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