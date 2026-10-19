import { ApiProperty } from '@nestjs/swagger';

// Публічна проєкція: без email, ролі та будь-яких security-полів.
export class PublicProfileDto {
  @ApiProperty()
  id!: string;

  @ApiProperty({ example: 'vasyl' })
  nickname!: string;

  @ApiProperty({ nullable: true, type: String })
  avatarUrl!: string | null;

  @ApiProperty({ type: String, format: 'date-time' })
  createdAt!: Date;
}

export class PublicProfileListDto {
  @ApiProperty({ type: [PublicProfileDto] })
  items!: PublicProfileDto[];

  @ApiProperty({ example: 1 })
  page!: number;

  @ApiProperty({ example: 20 })
  limit!: number;

  @ApiProperty({ example: 1 })
  total!: number;
}
