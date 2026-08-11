import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsUrl } from 'class-validator';

export class ReplaceCoverDto {
  @ApiProperty({
    example: 'https://cdn.lumi.app/covers/new-cover.jpg',
    description: 'Нова URL обкладинки треку',
  })
  @IsUrl()
  @IsNotEmpty()
  coverUrl!: string;
}
