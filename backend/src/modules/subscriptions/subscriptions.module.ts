import { Module } from '@nestjs/common';

import { NotificationsModule } from '../notifications/notifications.module';

import { PaymentGatewayService } from './payment-gateway.service';
import { SubscriptionsController } from './subscriptions.controller';
import { SubscriptionsService } from './subscriptions.service';

@Module({
  imports: [NotificationsModule],
  controllers: [SubscriptionsController],
  providers: [SubscriptionsService, PaymentGatewayService],
  exports: [SubscriptionsService],
})
export class SubscriptionsModule {}
