import { Injectable } from '@nestjs/common';
import { EventEmitter } from 'node:events';

import { RELEASE_EVENTS, ReleasePublishedEvent } from './release.event';

// Шина подій релізів: контент-модуль кидає подію, підписники її ловлять.
@Injectable()
export class ReleaseEventBus extends EventEmitter {
  emitReleasePublished(payload: ReleasePublishedEvent): void {
    this.emit(RELEASE_EVENTS.published, payload);
  }

  onReleasePublished(handler: (payload: ReleasePublishedEvent) => void): void {
    this.on(RELEASE_EVENTS.published, handler);
  }
}
