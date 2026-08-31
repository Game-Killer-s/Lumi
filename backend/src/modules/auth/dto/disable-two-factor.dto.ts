import { IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class DisableTwoFactorDto {
  @IsOptional()
  @IsString()
  password?: string;

  @IsString()
  @MinLength(6)
  @MaxLength(64)
  code!: string;
}
