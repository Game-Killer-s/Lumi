import {
  BadRequestException,
  NotFoundException,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Test, TestingModule } from '@nestjs/testing';

import { PrismaService } from '../../prisma/prisma.service';
import { NotificationsService } from '../notifications/notifications.service';

import { PaymentGatewayService } from './payment-gateway.service';
import { SubscriptionsService } from './subscriptions.service';

describe('SubscriptionsService', () => {
  let service: SubscriptionsService;
  let gateway: PaymentGatewayService;

  let subscriptionState: Record<string, any> | null;
  let lastPayment: Record<string, any> | null;

  let prisma: {
    client: {
      subscriptionPlan: { findUnique: jest.Mock; findMany: jest.Mock };
      userSubscription: {
        findFirst: jest.Mock;
        findUnique: jest.Mock;
        create: jest.Mock;
        update: jest.Mock;
      };
      payment: {
        create: jest.Mock;
        findUnique: jest.Mock;
        findMany: jest.Mock;
        count: jest.Mock;
        update: jest.Mock;
      };
      paymentMethod: {
        findFirst: jest.Mock;
        findMany: jest.Mock;
        findUnique: jest.Mock;
        create: jest.Mock;
        update: jest.Mock;
        updateMany: jest.Mock;
        delete: jest.Mock;
      };
    };
  };

  const notifications = { send: jest.fn().mockResolvedValue({ sent: true }) };

  const premiumPlan = {
    id: 'plan-premium',
    code: 'premium',
    title: 'Premium',
    priceCents: 14900,
    currency: 'UAH',
    periodDays: 30,
    isActive: true,
  };

  const validCard = {
    planId: 'plan-premium',
    provider: 'CARD' as const,
    cardNumber: '4242424242424242',
    expMonth: 12,
    expYear: 2030,
  };

  const seedSubscription = (overrides: Record<string, any> = {}) => {
    subscriptionState = {
      id: 'sub-1',
      userId: 'u1',
      planId: premiumPlan.id,
      status: 'ACTIVE',
      currentPeriodEnd: new Date(Date.now() + 5 * 24 * 60 * 60 * 1000),
      gracePeriodEndsAt: null,
      cancelAtPeriodEnd: false,
      plan: premiumPlan,
      ...overrides,
    };
  };

  const seedPayment = () => {
    seedSubscription();

    lastPayment = {
      id: 'pay-1',
      userId: 'u1',
      subscriptionId: 'sub-1',
      externalId: 'pay_ext_1',
      status: 'PENDING',
    };
  };

  beforeEach(async () => {
    subscriptionState = null;
    lastPayment = null;

    prisma = {
      client: {
        subscriptionPlan: {
          findUnique: jest.fn().mockResolvedValue(premiumPlan),
          findMany: jest.fn().mockResolvedValue([premiumPlan]),
        },
        userSubscription: {
          findFirst: jest.fn(() => Promise.resolve(subscriptionState)),
          findUnique: jest.fn(() => Promise.resolve(subscriptionState)),
          create: jest.fn((args: any) => {
            subscriptionState = {
              id: 'sub-1',
              status: 'PENDING',
              currentPeriodEnd: null,
              gracePeriodEndsAt: null,
              cancelAtPeriodEnd: false,
              ...args.data,
            };

            return Promise.resolve(subscriptionState);
          }),
          update: jest.fn((args: any) => {
            subscriptionState = { ...subscriptionState, ...args.data };

            return Promise.resolve(subscriptionState);
          }),
        },
        payment: {
          create: jest.fn((args: any) => {
            lastPayment = { id: 'pay-1', ...args.data };

            return Promise.resolve(lastPayment);
          }),
          findUnique: jest.fn(() => Promise.resolve(lastPayment)),
          findMany: jest.fn().mockResolvedValue([]),
          count: jest.fn().mockResolvedValue(1),
          update: jest.fn((args: any) => {
            lastPayment = { ...lastPayment, ...args.data };

            return Promise.resolve(lastPayment);
          }),
        },
        paymentMethod: {
          findFirst: jest.fn().mockResolvedValue(null),
          findMany: jest.fn().mockResolvedValue([]),
          findUnique: jest.fn().mockResolvedValue(null),
          create: jest.fn((args: any) =>
            Promise.resolve({ id: 'pm-1', ...args.data }),
          ),
          update: jest.fn((args: any) =>
            Promise.resolve({ id: 'pm-1', ...args.data }),
          ),
          updateMany: jest.fn().mockResolvedValue({ count: 1 }),
          delete: jest.fn().mockResolvedValue({ id: 'pm-1' }),
        },
      },
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        SubscriptionsService,
        PaymentGatewayService,
        { provide: PrismaService, useValue: prisma },
        { provide: NotificationsService, useValue: notifications },
        {
          provide: ConfigService,
          useValue: {
            get: jest.fn((key: string) =>
              key === 'payments.webhookSecret' ? 'test-secret' : undefined,
            ),
          },
        },
      ],
    }).compile();

    service = module.get(SubscriptionsService);
    gateway = module.get(PaymentGatewayService);
  });

  it('returns active plans for the tariff screen', async () => {
    const plans = await service.listPlans();

    expect(plans).toEqual([premiumPlan]);
    expect(prisma.client.subscriptionPlan.findMany).toHaveBeenCalledWith({
      where: { isActive: true },
      orderBy: { priceCents: 'asc' },
    });
  });

  it('activates the subscription after a successful payment', async () => {
    const result = await service.checkout('u1', validCard);

    expect(result.subscription.status).toBe('ACTIVE');
    expect(subscriptionState?.status).toBe('ACTIVE');
    expect(subscriptionState?.currentPeriodEnd).toBeInstanceOf(Date);
    expect(lastPayment?.status).toBe('SUCCEEDED');
    expect(prisma.client.paymentMethod.create).toHaveBeenCalled();
    expect(notifications.send).toHaveBeenCalled();
  });

  it('rejects a card that does not pass the Luhn check', async () => {
    await expect(
      service.checkout('u1', { ...validCard, cardNumber: '1234567890123' }),
    ).rejects.toThrow(BadRequestException);
  });

  it('throws when the chosen plan does not exist', async () => {
    prisma.client.subscriptionPlan.findUnique.mockResolvedValue(null);

    await expect(service.checkout('u1', validCard)).rejects.toThrow(
      NotFoundException,
    );
  });

  it('moves the subscription to grace period when the bank declines', async () => {
    await expect(
      service.checkout('u1', { ...validCard, cardNumber: '4000000000000002' }),
    ).rejects.toThrow(BadRequestException);

    expect(subscriptionState?.status).toBe('GRACE_PERIOD');
    expect(subscriptionState?.gracePeriodEndsAt).toBeInstanceOf(Date);
    expect(lastPayment?.status).toBe('FAILED');
  });

  it('shows the free status when there is no subscription', async () => {
    const status = await service.getStatus('u1');

    expect(status.hasSubscription).toBe(false);
    expect(status.status).toBe('FREE');
  });

  it('turns an expired active subscription into grace period', async () => {
    seedSubscription({ currentPeriodEnd: new Date(Date.now() - 1000) });

    const status = await service.getStatus('u1');

    expect(status.status).toBe('GRACE_PERIOD');
    expect(status.gracePeriodEndsAt).toBeInstanceOf(Date);
    expect(prisma.client.userSubscription.update).toHaveBeenCalled();
  });

  it('allows playback during the grace period', async () => {
    seedSubscription({
      status: 'GRACE_PERIOD',
      currentPeriodEnd: new Date(Date.now() - 1000),
      gracePeriodEndsAt: new Date(Date.now() + 24 * 60 * 60 * 1000),
    });

    const access = await service.checkPlayAccess('u1');

    expect(access.allowed).toBe(true);
    expect(access.status).toBe('GRACE_PERIOD');
  });

  it('suspends playback when the grace period is over', async () => {
    seedSubscription({
      status: 'GRACE_PERIOD',
      currentPeriodEnd: new Date(Date.now() - 10 * 24 * 60 * 60 * 1000),
      gracePeriodEndsAt: new Date(Date.now() - 1000),
    });

    const access = await service.checkPlayAccess('u1');

    expect(access.allowed).toBe(false);
    expect(access.status).toBe('SUSPENDED');
  });

  it('allows playback for a listener without a subscription', async () => {
    const access = await service.checkPlayAccess('u1');

    expect(access.allowed).toBe(true);
    expect(access.status).toBe('FREE');
  });

  it('cancels the subscription at the end of the paid period', async () => {
    seedSubscription();

    const result = await service.cancel('u1');

    expect(result.cancelAtPeriodEnd).toBe(true);
    expect(subscriptionState?.cancelAtPeriodEnd).toBe(true);
    expect(subscriptionState?.status).toBe('ACTIVE');
  });

  it('closes access right away when there is no paid period left', async () => {
    seedSubscription({ status: 'PENDING', currentPeriodEnd: null });

    await service.cancel('u1');

    expect(subscriptionState?.status).toBe('CANCELLED');
  });

  it('throws when cancelling without a subscription', async () => {
    await expect(service.cancel('u1')).rejects.toThrow(NotFoundException);
  });

  it('rejects a webhook with a wrong signature', async () => {
    seedPayment();

    await expect(
      service.handleWebhook(
        { event: 'payment_succeeded', externalId: 'pay_ext_1' },
        'wrong-signature',
      ),
    ).rejects.toThrow(UnauthorizedException);
  });

  it('activates the subscription on a successful webhook', async () => {
    seedPayment();

    const payload = {
      event: 'payment_succeeded' as const,
      externalId: 'pay_ext_1',
    };

    const result = await service.handleWebhook(payload, gateway.sign(payload));

    expect(result.payment.status).toBe('SUCCEEDED');
    expect(subscriptionState?.status).toBe('ACTIVE');
  });

  it('keeps the subscription in grace period while the bank keeps refusing', async () => {
    seedPayment();

    const payload = {
      event: 'payment_failed' as const,
      externalId: 'pay_ext_1',
      reason: 'Недостатньо коштів',
    };

    await service.handleWebhook(payload, gateway.sign(payload));
    expect(subscriptionState?.status).toBe('GRACE_PERIOD');

    const graceEnd = subscriptionState?.gracePeriodEndsAt;

    await service.handleWebhook(payload, gateway.sign(payload));

    // Повторні спроби не подовжують 3-денний Grace Period.
    expect(subscriptionState?.status).toBe('GRACE_PERIOD');
    expect(subscriptionState?.gracePeriodEndsAt).toEqual(graceEnd);
    expect(subscriptionState?.lastPaymentFailedAt).toBeInstanceOf(Date);
  });

  it('suspends the subscription when the grace period is over', async () => {
    seedPayment();

    const payload = {
      event: 'grace_period_expired' as const,
      externalId: 'pay_ext_1',
    };

    await service.handleWebhook(payload, gateway.sign(payload));

    expect(subscriptionState?.status).toBe('SUSPENDED');
    expect(subscriptionState?.gracePeriodEndsAt).toBeNull();
  });

  it('retries the charge once a day and activates the subscription again', async () => {
    seedSubscription({
      status: 'GRACE_PERIOD',
      gracePeriodEndsAt: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000),
      lastPaymentFailedAt: new Date(Date.now() - 25 * 60 * 60 * 1000),
    });

    prisma.client.paymentMethod.findFirst.mockResolvedValue({
      id: 'pm-1',
      userId: 'u1',
      last4: '4242',
      isDefault: true,
    });

    const status = await service.getStatus('u1');

    expect(status.status).toBe('ACTIVE');
    expect(lastPayment?.status).toBe('SUCCEEDED');
  });

  it('keeps the grace period when the saved card fails again', async () => {
    seedSubscription({
      status: 'GRACE_PERIOD',
      currentPeriodEnd: new Date(Date.now() - 1000),
      gracePeriodEndsAt: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000),
      lastPaymentFailedAt: new Date(Date.now() - 25 * 60 * 60 * 1000),
    });

    prisma.client.paymentMethod.findFirst.mockResolvedValue({
      id: 'pm-1',
      userId: 'u1',
      last4: '0002',
      isDefault: true,
    });

    const status = await service.getStatus('u1');

    expect(status.status).toBe('GRACE_PERIOD');
    expect(lastPayment?.status).toBe('FAILED');
  });

  it('returns the payment history for the cabinet', async () => {
    prisma.client.payment.findMany.mockResolvedValue([
      { id: 'pay-1', status: 'SUCCEEDED' },
    ]);

    const history = await service.listPayments('u1');

    expect(history).toHaveLength(1);
    expect(prisma.client.payment.findMany).toHaveBeenCalledWith({
      where: { userId: 'u1' },
      orderBy: { createdAt: 'desc' },
      take: 20,
    });
  });

  it('rejects a webhook for an unknown payment', async () => {
    const payload = {
      event: 'payment_succeeded' as const,
      externalId: 'missing',
    };

    await expect(
      service.handleWebhook(payload, gateway.sign(payload)),
    ).rejects.toThrow(NotFoundException);
  });

  it('saves only the masked card number', async () => {
    const method = await service.addPaymentMethod('u1', {
      cardNumber: '4242424242424242',
      expMonth: 12,
      expYear: 2030,
      holderName: 'TARAS SHEVCHENKO',
    });

    expect(method.last4).toBe('4242');
    expect(method.brand).toBe('VISA');
  });

  it('rejects an expired card', async () => {
    await expect(
      service.addPaymentMethod('u1', {
        cardNumber: '4242424242424242',
        expMonth: 1,
        expYear: 2024,
      }),
    ).rejects.toThrow(BadRequestException);
  });

  it('does not let a user delete a card of someone else', async () => {
    prisma.client.paymentMethod.findUnique.mockResolvedValue({
      id: 'pm-1',
      userId: 'u2',
    });

    await expect(service.removePaymentMethod('u1', 'pm-1')).rejects.toThrow(
      NotFoundException,
    );
  });

  // ---------- Story 4: можливості тарифу ----------

  it('gives premium capabilities to an active subscription', async () => {
    seedSubscription();

    const status = await service.getStatus('u1');

    expect(status.entitlement).toEqual({
      tier: 'PREMIUM',
      maxQuality: 'LOSSLESS',
      canUseHq: true,
      canUseLossless: true,
      canDownload: true,
    });
  });

  it('keeps premium capabilities during the grace period', async () => {
    seedSubscription({
      status: 'GRACE_PERIOD',
      gracePeriodEndsAt: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000),
    });

    const status = await service.getStatus('u1');

    expect(status.entitlement.canUseHq).toBe(true);
    expect(status.entitlement.canUseLossless).toBe(true);
  });

  it('limits a user without a subscription to standard quality', async () => {
    const status = await service.getStatus('u1');

    expect(status.entitlement).toEqual({
      tier: 'FREE',
      maxQuality: 'STANDARD',
      canUseHq: false,
      canUseLossless: false,
      canDownload: false,
    });
  });

  it('plays in standard quality when there is no subscription', async () => {
    const access = await service.checkPlayAccess('u1');

    expect(access.allowed).toBe(true);
    expect(access.quality).toBe('STANDARD');
  });

  it('restores the entitlement after a successful retry payment', async () => {
    seedSubscription({
      status: 'GRACE_PERIOD',
      currentPeriodEnd: new Date(Date.now() - 1000),
      gracePeriodEndsAt: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000),
      lastPaymentFailedAt: new Date(Date.now() - 25 * 60 * 60 * 1000),
    });

    prisma.client.paymentMethod.findFirst.mockResolvedValue({
      id: 'pm-1',
      userId: 'u1',
      last4: '4242',
      isDefault: true,
    });

    const status = await service.getStatus('u1');

    // Банк прийняв оплату: Grace Period закрито, преміум-доступ повернувся.
    expect(status.status).toBe('ACTIVE');
    expect(status.gracePeriodEndsAt).toBeNull();

    expect(status.entitlement.canUseHq).toBe(true);
    expect(status.entitlement.canUseLossless).toBe(true);
    expect(status.entitlement.canDownload).toBe(true);
  });

  it('closes hq access for a suspended subscription', async () => {
    seedSubscription({ status: 'SUSPENDED' });

    const status = await service.getStatus('u1');

    expect(status.entitlement.canUseHq).toBe(false);

    const access = await service.checkPlayAccess('u1');

    expect(access.allowed).toBe(false);
    expect(access.quality).toBe('NONE');
  });
});
