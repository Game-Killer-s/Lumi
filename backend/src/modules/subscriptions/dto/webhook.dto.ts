import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsIn, IsNotEmpty, IsOptional, IsString, MaxLength } from 'class-validator';

export const WEBHOOK_EVENTS = [
  'payment_succeeded',
  'payment_failed',
  'grace_period_expired',
  'subscription_cancelled',
] as const;

export type WebhookEvent = (typeof WEBHOOK_EVENTS)[number];

// Те, що платіжний шлюз надсилає нам після оплати.
export class WebhookDto {
  @ApiProperty({ enum: WEBHOOK_EVENTS, example: 'payment_succeeded' })
  @IsIn(WEBHOOK_EVENTS)
  event!: WebhookEvent;

  @ApiProperty({ example: 'pay_6f0a...' })
  @IsString()
  @IsNotEmpty()
  externalId!: string;

  @ApiPropertyOptional({ example: 'Недостатньо коштів' })
  @IsOptional()
  @IsString()
  @MaxLength(200)
  reason?: string;
}
