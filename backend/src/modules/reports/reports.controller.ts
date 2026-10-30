import { Body, Controller, Post } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';

import { Auth } from '../auth/decorators/auth.decorator';
import { CurrentUser } from '../auth/decorators/current-user.decorator';
import { CreateReportDto } from './dto/create-report.dto';
import { ReportsService } from './reports.service';

// Скарги з плеєра. Лише з токеном: скарга завжди має автора.
@ApiTags('reports')
@Controller('reports')
export class ReportsController {
  constructor(private readonly reportsService: ReportsService) {}

  // POST /api/reports — поскаржитись на трек або коментар.
  @Auth()
  @Post()
  @ApiOperation({ summary: 'Report a track or a comment' })
  create(@CurrentUser('sub') userId: string, @Body() dto: CreateReportDto) {
    return this.reportsService.create(userId, dto);
  }
}
