import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import {
  IsBoolean,
  IsInt,
  IsOptional,
  IsString,
  Matches,
  Max,
  MaxLength,
  Min,
} from 'class-validator';

// Нова картка на екрані платіжних даних.
export class AddPaymentMethodDto {
  @ApiProperty({ example: '4242424242424242' })
  @IsString()
  @Matches(/^\d{13,19}$/, { message: 'cardNumber must contain 13-19 digits' })
  cardNumber!: string;

  @ApiPropertyOptional({ example: 'TARAS SHEVCHENKO' })
  @IsOptional()
  @IsString()
  @MaxLength(60)
  holderName?: string;

  @ApiProperty({ example: 12 })
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(12)
  expMonth!: number;

  @ApiProperty({ example: 2030 })
  @Type(() => Number)
  @IsInt()
  @Min(2024)
  @Max(2100)
  expYear!: number;

  @ApiPropertyOptional({ example: '123' })
  @IsOptional()
  @IsString()
  @Matches(/^\d{3,4}$/, { message: 'cvc must contain 3-4 digits' })
  cvc?: string;

  @ApiPropertyOptional({ example: true })
  @IsOptional()
  @IsBoolean()
  isDefault?: boolean;
}

// Зміна строку дії або власника картки.
export class UpdatePaymentMethodDto {
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

  @ApiPropertyOptional({ example: true })
  @IsOptional()
  @IsBoolean()
  isDefault?: boolean;
}
