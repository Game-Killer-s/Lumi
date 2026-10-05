import { ApiProperty } from '@nestjs/swagger';

import {
  IsNotEmpty,
  IsString,
  MaxLength,
} from 'class-validator';

export class GoogleLoginDto {
  @ApiProperty({
    description: 'Google ID token obtained from the Android Google Sign-In flow',
  })
  @IsString()
  @IsNotEmpty()
  @MaxLength(8192)
  idToken!: string;
}
