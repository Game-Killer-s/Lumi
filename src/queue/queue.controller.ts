import { Body, Controller, Get, Param, Post } from '@nestjs/common';
import { QueueService } from './queue.service';
import { QueueTrack } from './queue.types';

@Controller('queue')
export class QueueController {
  constructor(private readonly queueService: QueueService) {}

  @Get()
  getQueue() {
    return this.queueService.getQueue();
  }

  @Post('current/:trackId')
  setCurrent(@Param('trackId') trackId: string) {
    return this.queueService.setCurrentTrack(trackId);
  }

  @Post('tracks')
  addTrack(@Body() track: QueueTrack) {
    return this.queueService.addTrack(track);
  }

  @Post('tracks/:trackId/remove')
  removeTrack(@Param('trackId') trackId: string) {
    return this.queueService.removeTrack(trackId);
  }
}
