# Lumi mock mode

The app currently runs against `MockDataStore` by design, so all main screens work without the backend:

- Home: recommendations, new releases, genres, pull-to-refresh
- Search: tracks, artists and albums
- Library: playlists, liked tracks, albums and history
- Artist / Album / Playlist detail pages
- Shared mini player + full player controls
- Like/unlike, follow/unfollow, add-to-playlist, create playlist, report and mock download state
- Login, registration and profile editing

To switch back to the real backend later, set `MockDataStore.ENABLED` to `false` and wire any backend endpoints still marked TODO in the repositories.

Mock audio intentionally does not stream a remote file: player controls update the queue/state locally. This keeps the UI deterministic and usable offline while backend/audio hosting is unfinished.
