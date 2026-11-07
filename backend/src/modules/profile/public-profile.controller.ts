import { Controller, Get, Param, Query } from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiNotFoundResponse,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';

import { OptionalAuth } from '../auth/decorators/optional-auth.decorator';
import { OptionalCurrentUser } from '../auth/decorators/optional-current-user.decorator';
import { AccessTokenPayload } from '../auth/types/token-payload.type';
import { CurrentListeningService } from './current-listening.service';
import { CurrentListeningDto } from './dto/current-listening.dto';
import { ProfileDto } from './dto/profile.dto';
import {
  PublicProfileDto,
  PublicProfileListDto,
} from './dto/public-profile.dto';
import { SearchProfilesQueryDto } from './dto/search-profiles-query.dto';
import { ProfileService } from './profile.service';

@ApiTags('profiles')
@ApiBearerAuth('access-token')
@OptionalAuth()
@Controller('profiles')
export class PublicProfileController {
  constructor(
    private readonly profileService: ProfileService,
    private readonly currentListeningService: CurrentListeningService,
  ) {}

  // Оголошуємо до :id, інакше "search" зматчиться як id.
  @Get('search')
  @ApiOperation({ summary: 'Search public profiles by nickname' })
  @ApiOkResponse({ type: PublicProfileListDto })
  search(
    @Query() query: SearchProfilesQueryDto,
  ): Promise<PublicProfileListDto> {
    return this.profileService.searchProfiles(query);
  }

  @Get(':id/current-listening')
  @ApiOperation({
    summary: 'Get the current listening status of a profile',
    description:
      'Повертає presence лише якщо профіль публічний і showCurrentListening=true. ' +
      'Власник і адміністратор бачать власний/приватний статус.',
  })
  @ApiOkResponse({ type: CurrentListeningDto })
  @ApiNotFoundResponse({
    description: 'Presence is hidden, expired or missing',
  })
  getCurrentListening(
    @Param('id') id: string,
    @OptionalCurrentUser() viewer: AccessTokenPayload | null,
  ): Promise<CurrentListeningDto> {
    return this.currentListeningService.getForProfile(id, viewer ?? undefined);
  }

  @Get(':id')
  @ApiOperation({
    summary: 'Get a public profile',
    description:
      'Сторонні бачать лише публічну проєкцію. Власник і адміністратор ' +
      'з валідним токеном отримують приватні поля; адмін-доступ логується.',
  })
  @ApiOkResponse({
    description: 'Public profile, or private profile for owner/admin',
  })
  @ApiNotFoundResponse({
    description: 'Profile is missing, blocked or private',
  })
  getProfile(
    @Param('id') id: string,
    @OptionalCurrentUser() viewer: AccessTokenPayload | null,
  ): Promise<ProfileDto | PublicProfileDto> {
    return this.profileService.getProfileForViewer(id, viewer ?? undefined);
  }
}
