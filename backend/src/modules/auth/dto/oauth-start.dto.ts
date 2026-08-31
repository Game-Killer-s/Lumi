import { ApiProperty } from '@nestjs/swagger';

import {
  IsString,
  MaxLength,
} from 'class-validator';

export class OAuthStartDto {
  @ApiProperty({
    example: 'lumi://oauth/callback',
    description:
      'Allowed URL to return to after OAuth authentication',
    maxLength: 512,
  })
  @IsString()
  @MaxLength(512)
  returnTo!: string;
}