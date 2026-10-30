import { Transform } from 'class-transformer';
import {
  IsIn,
  IsOptional,
  IsString,
  Length,
  MaxLength,
} from 'class-validator';

// На що скаржаться: на сам трек або на коментар під ним.
export const REPORT_TARGETS = ['track', 'comment'] as const;

// Причини збігаються з Android ReportReason (ui/player/ReportRules.kt).
// explicit_content / wrong_metadata лишені для старого контекстного меню треку
// (data/model/Models.kt ReportReason), щоб не ламати вже відправлені запити.
export const REPORT_REASONS = [
  'copyright',
  'bad_quality',
  'spam',
  'offensive',
  'other',
  'explicit_content',
  'wrong_metadata',
] as const;

// Тіло POST /api/reports.
export class CreateReportDto {
  @IsOptional()
  @IsIn(REPORT_TARGETS)
  target: (typeof REPORT_TARGETS)[number] = 'track';

  // id коментаря, якщо target = 'comment'.
  @IsOptional()
  @Transform(({ value }) => (typeof value === 'string' ? value.trim() : value))
  @IsString()
  @Length(1, 64)
  targetId?: string;

  @Transform(({ value }) => (typeof value === 'string' ? value.trim() : value))
  @IsString()
  @Length(1, 64)
  trackId: string;

  @IsIn(REPORT_REASONS)
  reason: (typeof REPORT_REASONS)[number];

  // Необов'язкове пояснення (ліміт 300 як у ReportRules.MAX_COMMENT_LENGTH).
  @IsOptional()
  @Transform(({ value }) => (typeof value === 'string' ? value.trim() : value))
  @IsString()
  @MaxLength(300)
  comment?: string;
}
