import { Controller, Get, Param, Query } from '@nestjs/common';
import { ApiTags } from '@nestjs/swagger';

import { Public } from '../auth/decorators/public.decorator';
import { CatalogService } from './catalog.service';
import { CatalogQueryDto, FeedQueryDto } from './dto/catalog-query.dto';

// Публічні endpoints каталогу і discovery стрічок.
// Вони @Public, бо це спільні дані (не персональні) —
// такі запити можна кешувати в shared cache.
@ApiTags('catalog')
@Controller()
export class CatalogController {
  constructor(private readonly catalogService: CatalogService) {}

  // GET /api/catalog — каталог треків
  @Public()
  @Get('catalog')
  getCatalog(@Query() query: CatalogQueryDto) {
    return this.catalogService.getCatalog(query, query.cache !== 'false');
  }

  // GET /api/catalog/popular — популярні треки
  @Public()
  @Get('catalog/popular')
  getPopular(@Query() query: FeedQueryDto) {
    return this.catalogService.getPopular(query.limit, query.cache !== 'false');
  }

  // GET /api/catalog/new-releases — нові релізи
  @Public()
  @Get('catalog/new-releases')
  getNewReleases(@Query() query: FeedQueryDto) {
    return this.catalogService.getNewReleases(
      query.limit,
      query.cache !== 'false',
    );
  }

  // GET /api/discovery — головна стрічка
  @Public()
  @Get('discovery')
  getDiscovery(@Query() query: FeedQueryDto) {
    return this.catalogService.getDiscoveryFeed(query.cache !== 'false');
  }

  // ============================================================
  // Публічні /tracks endpoints (прийшли з main, PR #5 content-admin).
  // Android-клієнт звертається саме до них (ApiConstants: tracks,
  // tracks/search). Слухач бачить лише опубліковані треки.
  // ============================================================

  // GET /api/tracks/search?q=... — пошук (оголошуємо до :id,
  // інакше 'search' зматчиться як id)
  @Public()
  @Get('tracks/search')
  search(@Query('q') query: string) {
    return this.catalogService.search(query);
  }

  // GET /api/tracks/stream/:id — посилання на аудіо
  @Public()
  @Get('tracks/stream/:id')
  stream(@Param('id') id: string) {
    return this.catalogService.getStream(id);
  }

  // GET /api/tracks/popular — стрічка «Популярно зараз» для мобільного
  // клієнта (Android: ApiConstants.TRACKS_POPULAR). Віддає
  // TrackListResponse { tracks, total, page, limit }.
  @Public()
  @Get('tracks/popular')
  getPopularTracks(@Query() query: FeedQueryDto) {
    return this.catalogService.getPopularFeed(query.limit);
  }

  // GET /api/tracks/new — стрічка «Свіжі релізи» (Android: TRACKS_NEW).
  @Public()
  @Get('tracks/new')
  getNewTracks(@Query() query: FeedQueryDto) {
    return this.catalogService.getNewFeed(query.limit);
  }

  // ============================================================
  // Публічні /albums endpoints для Android-бібліотеки.
  // AlbumDetailScreen і вкладка «Альбоми» читають саме цей контракт:
  // AlbumListResponse { albums, total, page, limit } та Album.
  // ============================================================

  @Public()
  @Get('albums')
  getAlbums(@Query() query: FeedQueryDto) {
    return this.catalogService.getAlbums(query.limit);
  }

  @Public()
  @Get('albums/:id')
  getAlbum(@Param('id') id: string) {
    return this.catalogService.findAlbum(id);
  }

  // GET /api/tracks — усі опубліковані треки
  @Public()
  @Get('tracks')
  findAll() {
    return this.catalogService.findAll();
  }

  // GET /api/tracks/:id/lyrics — текст пісні (Android: LyricsApi).
  // Оголошуємо до 'tracks/:id'. Відсутній текст — це lyrics: null,
  // а не помилка: клієнт показує заглушку «текст ще не додали».
  @Public()
  @Get('tracks/:id/lyrics')
  getLyrics(@Param('id') id: string) {
    return this.catalogService.getLyrics(id);
  }

  // GET /api/tracks/:id — один опублікований трек
  @Public()
  @Get('tracks/:id')
  findOne(@Param('id') id: string) {
    return this.catalogService.findOne(id);
  }
}
