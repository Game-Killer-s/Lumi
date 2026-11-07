import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Patch,
  Post,
  Put,
  UploadedFile,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import {
  ApiBody,
  ApiConsumes,
  ApiNoContentResponse,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';

import { Auth } from '../auth/decorators/auth.decorator';
import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { UploadedMediaFile } from '../media/media.constants';
import { AVATAR_UPLOAD_OPTIONS } from './avatar.constants';
import { CurrentListeningService } from './current-listening.service';
import { ChangePasswordDto } from './dto/change-password.dto';
import {
  CurrentListeningStatusDto,
  UpdateCurrentListeningDto,
} from './dto/current-listening.dto';
import { ProfileDto, UpdateProfileDto } from './dto/profile.dto';
import { UpdatePrivacyDto } from './dto/privacy.dto';
import { PresenceRateLimitGuard } from './guards/presence-rate-limit.guard';
import { ProfileOwnerGuard } from './guards/profile-owner.guard';
import { ProfileService } from './profile.service';

@ApiTags('profile')
@Controller('profile')
export class ProfileController {
  constructor(
    private readonly profileService: ProfileService,
    private readonly currentListeningService: CurrentListeningService,
  ) {}

  @Auth()
  @Get()
  @ApiOperation({ summary: 'Get the current user profile' })
  @ApiOkResponse({ type: ProfileDto })
  getProfile(@CurrentUser('sub') userId: string): Promise<ProfileDto> {
    return this.profileService.getProfile(userId);
  }

  @Auth()
  @Patch()
  @UseGuards(ProfileOwnerGuard)
  @ApiOperation({ summary: 'Update the current user profile' })
  @ApiOkResponse({ type: ProfileDto })
  updateProfile(
    @CurrentUser('sub') userId: string,
    @Body() dto: UpdateProfileDto,
  ): Promise<ProfileDto> {
    return this.profileService.updateProfile(userId, dto);
  }

  @Auth()
  @Patch('privacy')
  @UseGuards(ProfileOwnerGuard)
  @ApiOperation({ summary: 'Update profile privacy settings' })
  @ApiOkResponse({ type: ProfileDto })
  updatePrivacy(
    @CurrentUser('sub') userId: string,
    @Body() dto: UpdatePrivacyDto,
  ): Promise<ProfileDto> {
    return this.profileService.updatePrivacy(userId, dto);
  }

  @Auth()
  @Put('current-listening')
  @UseGuards(PresenceRateLimitGuard)
  @ApiOperation({ summary: 'Publish or refresh the current listening status' })
  @ApiOkResponse({ type: CurrentListeningStatusDto })
  updateCurrentListening(
    @CurrentUser('sub') userId: string,
    @Body() dto: UpdateCurrentListeningDto,
  ): Promise<CurrentListeningStatusDto> {
    return this.currentListeningService.update(userId, dto.trackId);
  }

  @Auth()
  @Delete('current-listening')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: 'Clear the current listening status' })
  @ApiNoContentResponse()
  clearCurrentListening(@CurrentUser('sub') userId: string): Promise<void> {
    return this.currentListeningService.clear(userId);
  }

  @Auth()
  @Patch('password')
  @HttpCode(HttpStatus.NO_CONTENT)
  @UseGuards(ProfileOwnerGuard)
  @ApiOperation({ summary: 'Change password and revoke other sessions' })
  @ApiNoContentResponse()
  changePassword(
    @CurrentUser('sub') userId: string,
    @CurrentUser('sessionId') sessionId: string,
    @Body() dto: ChangePasswordDto,
  ): Promise<void> {
    return this.profileService.changePassword(userId, sessionId, dto);
  }

  @Auth()
  @Post('avatar')
  @HttpCode(HttpStatus.OK)
  @UseGuards(ProfileOwnerGuard)
  @ApiConsumes('multipart/form-data')
  @ApiOperation({ summary: 'Upload or replace the profile avatar' })
  @ApiBody({
    schema: {
      type: 'object',
      properties: {
        avatar: { type: 'string', format: 'binary' },
      },
      required: ['avatar'],
    },
  })
  @ApiOkResponse({ type: ProfileDto })
  @UseInterceptors(FileInterceptor('avatar', AVATAR_UPLOAD_OPTIONS))
  updateAvatar(
    @CurrentUser('sub') userId: string,
    @UploadedFile() file: UploadedMediaFile | undefined,
  ): Promise<ProfileDto> {
    return this.profileService.updateAvatar(userId, file);
  }
}
