import { ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsInt,
  IsNotEmpty,
  IsOptional,
  IsString,
  IsUrl,
  IsUUID,
  Max,
  MaxLength,
  Min,
} from 'class-validator';

/**
 * DTO для редагування метаданих треку адміністратором.
 * Усі поля опційні - оновлюється лише те, що передали.
 */
export class AdminTrackUpdateDto {
  @ApiPropertyOptional({
    example: 'Midnight Drive (Remastered)',
  })
  @IsOptional()
  @IsString()
  @IsNotEmpty()
  @MaxLength(200)
  title?: string;

  @ApiPropertyOptional({
    example: 214,
  })
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(86400)
  duration?: number;

  @ApiPropertyOptional({
    example: 'Some lyrics...',
  })
  @IsOptional()
  @IsString()
  @MaxLength(10000)
  lyrics?: string;

  @ApiPropertyOptional({
    example: 'https://cdn.lumi.app/covers/new-cover.jpg',
  })
  @IsOptional()
  @IsUrl()
  coverUrl?: string;

  @ApiPropertyOptional({
    description: 'Новий виконавець (id має існувати)',
  })
  @IsOptional()
  @IsUUID()
  artistId?: string;

  @ApiPropertyOptional({
    description:
      'Альбом (id має існувати і належати виконавцю). null - прибрати',
    nullable: true,
  })
  @IsOptional()
  @IsUUID()
  albumId?: string | null;

  @ApiPropertyOptional({
    description: 'Жанр (id має існувати). null - прибрати',
    nullable: true,
  })
  @IsOptional()
  @IsUUID()
  genreId?: string | null;
}
