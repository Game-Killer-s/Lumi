import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Transform, Type } from 'class-transformer';
import { IsInt, IsOptional, IsString, Length, Max, Min } from 'class-validator';

function trimValue(value: unknown): unknown {
  return typeof value === 'string' ? value.trim() : value;
}

export class SearchProfilesQueryDto {
  @ApiProperty({ example: 'vasyl', minLength: 1, maxLength: 50 })
  @Transform(({ value }) => trimValue(value))
  @IsString()
  @Length(1, 50)
  q!: string;

  @ApiPropertyOptional({ example: 1, default: 1 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  page: number = 1;

  @ApiPropertyOptional({ example: 20, default: 20, maximum: 50 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(50)
  limit: number = 20;
}
