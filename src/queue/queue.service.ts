import { Injectable, NotFoundException } from '@nestjs/common';
import { QueueTrack, PlaybackQueue } from './queue.types';

@Injectable()
export class QueueService {
  private readonly queue: PlaybackQueue = {
    tracks: [
      { id: 'track-1', title: 'First Track', artist: 'Lumi Artist' },
      { id: 'track-2', title: 'Second Track', artist: 'Lumi Artist' },
      { id: 'track-3', title: 'Third Track', artist: 'Lumi Artist' },
    ],
    currentTrackId: 'track-1',
    total: 3,
    updatedAt: new Date().toISOString(),
  };

  getQueue(): PlaybackQueue {
    return {
      ...this.queue,
      tracks: [...this.queue.tracks],
    };
  }

  setCurrentTrack(trackId: string): PlaybackQueue {
    const exists = this.queue.tracks.some((track) => track.id === trackId);

    if (!exists) {
      throw new NotFoundException('Track is not in the queue');
    }

    this.queue.currentTrackId = trackId;
    this.queue.updatedAt = new Date().toISOString();

    return this.getQueue();
  }

  addTrack(track: QueueTrack): PlaybackQueue {
    this.queue.tracks.push(track);
    this.queue.total = this.queue.tracks.length;
    this.queue.updatedAt = new Date().toISOString();
    return this.getQueue();
  }

  removeTrack(trackId: string): PlaybackQueue {
    this.queue.tracks = this.queue.tracks.filter((track) => track.id !== trackId);
    this.queue.total = this.queue.tracks.length;

    if (this.queue.currentTrackId === trackId) {
      this.queue.currentTrackId = this.queue.tracks[0]?.id ?? null;
    }

    this.queue.updatedAt = new Date().toISOString();
    return this.getQueue();
  }
}
