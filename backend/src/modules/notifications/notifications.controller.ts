import {
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  Patch,
  Post,
  Put,
  Query,
} from '@nestjs/common';
import { ApiOkResponse, ApiTags } from '@nestjs/swagger';

import { Auth } from '../auth/decorators/auth.decorator';
import { CurrentUser } from '../auth/decorators/current-user.decorator';

import { ListNotificationsQueryDto } from './dto/list-notifications-query.dto';
import { SendPushDto } from './dto/send-push.dto';
import { UpdateNotificationPreferencesDto } from './dto/update-notification-preferences.dto';
import { UpdateNotificationSettingsDto } from './dto/update-notification-settings.dto';
import { NotificationsService } from './notifications.service';

@ApiTags('notifications')
@Controller('notifications')
export class NotificationsController {
  constructor(private readonly notificationsService: NotificationsService) {}

  // GET /api/notifications — шторка сповіщень з пагінацією
  @Auth()
  @Get()
  list(
    @CurrentUser('sub') userId: string,
    @Query() query: ListNotificationsQueryDto,
  ) {
    return this.notificationsService.listNotifications(userId, query);
  }

  // GET /api/notifications/unread-count — скільки непрочитаних
  @Auth()
  @Get('unread-count')
  unreadCount(@CurrentUser('sub') userId: string) {
    return this.notificationsService.getUnreadCount(userId);
  }

  // GET /api/notifications/preferences — які типи сповіщень увімкнені
  @Auth()
  @Get('preferences')
  getPreferences(@CurrentUser('sub') userId: string) {
    return this.notificationsService.getPreferences(userId);
  }

  // PATCH /api/notifications/preferences — змінити типи сповіщень
  @Auth()
  @Patch('preferences')
  updatePreferences(
    @CurrentUser('sub') userId: string,
    @Body() dto: UpdateNotificationPreferencesDto,
  ) {
    return this.notificationsService.updatePreferences(userId, dto);
  }

  // GET /api/notifications/settings — свитчі в налаштуваннях
  @Auth()
  @Get('settings')
  getSettings(@CurrentUser('sub') userId: string) {
    return this.notificationsService.getSettings(userId);
  }

  // PUT /api/notifications/settings — зберегти свитчі
  @Auth()
  @Put('settings')
  updateSettings(
    @CurrentUser('sub') userId: string,
    @Body() dto: UpdateNotificationSettingsDto,
  ) {
    return this.notificationsService.updateSettings(userId, dto);
  }

  // PATCH /api/notifications/read-all — прочитати всі
  @Auth()
  @Patch('read-all')
  markAllAsRead(@CurrentUser('sub') userId: string) {
    return this.notificationsService.markAllAsRead(userId);
  }

  // PATCH /api/notifications/:id/read — прочитати одне
  @Auth()
  @Patch(':id/read')
  markAsRead(@CurrentUser('sub') userId: string, @Param('id') id: string) {
    return this.notificationsService.markAsRead(userId, id);
  }

  // POST /api/notifications/push — відправити push про новий реліз
  @Auth()
  @Post('push')
  @HttpCode(HttpStatus.OK)
  @ApiOkResponse({
    description: 'Push відправлено або вимкнено налаштуваннями',
  })
  sendPush(@CurrentUser('sub') userId: string, @Body() dto: SendPushDto) {
    return this.notificationsService.send({
      userId,
      type: dto.type,
      title: dto.title,
      body: dto.body,
    });
  }
}
