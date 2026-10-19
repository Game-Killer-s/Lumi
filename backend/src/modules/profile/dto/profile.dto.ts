import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Role } from '@prisma/client';
import { Transform } from 'class-transformer';
import {
  IsEmail,
  IsOptional,
  IsString,
  Matches,
  MaxLength,
} from 'class-validator';

import { normalizeEmail, normalizeNickname } from '../profile.utils';

function normalizeNicknameValue(value: unknown): unknown {
  return typeof value === 'string' ? normalizeNickname(value) : value;
}

function normalizeEmailValue(value: unknown): unknown {
  return typeof value === 'string' ? normalizeEmail(value) : value;
}

export class ProfileDto {
  @ApiProperty()
  id!: string;

  @ApiProperty({ example: 'vasyl' })
  nickname!: string;

  @ApiProperty({ example: 'user@example.com' })
  email!: string;

  @ApiProperty({ nullable: true, type: String })
  avatarUrl!: string | null;

  @ApiProperty({ enum: Role })
  role!: Role;

  @ApiProperty({ example: true })
  isPublic!: boolean;

  @ApiProperty({ example: true })
  showCurrentListening!: boolean;

  @ApiProperty({ type: String, format: 'date-time' })
  createdAt!: Date;
}

export class UpdateProfileDto {
  @ApiPropertyOptional({ example: 'vasyl' })
  @Transform(({ value }) => normalizeNicknameValue(value))
  @IsOptional()
  @IsString()
  @Matches(/\S/, { message: 'nickname must not be blank' })
  @MaxLength(50)
  nickname?: string;

  @ApiPropertyOptional({ example: 'user@example.com' })
  @Transform(({ value }) => normalizeEmailValue(value))
  @IsOptional()
  @IsEmail()
  @MaxLength(254)
  email?: string;
}
