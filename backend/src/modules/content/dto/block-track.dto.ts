import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString, MaxLength } from 'class-validator';

export class BlockTrackDto {
  @ApiProperty({
    example: 'Copyright violation',
    description: 'Причина блокування (обов’язкова)',
  })
  @IsString()
  @IsNotEmpty()
  @MaxLength(500)
  reason!: string;
}
