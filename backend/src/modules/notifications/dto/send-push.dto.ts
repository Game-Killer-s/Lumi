import { ApiProperty } from '@nestjs/swagger';
import { IsIn, IsNotEmpty, IsString, MaxLength } from 'class-validator';

export const PUSH_TYPES = [
  'new_release',
  'artist_update',
  'platform_update',
  'subscription',
] as const;

export type PushType = (typeof PUSH_TYPES)[number];

// Запит на відправку push-сповіщення (викликає бекенд, напр. після релізу).
export class SendPushDto {
  @ApiProperty({ enum: PUSH_TYPES, example: 'new_release' })
  @IsIn(PUSH_TYPES)
  type!: PushType;

  @ApiProperty({ example: 'Новий реліз: Luna Waves' })
  @IsString()
  @IsNotEmpty()
  @MaxLength(100)
  title!: string;

  @ApiProperty({ example: 'Трек «Midnight Drive» уже доступний' })
  @IsString()
  @IsNotEmpty()
  @MaxLength(300)
  body!: string;
}
