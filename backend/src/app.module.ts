import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import configuration from './config/configuration';
import { validationSchema } from './config/validation.schema';
import { HealthModule } from './modules/health/health.module';
import { AuthModule } from './modules/auth/auth.module';
import { CacheModule } from './modules/cache/cache.module';
import { CatalogModule } from './modules/catalog/catalog.module';
import { CommentsModule } from './modules/comments/comments.module';
import { ContentModule } from './modules/content/content.module';
import { LikesModule } from './modules/likes/likes.module';
import { MediaModule } from './modules/media/media.module';
import { MetricsModule } from './modules/metrics/metrics.module';
import { NotificationsModule } from './modules/notifications/notifications.module';
import { ProfileModule } from './modules/profile/profile.module';
import { ReportsModule } from './modules/reports/reports.module';
import { SubscriptionsModule } from './modules/subscriptions/subscriptions.module';
import { PrismaModule } from './prisma/prisma.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      load: [configuration],
      validationSchema,
      envFilePath: [`.env.${process.env.NODE_ENV || 'development'}`, '.env'],
    }),
    PrismaModule,
    CacheModule,
    MetricsModule,
    CatalogModule,
    HealthModule,
    AuthModule,
    ContentModule,
    MediaModule,
    NotificationsModule,
    ProfileModule,
    SubscriptionsModule,
    LikesModule,
    CommentsModule,
    ReportsModule,
  ],
})
export class AppModule {}
