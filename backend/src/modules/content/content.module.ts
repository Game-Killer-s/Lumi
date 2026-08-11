import { Module } from '@nestjs/common';

import { CatalogModule } from '../catalog/catalog.module';
import { AdminTracksController } from './admin-tracks.controller';
import { AdminTracksService } from './admin-tracks.service';
import { ContentLifecycleController } from './content-lifecycle.controller';
import { ContentLifecycleService } from './content-lifecycle.service';

@Module({
  imports: [CatalogModule],
  controllers: [ContentLifecycleController, AdminTracksController],
  providers: [ContentLifecycleService, AdminTracksService],
})
export class ContentModule {}
