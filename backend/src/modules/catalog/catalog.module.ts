import { Module } from '@nestjs/common';

import { CatalogCacheService } from './catalog-cache.service';
import { CatalogController } from './catalog.controller';
import { CatalogService } from './catalog.service';

@Module({
  controllers: [CatalogController],
  providers: [CatalogService, CatalogCacheService],
  exports: [CatalogCacheService],
})
export class CatalogModule {}
