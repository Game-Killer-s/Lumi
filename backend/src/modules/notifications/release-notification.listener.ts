import { Injectable, Logger, OnModuleInit } from '@nestjs/common';

import { ReleaseEventBus } from './release.event-bus';
import { ReleaseNotificationService } from './release-notification.service';

// Handler події нового published release.
@Injectable()
export class ReleaseNotificationListener implements OnModuleInit {
  private readonly logger = new Logger(ReleaseNotificationListener.name);

  constructor(
    private readonly eventBus: ReleaseEventBus,
    private readonly notifications: ReleaseNotificationService,
  ) {}

  onModuleInit(): void {
    this.eventBus.onReleasePublished((payload) => {
      this.notifications
        .generateForRelease(payload.releaseId)
        .catch((error: Error) => {
          this.logger.error(
            `release ${payload.releaseId}: генерація впала — ${error.message}`,
          );
        });
    });
  }
}
