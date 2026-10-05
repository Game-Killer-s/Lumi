import { ApiPropertyOptional } from '@nestjs/swagger';
import { IsBoolean, IsOptional } from 'class-validator';

// Свитчи на екрані налаштувань сповіщень.
export class UpdateNotificationSettingsDto {
  @ApiPropertyOptional({ description: 'Нові релізи від моїх артистів' })
  @IsOptional()
  @IsBoolean()
  newReleases?: boolean;

  @ApiPropertyOptional({ description: 'Оновлення від виконавців' })
  @IsOptional()
  @IsBoolean()
  artistUpdates?: boolean;

  @ApiPropertyOptional({ description: 'Акції та оновлення платформи' })
  @IsOptional()
  @IsBoolean()
  platformUpdates?: boolean;

  @ApiPropertyOptional({ description: 'Системні повідомлення' })
  @IsOptional()
  @IsBoolean()
  systemNotifications?: boolean;

  @ApiPropertyOptional({ description: 'Push-сповіщення на пристрої' })
  @IsOptional()
  @IsBoolean()
  pushEnabled?: boolean;

  @ApiPropertyOptional({ description: 'Дублювати сповіщення на пошту' })
  @IsOptional()
  @IsBoolean()
  emailEnabled?: boolean;
}
