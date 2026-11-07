import { ApiProperty } from '@nestjs/swagger';
import {
  IsNotEmpty,
  IsString,
  Matches,
  MaxLength,
  MinLength,
} from 'class-validator';

export class ChangePasswordDto {
  @ApiProperty({
    example: 'OldPassword123',
    format: 'password',
    writeOnly: true,
  })
  @IsString()
  @IsNotEmpty()
  @MaxLength(128)
  currentPassword!: string;

  @ApiProperty({
    example: 'NewPassword123',
    format: 'password',
    writeOnly: true,
  })
  @IsString()
  @IsNotEmpty()
  @MinLength(8)
  @MaxLength(128)
  @Matches(/(?=.*[a-zA-Z])(?=.*\d)/, {
    message: 'newPassword must contain letters and digits',
  })
  newPassword!: string;
}
