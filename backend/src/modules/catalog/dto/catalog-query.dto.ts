import { Type } from 'class-transformer';
import { IsIn, IsInt, IsOptional, IsString, Max, Min } from 'class-validator';

// Параметри для GET /api/catalog
export class CatalogQueryDto {
  @IsOptional()
  @IsString()
  genreId?: string;

  @IsOptional()
  @IsIn(['popular', 'new', 'title'])
  sort: 'popular' | 'new' | 'title' = 'popular';

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  page: number = 1;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(50)
  limit: number = 20;

  // '?cache=false' — обійти кеш (потрібно для load test,
  // щоб порівняти кешований і некешований запит).
  @IsOptional()
  @IsIn(['true', 'false'])
  cache: 'true' | 'false' = 'true';
}

// Параметри для стрічок: популярне, нові релізи, discovery.
export class FeedQueryDto {
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(50)
  limit: number = 10;

  @IsOptional()
  @IsIn(['true', 'false'])
  cache: 'true' | 'false' = 'true';
}
