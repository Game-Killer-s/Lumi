import { ApiProperty } from '@nestjs/swagger';
import { IsOptional, IsString, MaxLength } from 'class-validator';

/**
 * Метадані для multipart-завантаження обкладинки
 * (multipart/form-data: file = cover + ці поля).
 */
export class UploadCoverDto {
  @ApiProperty({ required: false, description: 'Підпис обкладинки' })
  @IsOptional()
  @IsString()
  @MaxLength(200)
  title?: string;
}
