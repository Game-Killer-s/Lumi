import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString, MaxLength } from 'class-validator';

export class RefreshTokenDto {
  @ApiProperty({
    example: 'eyJhbGciOiJIUzI1NiIs...',
    description: 'Refresh token issued during login or refresh',
  })
  @IsString()
  @IsNotEmpty()
  @MaxLength(4096)
  refreshToken!: string;
}
