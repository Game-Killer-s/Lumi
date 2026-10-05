import { Module } from '@nestjs/common';

import { NotificationsController } from './notifications.controller';
import { NotificationsService } from './notifications.service';
import { PushService } from './push.service';
import { ReleaseEventBus } from './release.event-bus';
import { ReleaseNotificationListener } from './release-notification.listener';
import { ReleaseNotificationService } from './release-notification.service';

@Module({
  controllers: [NotificationsController],
  providers: [
    NotificationsService,
    PushService,
    ReleaseNotificationService,
    ReleaseEventBus,
    ReleaseNotificationListener,
  ],
  // Шину віддаємо назовні: контент-модуль кидає подію про новий реліз.
  exports: [NotificationsService, ReleaseEventBus],
})
export class NotificationsModule {}
