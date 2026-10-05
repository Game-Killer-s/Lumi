export const RELEASE_EVENTS = {
  published: 'release.published',
} as const;

export interface ReleasePublishedEvent {
  releaseId: string;
}

export type ReleaseNotificationPayload = {
  releaseId: string;
  title: string;
  coverUrl: string | null;
  artistId: string;
  artistName: string;
  albumId: string | null;
  albumTitle: string | null;
};
