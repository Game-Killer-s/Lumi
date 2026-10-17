import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createHmac, randomUUID } from 'crypto';

export interface ChargeInput {
  amountCents: number;
  currency: string;
  cardNumber?: string;
  paymentToken?: string;
}

export interface ChargeResult {
  externalId: string;
  status: 'SUCCEEDED' | 'FAILED';
  failureReason?: string;
}

export interface WebhookSignaturePayload {
  event: string;
  externalId: string;
}

// Заглушка платіжного шлюзу (Stripe / LiqPay).
// Коли з'являться ключі — тут буде реальний виклик API.
@Injectable()
export class PaymentGatewayService {
  private readonly logger = new Logger(PaymentGatewayService.name);

  constructor(private readonly config: ConfigService) {}

  async chargeCard(input: ChargeInput): Promise<ChargeResult> {
    const externalId = `pay_${randomUUID()}`;
    const digits = (input.cardNumber ?? '').replace(/\D/g, '');

    // Демо-картки: номери на ...0002 і ...0000 банк відхиляє.
    if (digits.endsWith('0002') || digits.endsWith('0000')) {
      this.logger.warn(`charge ${externalId} failed`);

      return {
        externalId,
        status: 'FAILED',
        failureReason: 'Картку відхилено банком',
      };
    }

    this.logger.log(
      `charge ${externalId} ok (${input.amountCents} ${input.currency})`,
    );

    return { externalId, status: 'SUCCEEDED' };
  }

  // Підпис webhook рахуємо так само, як це робить шлюз.
  sign(payload: WebhookSignaturePayload): string {
    return createHmac('sha256', this.getSecret())
      .update(`${payload.event}:${payload.externalId}`)
      .digest('hex');
  }

  isSignatureValid(
    payload: WebhookSignaturePayload,
    signature?: string,
  ): boolean {
    if (!signature) {
      return false;
    }

    return this.sign(payload) === signature;
  }

  private getSecret(): string {
    return this.config.get<string>('payments.webhookSecret') ?? '';
  }
}
