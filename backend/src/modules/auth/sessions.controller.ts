import {
  Controller,
  Delete,
  HttpCode,
  HttpStatus,
  Param,
  Post,
} from '@nestjs/common';

import { ApiNoContentResponse, ApiOperation, ApiTags } from '@nestjs/swagger';

import { AuthService } from './auth.service';

import { Auth } from './decorators/auth.decorator';

import { CurrentUser } from './decorators/current-user.decorator';

@ApiTags('auth sessions')
@Controller('auth/sessions')
export class SessionsController {
  constructor(private readonly authService: AuthService) {}

  /*
   * DELETE /api/auth/sessions/:sessionId
   *
   * Завершує конкретну session
   * поточного користувача.
   */
  @Delete(':sessionId')
  @Auth()
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({
    summary: 'Revoke a specific user session',
  })
  @ApiNoContentResponse({
    description: 'Session revoked',
  })
  async revokeSession(
    @CurrentUser('sub')
    userId: string,

    @Param('sessionId')
    sessionId: string,
  ): Promise<void> {
    await this.authService.revokeUserSession(userId, sessionId);
  }

  /*
   * POST /api/auth/sessions/revoke-all
   *
   * Завершує всі sessions
   * поточного користувача.
   *
   * Поточна session теж буде
   * відкликана.
   */
  @Post('revoke-all')
  @Auth()
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({
    summary: 'Revoke all user sessions',
  })
  @ApiNoContentResponse({
    description: 'All sessions revoked',
  })
  async revokeAllSessions(
    @CurrentUser('sub')
    userId: string,
  ): Promise<void> {
    await this.authService.revokeAllSessions(userId);
  }
}
