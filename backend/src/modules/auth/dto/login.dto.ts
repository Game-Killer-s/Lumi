import { ApiProperty } from '@nestjs/swagger';
import {
  IsNotEmpty,
  IsString,
  MaxLength,
} from 'class-validator';

export class LoginDto {
  @ApiProperty({
    example: 'user@example.com',
    description: 'User login or email',
  })
  @IsString()
  @IsNotEmpty()
  @MaxLength(254)
  login!: string;

  @ApiProperty({
    example: 'StrongPassword123!',
    format: 'password',
    writeOnly: true,
  })
  @IsString()
  @IsNotEmpty()
  @MaxLength(128)
  password!: string;
}