import { Global, Module } from '@nestjs/common';

import { CacheEventBus } from './cache.event-bus';
import { CacheInvalidationListener } from './cache-invalidation.listener';
import { CacheService } from './cache.service';

@Global()
@Module({
  providers: [CacheService, CacheEventBus, CacheInvalidationListener],
  exports: [CacheService, CacheEventBus],
})
export class CacheModule {}
