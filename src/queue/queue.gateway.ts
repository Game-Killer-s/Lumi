import {
  ConnectedSocket,
  MessageBody,
  SubscribeMessage,
  WebSocketGateway,
  WebSocketServer,
} from '@nestjs/websockets';
import { Server, Socket } from 'socket.io';
import { QueueService } from './queue.service';

@WebSocketGateway({ cors: true, namespace: '/queue' })
export class QueueGateway {
  @WebSocketServer()
  server!: Server;

  constructor(private readonly queueService: QueueService) {}

  @SubscribeMessage('queue:get')
  getQueue(@ConnectedSocket() client: Socket) {
    client.emit('queue:updated', this.queueService.getQueue());
  }

  @SubscribeMessage('queue:current')
  changeCurrent(
    @MessageBody() body: { trackId: string },
  ) {
    const queue = this.queueService.setCurrentTrack(body.trackId);
    this.server.emit('queue:updated', queue);
    return queue;
  }
}
