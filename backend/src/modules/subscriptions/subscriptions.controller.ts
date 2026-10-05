import {
  Body,
  Controller,
  Delete,
  Get,
  Headers,
  HttpCode,
  HttpStatus,
  Param,
  Post,
  Put,
} from '@nestjs/common';
import { ApiOkResponse, ApiTags } from '@nestjs/swagger';

import { Auth } from '../auth/decorators/auth.decorator';
import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { Public } from '../auth/decorators/public.decorator';

import { CheckoutDto } from './dto/checkout.dto';
import {
  AddPaymentMethodDto,
  UpdatePaymentMethodDto,
} from './dto/payment-method.dto';
import { WebhookDto } from './dto/webhook.dto';
import { SubscriptionsService } from './subscriptions.service';

@ApiTags('subscriptions')
@Controller('subscriptions')
export class SubscriptionsController {
  constructor(
    private readonly subscriptionsService: SubscriptionsService,
  ) {}

  // GET /api/subscriptions/plans — тарифи для екрана вибору
  @Public()
  @Get('plans')
  getPlans() {
    return this.subscriptionsService.listPlans();
  }

  // GET /api/subscriptions/status — стан підписки (Story 3)
  @Auth()
  @Get('status')
  getStatus(@CurrentUser('sub') userId: string) {
    return this.subscriptionsService.getStatus(userId);
  }

  // GET /api/subscriptions/play-access — чи можна вмикати трек (Story 3)
  @Auth()
  @Get('play-access')
  checkPlayAccess(@CurrentUser('sub') userId: string) {
    return this.subscriptionsService.checkPlayAccess(userId);
  }

  // GET /api/subscriptions/payments — історія оплат
  @Auth()
  @Get('payments')
  listPayments(@CurrentUser('sub') userId: string) {
    return this.subscriptionsService.listPayments(userId);
  }

  // POST /api/subscriptions/checkout — оплата тарифу
  @Auth()
  @Post('checkout')
  @HttpCode(HttpStatus.OK)
  checkout(
    @CurrentUser('sub') userId: string,
    @Body() dto: CheckoutDto,
  ) {
    return this.subscriptionsService.checkout(userId, dto);
  }

  // POST /api/subscriptions/cancel — скасувати підписку
  @Auth()
  @Post('cancel')
  @HttpCode(HttpStatus.OK)
  cancel(@CurrentUser('sub') userId: string) {
    return this.subscriptionsService.cancel(userId);
  }

  // ---------- Платіжні дані ----------

  @Auth()
  @Get('payment-methods')
  listPaymentMethods(@CurrentUser('sub') userId: string) {
    return this.subscriptionsService.listPaymentMethods(userId);
  }

  @Auth()
  @Post('payment-methods')
  addPaymentMethod(
    @CurrentUser('sub') userId: string,
    @Body() dto: AddPaymentMethodDto,
  ) {
    return this.subscriptionsService.addPaymentMethod(userId, dto);
  }

  @Auth()
  @Put('payment-methods/:id')
  updatePaymentMethod(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
    @Body() dto: UpdatePaymentMethodDto,
  ) {
    return this.subscriptionsService.updatePaymentMethod(userId, id, dto);
  }

  @Auth()
  @Delete('payment-methods/:id')
  @HttpCode(HttpStatus.OK)
  removePaymentMethod(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
  ) {
    return this.subscriptionsService.removePaymentMethod(userId, id);
  }

  // POST /api/subscriptions/webhook — повідомлення від платіжного шлюзу
  @Public()
  @Post('webhook')
  @HttpCode(HttpStatus.OK)
  @ApiOkResponse({ description: 'Статус платежу та підписки оновлено' })
  handleWebhook(
    @Body() dto: WebhookDto,
    @Headers('x-lumi-signature') signature?: string,
  ) {
    return this.subscriptionsService.handleWebhook(dto, signature);
  }
}
