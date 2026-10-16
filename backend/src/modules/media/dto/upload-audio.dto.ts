import { ApiProperty } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsInt, IsOptional, IsString, MaxLength, Min } from 'class-validator';

/**
 * Метадані, які надсилають разом з аудіофайлом
 * (multipart/form-data: file = audio + ці поля).
 * Сам файл читає multer, тому тут тільки текстові поля форми.
 */
export class UploadAudioDto {
  @ApiProperty({ required: false, example: 'Midnight Drive' })
  @IsOptional()
  @IsString()
  @MaxLength(200)
  title?: string;

  @ApiProperty({ required: false, example: 214 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  duration?: number;

  @ApiProperty({ required: false, description: 'Текст пісні' })
  @IsOptional()
  @IsString()
  @MaxLength(5000)
  lyrics?: string;
}
