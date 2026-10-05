import { ApiPropertyOptional } from '@nestjs/swagger';
import { IsBoolean, IsOptional } from 'class-validator';

// Типи сповіщень, які користувач може вмикати й вимикати.
export class UpdateNotificationPreferencesDto {
  @ApiPropertyOptional({ description: 'Нові релізи моїх артистів' })
  @IsOptional()
  @IsBoolean()
  new_release?: boolean;

  @ApiPropertyOptional({ description: 'Оновлення від виконавців' })
  @IsOptional()
  @IsBoolean()
  artist_update?: boolean;

  @ApiPropertyOptional({ description: 'Промо та акції платформи' })
  @IsOptional()
  @IsBoolean()
  promo?: boolean;

  @ApiPropertyOptional({ description: 'Системні повідомлення' })
  @IsOptional()
  @IsBoolean()
  system?: boolean;
}
