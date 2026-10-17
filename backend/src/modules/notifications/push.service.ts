import { Injectable, Logger } from '@nestjs/common';

// Push-сервіс. Поки що просто пише в лог сервера,
// а коли з'явиться ключ FCM — тут буде реальна відправка.
@Injectable()
export class PushService {
  private readonly logger = new Logger(PushService.name);

  async send(userId: string, title: string, body: string): Promise<void> {
    this.logger.log(`push -> ${userId}: ${title} | ${body}`);
  }
}
