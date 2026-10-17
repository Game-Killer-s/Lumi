import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import {
  IsIn,
  IsInt,
  IsNotEmpty,
  IsOptional,
  IsString,
  Matches,
  Max,
  MaxLength,
  Min,
} from 'class-validator';

import { PaymentProvider } from '@prisma/client';

// Оформлення підписки: обраний тариф + платіжні дані.
export class CheckoutDto {
  @ApiProperty({ example: '9a2f1c56-...' })
  @IsString()
  @IsNotEmpty()
  planId!: string;

  @ApiPropertyOptional({ enum: ['CARD', 'GOOGLE_PAY'], default: 'CARD' })
  @IsOptional()
  @IsIn(['CARD', 'GOOGLE_PAY'])
  provider: PaymentProvider = PaymentProvider.CARD;

  @ApiPropertyOptional({ example: '4242424242424242' })
  @IsOptional()
  @IsString()
  @Matches(/^\d{13,19}$/, { message: 'cardNumber must contain 13-19 digits' })
  cardNumber?: string;

  @ApiPropertyOptional({ example: 'TARAS SHEVCHENKO' })
  @IsOptional()
  @IsString()
  @MaxLength(60)
  holderName?: string;

  @ApiPropertyOptional({ example: 12 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(12)
  expMonth?: number;

  @ApiPropertyOptional({ example: 2030 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(2024)
  @Max(2100)
  expYear?: number;

  @ApiPropertyOptional({ example: '123' })
  @IsOptional()
  @IsString()
  @Matches(/^\d{3,4}$/, { message: 'cvc must contain 3-4 digits' })
  cvc?: string;

  // Для Google Pay замість картки приходить токен від Google.
  @ApiPropertyOptional({ example: 'gpay_token_...' })
  @IsOptional()
  @IsString()
  @MaxLength(300)
  paymentToken?: string;
}
