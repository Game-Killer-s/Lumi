import { ApiProperty } from '@nestjs/swagger';

import {
  IsEmail,
  IsNotEmpty,
  IsString,
  Matches,
  MaxLength,
  MinLength,
} from 'class-validator';

export class RegisterDto {
  @ApiProperty({
    example: 'vasyl',
    description: 'User nickname',
  })
  @IsString()
  @IsNotEmpty()
  @MaxLength(50)
  nickname!: string;

  @ApiProperty({
    example: 'user@example.com',
    description: 'User email',
  })
  @IsString()
  @IsNotEmpty()
  @IsEmail()
  @MaxLength(254)
  email!: string;

  @ApiProperty({
    example: 'StrongPassword123',
    format: 'password',
    writeOnly: true,
  })
  @IsString()
  @IsNotEmpty()
  @MinLength(8)
  @MaxLength(128)
  @Matches(/(?=.*[a-zA-Z])(?=.*\d)/, {
    message: 'password must contain letters and digits',
  })
  password!: string;
}
