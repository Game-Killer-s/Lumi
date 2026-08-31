import { ApiProperty } from '@nestjs/swagger';

import {
  IsString,
  MaxLength,
} from 'class-validator';

export class OAuthExchangeDto {
  @ApiProperty({
    example: 'Yp-M8ChP54hZxVj2Y...',
    description:
      'Short-lived one-time OAuth handoff code returned to the Lumi application',
    maxLength: 256,
  })
  @IsString()
  @MaxLength(256)
  code!: string;
}