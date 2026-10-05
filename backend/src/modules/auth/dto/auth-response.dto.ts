import { ApiProperty } from '@nestjs/swagger';

import { Role } from '@prisma/client';

import { TokenPairDto } from './token-pair.dto';

export class AuthUserDto {
  @ApiProperty({ example: 'uuid' })
  id!: string;

  @ApiProperty({ example: 'vasyl' })
  nickname!: string;

  @ApiProperty({ example: 'user@example.com' })
  email!: string;

  @ApiProperty({ example: null, nullable: true })
  avatarUrl!: string | null;

  @ApiProperty({ example: 'LISTENER', enum: Role })
  role!: Role;

  @ApiProperty({ example: '2026-08-24T12:00:00.000Z' })
  createdAt!: Date;
}

export class AuthResponseDto extends TokenPairDto {
  @ApiProperty({ type: AuthUserDto })
  user!: AuthUserDto;
}
