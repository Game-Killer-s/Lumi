import { Controller, Get } from '@nestjs/common';
import { ApiTags } from '@nestjs/swagger';

import { Public } from '../auth/decorators/public.decorator';
import { CacheService } from '../cache/cache.service';
import { MetricsService } from './metrics.service';

@ApiTags('metrics')
@Controller('metrics')
export class MetricsController {
  constructor(
    private readonly metricsService: MetricsService,
    private readonly cacheService: CacheService,
  ) {}

  @Public()
  @Get()
  getMetrics() {
    return this.metricsService.snapshot(this.cacheService.getStats());
  }
}
