import { Global, MiddlewareConsumer, Module, NestModule } from '@nestjs/common';

import { MetricsController } from './metrics.controller';
import { MetricsService } from './metrics.service';
import { RequestMetricsMiddleware } from './request-metrics.middleware';

@Global()
@Module({
  controllers: [MetricsController],
  providers: [MetricsService, RequestMetricsMiddleware],
  exports: [MetricsService],
})
export class MetricsModule implements NestModule {
  configure(consumer: MiddlewareConsumer): void {
    consumer.apply(RequestMetricsMiddleware).forRoutes('*');
  }
}
