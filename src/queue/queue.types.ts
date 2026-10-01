export interface QueueTrack {
  id: string;
  title: string;
  artist: string;
}

export interface PlaybackQueue {
  tracks: QueueTrack[];
  currentTrackId: string | null;
  total: number;
  updatedAt: string;
}
