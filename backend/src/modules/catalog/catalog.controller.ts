import { Controller, Get, Param, Query } from '@nestjs/common';
import { ApiTags } from '@nestjs/swagger';

import { Public } from '../auth/decorators/public.decorator';
import { CatalogService } from './catalog.service';

/**
 * Публічні endpoints каталогу для слухачів.
 * Повертають тільки опубліковані треки.
 */
@ApiTags('catalog')
@Controller('tracks')
export class CatalogController {
  constructor(private readonly catalogService: CatalogService) {}

  @Public()
  @Get()
  findAll() {
    return this.catalogService.findAll();
  }

  @Public()
  @Get('search')
  search(@Query('q') query: string) {
    return this.catalogService.search(query);
  }

  @Public()
  @Get('stream/:id')
  stream(@Param('id') id: string) {
    return this.catalogService.getStream(id);
  }

  @Public()
  @Get(':id')
  findOne(@Param('id') id: string) {
    return this.catalogService.findOne(id);
  }
}
