import {
  BadRequestException,
  Injectable,
  NotFoundException,
  UnauthorizedException,
} from '@nestjs/common';
import { Prisma, SubscriptionStatus } from '@prisma/client';

import { PrismaService } from '../../prisma/prisma.service';
import { NotificationsService } from '../notifications/notifications.service';

import { CardUtil } from './card.util';
import { PaymentGatewayService } from './payment-gateway.service';

import { CheckoutDto } from './dto/checkout.dto';
import {
  AddPaymentMethodDto,
  UpdatePaymentMethodDto,
} from './dto/payment-method.dto';
import { WebhookDto } from './dto/webhook.dto';

const DAY_MS = 24 * 60 * 60 * 1000;

// Скільки днів триває тестовий доступ у Grace Period (за ТЗ — 3 дні).
const GRACE_PERIOD_DAYS = 3;

export const STATUS_TITLES: Record<SubscriptionStatus, string> = {
  PENDING: 'Очікує оплати',
  ACTIVE: 'Активна',
  GRACE_PERIOD: 'Оплата прострочена (Grace Period)',
  SUSPENDED: 'Доступ призупинено',
  CANCELLED: 'Підписку скасовано',
};

export const STATUS_MESSAGES: Record<SubscriptionStatus, string> = {
  PENDING: 'Підписка створена, але оплата ще не підтверджена',
  ACTIVE: 'Активна підписка — повний доступ до HQ-аудіопотоку',
  GRACE_PERIOD:
    'Оплата не пройшла — у вас 3 дні тестового доступу, система щодня пробує списати кошти знову',
  SUSPENDED: 'Доступ до HQ-сервера обмежено: оплата не пройшла за 3 дні',
  CANCELLED: 'Підписку скасовано, доступ закрито',
};

export interface Entitlement {
  tier: string;
  maxQuality: string;
  canUseHq: boolean;
  canUseLossless: boolean;
  canDownload: boolean;
}

// Безкоштовний тариф: стандартна якість, без HQ/Lossless і завантажень (ТЗ 4.2).
export const FREE_ENTITLEMENT: Entitlement = {
  tier: 'FREE',
  maxQuality: 'STANDARD',
  canUseHq: false,
  canUseLossless: false,
  canDownload: false,
};

// Преміум: HQ, Lossless та офлайн-завантаження треків.
export const PREMIUM_ENTITLEMENT: Entitlement = {
  tier: 'PREMIUM',
  maxQuality: 'LOSSLESS',
  canUseHq: true,
  canUseLossless: true,
  canDownload: true,
};

export interface SubscriptionStatusResponse {
  hasSubscription: boolean;
  status: string;
  title: string;
  message: string;
  plan: {
    id: string;
    title: string;
    priceCents: number;
    currency: string;
  } | null;
  currentPeriodEnd: Date | null;
  gracePeriodEndsAt: Date | null;
  cancelAtPeriodEnd: boolean;
  // Дата і сума наступного автоматичного списання (з ТЗ).
  nextChargeDate: Date | null;
  nextChargeAmountCents: number | null;
  // Скільки платежів уже пройшло.
  paymentsCount: number;
  // Що дозволено тарифом: якість і завантаження.
  entitlement: Entitlement;
}

export interface PlayAccessResponse {
  allowed: boolean;
  quality: string;
  status: string;
  message: string;
}

type SubscriptionWithPlan = Prisma.UserSubscriptionGetPayload<{
  include: { plan: true };
}>;

@Injectable()
export class SubscriptionsService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly gateway: PaymentGatewayService,
    private readonly notifications: NotificationsService,
  ) {}

  // ---------- Тарифи ----------

  listPlans() {
    return this.prisma.client.subscriptionPlan.findMany({
      where: { isActive: true },
      orderBy: { priceCents: 'asc' },
    });
  }

  // ---------- Story 3: статус і обмеження ----------

  async getStatus(userId: string): Promise<SubscriptionStatusResponse> {
    const subscription = await this.findCurrentSubscription(userId);

    if (!subscription) {
      return this.toStatusResponse(null, 0);
    }

    // Поки триває Grace Period — раз на добу пробуємо списати кошти ще раз.
    await this.retryPaymentIfDue(subscription);

    const fresh = await this.findCurrentSubscription(userId);
    const resolved = await this.resolveStatus(fresh);

    const paymentsCount = await this.prisma.client.payment.count({
      where: { userId, status: 'SUCCEEDED' },
    });

    return this.toStatusResponse(resolved, paymentsCount);
  }

  // Перед відтворенням треку питаємо, чи можна його вмикати.
  async checkPlayAccess(userId: string): Promise<PlayAccessResponse> {
    const status = await this.getStatus(userId);

    // Без підписки працює безкоштовний доступ у стандартній якості.
    if (!status.hasSubscription) {
      return {
        allowed: true,
        quality: status.entitlement.maxQuality,
        status: status.status,
        message: 'Безкоштовний доступ у стандартній якості',
      };
    }

    // HQ доступна лише за тарифом; Grace Period теж дозволяє (ТЗ 4.2).
    const allowed = status.entitlement.canUseHq;

    return {
      allowed,
      quality: allowed ? 'HQ' : 'NONE',
      status: status.status,
      message: status.message,
    };
  }

  // Історія оплат — використовується в кабінеті користувача.
  listPayments(userId: string) {
    return this.prisma.client.payment.findMany({
      where: { userId },
      orderBy: { createdAt: 'desc' },
      take: 20,
    });
  }

  // ---------- Story 2: оплата та скасування ----------

  async checkout(userId: string, dto: CheckoutDto) {
    const plan = await this.prisma.client.subscriptionPlan.findUnique({
      where: { id: dto.planId },
    });

    if (!plan || !plan.isActive) {
      throw new NotFoundException('Тариф не знайдено');
    }

    await this.assertPaymentData(dto);

    const subscription = await this.getOrCreateSubscription(
      userId,
      plan.id,
      dto.provider,
    );

    const charge = await this.gateway.chargeCard({
      amountCents: plan.priceCents,
      currency: plan.currency,
      cardNumber: dto.cardNumber,
      paymentToken: dto.paymentToken,
    });

    const payment = await this.prisma.client.payment.create({
      data: {
        userId,
        subscriptionId: subscription.id,
        amountCents: plan.priceCents,
        currency: plan.currency,
        status: charge.status,
        provider: dto.provider,
        externalId: charge.externalId,
        failureReason: charge.failureReason,
      },
    });

    // Оплата не пройшла — підписка їде в Grace Period або Suspended.
    if (charge.status === 'FAILED') {
      const failed = await this.applyPaymentFailure(
        subscription,
        charge.failureReason ?? 'Оплата не пройшла',
      );

      throw new BadRequestException(STATUS_MESSAGES[failed.status]);
    }

    await this.rememberCard(userId, dto);

    const activated = await this.activateSubscription(subscription);

    return { subscription: activated, payment };
  }

  async cancel(userId: string) {
    const subscription = await this.findCurrentSubscription(userId);

    if (!subscription) {
      throw new NotFoundException('Підписки не знайдено');
    }

    // Якщо період ще йде — залишаємо доступ до кінця періоду.
    const periodIsRunning =
      subscription.status === SubscriptionStatus.ACTIVE &&
      subscription.currentPeriodEnd !== null &&
      subscription.currentPeriodEnd > new Date();

    const updated = await this.prisma.client.userSubscription.update({
      where: { id: subscription.id },
      data: {
        cancelAtPeriodEnd: true,
        status: periodIsRunning
          ? subscription.status
          : SubscriptionStatus.CANCELLED,
      },
    });

    await this.notifications.send({
      userId,
      type: 'subscription',
      title: 'Підписку скасовано',
      body: periodIsRunning
        ? 'Доступ працюватиме до кінця оплаченого періоду'
        : 'Доступ до музики закрито',
    });

    return { subscription: updated, cancelAtPeriodEnd: true };
  }

  // ---------- Платіжні дані ----------

  listPaymentMethods(userId: string) {
    return this.prisma.client.paymentMethod.findMany({
      where: { userId },
      orderBy: { createdAt: 'desc' },
    });
  }

  async addPaymentMethod(userId: string, dto: AddPaymentMethodDto) {
    if (!CardUtil.isValidNumber(dto.cardNumber)) {
      throw new BadRequestException('Перевірте номер картки');
    }

    if (CardUtil.isExpired(dto.expMonth, dto.expYear)) {
      throw new BadRequestException('Термін дії картки минув');
    }

    const isDefault = dto.isDefault !== false;

    if (isDefault) {
      await this.prisma.client.paymentMethod.updateMany({
        where: { userId },
        data: { isDefault: false },
      });
    }

    return this.prisma.client.paymentMethod.create({
      data: {
        userId,
        brand: CardUtil.detectBrand(dto.cardNumber),
        last4: CardUtil.last4(dto.cardNumber),
        expMonth: dto.expMonth,
        expYear: dto.expYear,
        holderName: dto.holderName,
        isDefault,
      },
    });
  }

  async updatePaymentMethod(
    userId: string,
    id: string,
    dto: UpdatePaymentMethodDto,
  ) {
    const method = await this.requirePaymentMethod(userId, id);

    const expMonth = dto.expMonth ?? method.expMonth;
    const expYear = dto.expYear ?? method.expYear;

    if (CardUtil.isExpired(expMonth, expYear)) {
      throw new BadRequestException('Термін дії картки минув');
    }

    if (dto.isDefault) {
      await this.prisma.client.paymentMethod.updateMany({
        where: { userId },
        data: { isDefault: false },
      });
    }

    return this.prisma.client.paymentMethod.update({
      where: { id },
      data: {
        holderName: dto.holderName ?? method.holderName,
        expMonth,
        expYear,
        isDefault: dto.isDefault ?? method.isDefault,
      },
    });
  }

  async removePaymentMethod(userId: string, id: string) {
    await this.requirePaymentMethod(userId, id);

    await this.prisma.client.paymentMethod.delete({ where: { id } });

    return { deleted: true };
  }

  // ---------- Webhook від платіжного шлюзу ----------

  async handleWebhook(dto: WebhookDto, signature?: string) {
    if (!this.gateway.isSignatureValid(dto, signature)) {
      throw new UnauthorizedException('Невірний підпис webhook');
    }

    const payment = await this.prisma.client.payment.findUnique({
      where: { externalId: dto.externalId },
    });

    if (!payment) {
      throw new NotFoundException('Платіж не знайдено');
    }

    const subscription = payment.subscriptionId
      ? await this.prisma.client.userSubscription.findUnique({
          where: { id: payment.subscriptionId },
        })
      : null;

    if (dto.event === 'payment_succeeded') {
      const updatedPayment = await this.prisma.client.payment.update({
        where: { id: payment.id },
        data: { status: 'SUCCEEDED', failureReason: null },
      });

      return {
        payment: updatedPayment,
        subscription: subscription
          ? await this.activateSubscription(subscription)
          : null,
      };
    }

    if (dto.event === 'payment_failed') {
      const reason = dto.reason ?? 'Оплата не пройшла';

      const updatedPayment = await this.prisma.client.payment.update({
        where: { id: payment.id },
        data: { status: 'FAILED', failureReason: reason },
      });

      return {
        payment: updatedPayment,
        subscription: subscription
          ? await this.applyPaymentFailure(subscription, reason)
          : null,
      };
    }

    if (dto.event === 'grace_period_expired') {
      return {
        payment,
        subscription: subscription
          ? await this.suspendSubscription(subscription)
          : null,
      };
    }

    const cancelled = subscription
      ? await this.prisma.client.userSubscription.update({
          where: { id: subscription.id },
          data: {
            status: SubscriptionStatus.CANCELLED,
            cancelAtPeriodEnd: true,
          },
        })
      : null;

    return { payment, subscription: cancelled };
  }

  // ---------- Внутрішня логіка ----------

  private findCurrentSubscription(userId: string) {
    return this.prisma.client.userSubscription.findFirst({
      where: { userId },
      orderBy: { createdAt: 'desc' },
      include: { plan: true },
    });
  }

  // Рахуємо актуальний стан: період міг закінчитись, поки ніхто не дивився.
  private async resolveStatus(
    subscription: SubscriptionWithPlan | null,
  ): Promise<SubscriptionWithPlan | null> {
    if (!subscription) {
      return null;
    }

    const now = new Date();
    let status = subscription.status;
    let gracePeriodEndsAt = subscription.gracePeriodEndsAt;

    if (
      status === SubscriptionStatus.ACTIVE &&
      subscription.currentPeriodEnd &&
      subscription.currentPeriodEnd <= now
    ) {
      status = SubscriptionStatus.GRACE_PERIOD;
      gracePeriodEndsAt = new Date(
        subscription.currentPeriodEnd.getTime() + GRACE_PERIOD_DAYS * DAY_MS,
      );
    }

    if (
      status === SubscriptionStatus.GRACE_PERIOD &&
      gracePeriodEndsAt &&
      gracePeriodEndsAt <= now
    ) {
      status = SubscriptionStatus.SUSPENDED;
    }

    if (status === subscription.status) {
      return subscription;
    }

    // Grace Period сплив — блокуємо доступ і пишемо про це користувачу.
    if (status === SubscriptionStatus.SUSPENDED) {
      await this.suspendSubscription(subscription);

      return { ...subscription, status, gracePeriodEndsAt: null };
    }

    await this.prisma.client.userSubscription.update({
      where: { id: subscription.id },
      data: { status, gracePeriodEndsAt },
    });

    return { ...subscription, status, gracePeriodEndsAt };
  }

  // Можливості тарифу за станом підписки: активний чи Grace Period — преміум.
  private entitlementFor(status: string): Entitlement {
    return status === 'ACTIVE' || status === 'GRACE_PERIOD'
      ? PREMIUM_ENTITLEMENT
      : FREE_ENTITLEMENT;
  }

  private toStatusResponse(
    subscription: SubscriptionWithPlan | null,
    paymentsCount: number,
  ): SubscriptionStatusResponse {
    if (!subscription) {
      return {
        hasSubscription: false,
        status: 'FREE',
        title: 'Безкоштовний',
        message: 'У вас немає активної підписки',
        plan: null,
        currentPeriodEnd: null,
        gracePeriodEndsAt: null,
        cancelAtPeriodEnd: false,
        nextChargeDate: null,
        nextChargeAmountCents: null,
        paymentsCount,
        entitlement: this.entitlementFor('FREE'),
      };
    }

    return {
      hasSubscription: true,
      status: subscription.status,
      title: STATUS_TITLES[subscription.status],
      message: STATUS_MESSAGES[subscription.status],
      plan: subscription.plan
        ? {
            id: subscription.plan.id,
            title: subscription.plan.title,
            priceCents: subscription.plan.priceCents,
            currency: subscription.plan.currency,
          }
        : null,
      currentPeriodEnd: subscription.currentPeriodEnd,
      gracePeriodEndsAt: subscription.gracePeriodEndsAt,
      cancelAtPeriodEnd: subscription.cancelAtPeriodEnd,
      nextChargeDate: subscription.cancelAtPeriodEnd
        ? null
        : subscription.currentPeriodEnd,
      nextChargeAmountCents: subscription.plan
        ? subscription.plan.priceCents
        : null,
      paymentsCount,
      entitlement: this.entitlementFor(subscription.status),
    };
  }

  private async getOrCreateSubscription(
    userId: string,
    planId: string,
    provider: CheckoutDto['provider'],
  ) {
    const existing = await this.findCurrentSubscription(userId);

    if (existing) {
      return this.prisma.client.userSubscription.update({
        where: { id: existing.id },
        data: { planId, provider },
      });
    }

    return this.prisma.client.userSubscription.create({
      data: {
        userId,
        planId,
        provider,
        status: SubscriptionStatus.PENDING,
      },
    });
  }

  private async assertPaymentData(dto: CheckoutDto): Promise<void> {
    if (dto.provider === 'GOOGLE_PAY') {
      if (!dto.paymentToken) {
        throw new BadRequestException('Google Pay не повернув токен оплати');
      }

      return;
    }

    if (!dto.cardNumber || !dto.expMonth || !dto.expYear) {
      throw new BadRequestException('Вкажіть номер картки та строк дії');
    }

    if (!CardUtil.isValidNumber(dto.cardNumber)) {
      throw new BadRequestException('Перевірте номер картки');
    }

    if (CardUtil.isExpired(dto.expMonth, dto.expYear)) {
      throw new BadRequestException('Термін дії картки минув');
    }
  }

  private async rememberCard(userId: string, dto: CheckoutDto) {
    if (dto.provider !== 'CARD' || !dto.cardNumber) {
      return;
    }

    const last4 = CardUtil.last4(dto.cardNumber);

    const existing = await this.prisma.client.paymentMethod.findFirst({
      where: { userId, last4 },
    });

    if (existing) {
      return;
    }

    await this.prisma.client.paymentMethod.create({
      data: {
        userId,
        brand: CardUtil.detectBrand(dto.cardNumber),
        last4,
        expMonth: dto.expMonth ?? 1,
        expYear: dto.expYear ?? 2100,
        holderName: dto.holderName,
      },
    });
  }

  private async activateSubscription(subscription: {
    id: string;
    userId: string;
    planId: string;
  }) {
    const plan = await this.prisma.client.subscriptionPlan.findUnique({
      where: { id: subscription.planId },
    });

    const periodDays = plan?.periodDays ?? 30;
    const periodEnd = new Date(Date.now() + periodDays * DAY_MS);

    const updated = await this.prisma.client.userSubscription.update({
      where: { id: subscription.id },
      data: {
        status: SubscriptionStatus.ACTIVE,
        currentPeriodEnd: periodEnd,
        gracePeriodEndsAt: null,
        lastPaymentFailedAt: null,
        cancelAtPeriodEnd: false,
      },
    });

    await this.notifications.send({
      userId: subscription.userId,
      type: 'subscription',
      title: 'Підписка активна',
      body: `Доступ відкрито до ${periodEnd.toLocaleDateString('uk-UA')}`,
    });

    return updated;
  }

  private async applyPaymentFailure(
    subscription: {
      id: string;
      userId: string;
      status: SubscriptionStatus;
      gracePeriodEndsAt?: Date | null;
    },
    reason: string,
  ) {
    const now = new Date();

    // Якщо підписка вже заблокована — новий Grace Period не даємо.
    if (
      subscription.status === SubscriptionStatus.SUSPENDED ||
      subscription.status === SubscriptionStatus.CANCELLED
    ) {
      const updated = await this.prisma.client.userSubscription.update({
        where: { id: subscription.id },
        data: { lastPaymentFailedAt: now },
      });

      await this.notifications.send({
        userId: subscription.userId,
        type: 'subscription',
        title: STATUS_TITLES[subscription.status],
        body: reason,
      });

      return updated;
    }

    // 3 дні даються один раз: повторні спроби не продовжують Grace Period.
    const gracePeriodEndsAt =
      subscription.gracePeriodEndsAt && subscription.gracePeriodEndsAt > now
        ? subscription.gracePeriodEndsAt
        : new Date(now.getTime() + GRACE_PERIOD_DAYS * DAY_MS);

    const updated = await this.prisma.client.userSubscription.update({
      where: { id: subscription.id },
      data: {
        status: SubscriptionStatus.GRACE_PERIOD,
        lastPaymentFailedAt: now,
        gracePeriodEndsAt,
      },
    });

    await this.notifications.send({
      userId: subscription.userId,
      type: 'subscription',
      title: STATUS_TITLES[SubscriptionStatus.GRACE_PERIOD],
      body: reason,
    });

    return updated;
  }

  // Поки триває Grace Period — раз на добу пробуємо списати кошти знову.
  private async retryPaymentIfDue(subscription: SubscriptionWithPlan) {
    if (subscription.status !== SubscriptionStatus.GRACE_PERIOD) {
      return;
    }

    const lastAttempt = subscription.lastPaymentFailedAt?.getTime() ?? 0;

    if (Date.now() - lastAttempt < DAY_MS) {
      return;
    }

    const method = await this.prisma.client.paymentMethod.findFirst({
      where: { userId: subscription.userId, isDefault: true },
    });

    // Немає збереженої картки — просто чекаємо кінця Grace Period.
    if (!method) {
      return;
    }

    const charge = await this.gateway.chargeCard({
      amountCents: subscription.plan.priceCents,
      currency: subscription.plan.currency,
      cardNumber: method.last4,
    });

    await this.prisma.client.payment.create({
      data: {
        userId: subscription.userId,
        subscriptionId: subscription.id,
        amountCents: subscription.plan.priceCents,
        currency: subscription.plan.currency,
        status: charge.status,
        provider: subscription.provider,
        externalId: charge.externalId,
        failureReason: charge.failureReason,
      },
    });

    if (charge.status === 'SUCCEEDED') {
      await this.activateSubscription(subscription);
      return;
    }

    await this.applyPaymentFailure(
      subscription,
      charge.failureReason ?? 'Повторне списання не пройшло',
    );
  }

  // Grace Period сплив — доступ до HQ-потоку блокується.
  private async suspendSubscription(subscription: {
    id: string;
    userId: string;
  }) {
    const updated = await this.prisma.client.userSubscription.update({
      where: { id: subscription.id },
      data: {
        status: SubscriptionStatus.SUSPENDED,
        gracePeriodEndsAt: null,
      },
    });

    await this.notifications.send({
      userId: subscription.userId,
      type: 'subscription',
      title: STATUS_TITLES[SubscriptionStatus.SUSPENDED],
      body: STATUS_MESSAGES[SubscriptionStatus.SUSPENDED],
    });

    return updated;
  }

  private async requirePaymentMethod(userId: string, id: string) {
    const method = await this.prisma.client.paymentMethod.findUnique({
      where: { id },
    });

    // Чужу картку редагувати не можна.
    if (!method || method.userId !== userId) {
      throw new NotFoundException('Картку не знайдено');
    }

    return method;
  }
}
