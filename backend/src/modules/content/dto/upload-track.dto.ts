import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString, MaxLength } from 'class-validator';

export class UploadTrackDto {
  @ApiProperty({
    example: 'https://cdn.lumi.app/audio/midnight-drive.mp3',
    description: 'URL завантаженого аудіофайлу',
  })
  @IsString()
  @IsNotEmpty()
  @MaxLength(2048)
  audioUrl!: string;
}
