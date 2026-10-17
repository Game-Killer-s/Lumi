import { AudioProbeResult } from './audio-toolchain.service';
import {
  assetMetadataFields,
  CORRUPTED_AUDIO_REASON,
  MediaMetadataError,
  MediaMetadataService,
  MAX_CHANNELS,
  PROBE_TOOL_MISSING_REASON,
  toSafeFailureReason,
} from './media-metadata.service';
import { MediaStorageService } from './media-storage.service';

// Реальний за формою вивід ffprobe для FLAC: аудіо-потік + embedded tags.
const FLAC_PROBE: AudioProbeResult = {
  streams: [
    {
      codec_type: 'audio',
      codec_name: 'flac',
      sample_rate: '44100',
      channels: 2,
      bit_rate: '1014000',
      duration: '214.360000',
      tags: { title: 'Midnight Drive', ARTIST: 'Lumi' },
    },
  ],
  format: {
    duration: '214.360000',
    bit_rate: '1014000',
    tags: {
      title: 'Midnight Drive',
      artist: 'Lumi',
      album: 'Night Tapes',
      genre: 'Synthwave',
      date: '2026',
      TRACKNUMBER: '3',
      // Такого поля у картці треку не потрібно - воно не має потрапити в базу.
      encoder: 'reference libFLAC 1.4.2',
    },
  },
};

// WAV (PCM 16 bit): тегів немає взагалі.
const WAV_PROBE: AudioProbeResult = {
  streams: [
    {
      codec_type: 'audio',
      codec_name: 'pcm_s16le',
      sample_rate: '48000',
      channels: 1,
      bit_rate: '768000',
      duration: '12.500000',
    },
  ],
  format: { duration: '12.500000', bit_rate: '768000' },
};

const STORAGE_KEY = 'audio/2026/10/source.flac';

describe('MediaMetadataService', () => {
  let service: MediaMetadataService;
  let toolchain: { probe: jest.Mock };
  let storage: { absolutePath: jest.Mock };

  beforeEach(() => {
    toolchain = { probe: jest.fn().mockResolvedValue(FLAC_PROBE) };
    storage = {
      absolutePath: jest
        .fn()
        .mockImplementation((key: string) => `/storage/${key}`),
    };

    service = new MediaMetadataService(
      toolchain as never,
      storage as unknown as MediaStorageService,
    );
  });

  describe('FLAC metadata', () => {
    it('extracts duration, codec, sample rate, channels and bitrate', () => {
      expect(service.validateProbe(FLAC_PROBE)).toEqual({
        durationSeconds: 214.36,
        codec: 'flac',
        sampleRateHz: 44100,
        channels: 2,
        bitrateKbps: 1014,
        tags: {
          title: 'Midnight Drive',
          artist: 'Lumi',
          album: 'Night Tapes',
          genre: 'Synthwave',
          date: '2026',
          tracknumber: '3',
        },
      });
    });

    it('probes the file inside the storage directory and returns metadata', async () => {
      const inspection = await service.inspect(STORAGE_KEY);

      expect(toolchain.probe).toHaveBeenCalledWith(`/storage/${STORAGE_KEY}`);
      expect(inspection.ok).toBe(true);

      if (inspection.ok) {
        expect(inspection.metadata.durationSeconds).toBe(214.36);
      }
    });
  });

  describe('WAV metadata', () => {
    it('reads PCM metadata and leaves tags empty when the file has none', () => {
      expect(service.validateProbe(WAV_PROBE)).toEqual({
        durationSeconds: 12.5,
        codec: 'pcm_s16le',
        sampleRateHz: 48000,
        channels: 1,
        bitrateKbps: 768,
        tags: null,
      });
    });

    it('falls back to the stream duration when the container has none', () => {
      const result = service.validateProbe({
        streams: [{ ...WAV_PROBE.streams[0], duration: '12.500000' }],
        format: {},
      });

      expect(result.durationSeconds).toBe(12.5);
    });

    it('accepts a full WAV stream description from the real ffprobe output', () => {
      const result = service.validateProbe({
        streams: [
          {
            codec_type: 'audio',
            codec_name: 'pcm_s24le',
            sample_rate: '96000',
            channels: 6,
            bit_rate: '13824000',
            duration: '187.970000',
          },
        ],
        format: { duration: '187.970000', bit_rate: '13824000' },
      });

      expect(result).toEqual({
        durationSeconds: 187.97,
        codec: 'pcm_s24le',
        sampleRateHz: 96000,
        channels: 6,
        bitrateKbps: 13824,
        tags: null,
      });
    });
  });
  describe('invalid and corrupted audio', () => {
    it('rejects a file without an audio stream', () => {
      expect(() => service.validateProbe({ streams: [] })).toThrow(
        MediaMetadataError,
      );

      try {
        service.validateProbe({ streams: [] });
      } catch (error) {
        expect((error as MediaMetadataError).code).toBe('NO_AUDIO_STREAM');
        expect((error as MediaMetadataError).safeReason).toContain(
          'no readable audio stream',
        );
      }
    });

    it('rejects a zero duration', () => {
      expect(() =>
        service.validateProbe({
          streams: [{ ...FLAC_PROBE.streams[0], duration: '0.000000' }],
          format: { duration: '0.000000' },
        }),
      ).toThrow(/no readable duration/);
    });

    it('rejects a sample rate that ffprobe could not read', () => {
      expect(() =>
        service.validateProbe({
          streams: [{ ...FLAC_PROBE.streams[0], sample_rate: 'N/A' }],
          format: {},
        }),
      ).toThrow(/Unsupported sample rate/);
    });

    it('rejects a sample rate outside the supported range', () => {
      expect(() =>
        service.validateProbe({
          streams: [{ ...FLAC_PROBE.streams[0], sample_rate: '4000' }],
          format: {},
        }),
      ).toThrow(/Unsupported sample rate/);
    });

    it('rejects an impossible channel count', () => {
      expect(() =>
        service.validateProbe({
          streams: [{ ...FLAC_PROBE.streams[0], channels: MAX_CHANNELS + 1 }],
          format: {},
        }),
      ).toThrow(/Unsupported channel count/);
    });

    it('rejects a codec that we do not accept', () => {
      expect(() =>
        service.validateProbe({
          streams: [{ ...FLAC_PROBE.streams[0], codec_name: 'mp3' }],
          format: {},
        }),
      ).toThrow(/Unsupported audio codec: mp3/);
    });

    it('turns a failed probe into a failed inspection with a safe reason', async () => {
      toolchain.probe.mockRejectedValue(
        new Error(
          'Audio probe failed: Command failed: ffprobe -v error /storage/audio/2026/10/broken.flac',
        ),
      );

      const inspection = await service.inspect(STORAGE_KEY);

      expect(inspection).toEqual({ ok: false, reason: CORRUPTED_AUDIO_REASON });
      expect(inspection.ok).toBe(false);
    });

    it('reports a missing probe tool as a server problem', async () => {
      toolchain.probe.mockRejectedValue(new Error('spawn ffprobe ENOENT'));

      await expect(service.inspect(STORAGE_KEY)).resolves.toEqual({
        ok: false,
        reason: PROBE_TOOL_MISSING_REASON,
      });
    });

    it('reports unparsable probe output as a corrupted file', async () => {
      toolchain.probe.mockRejectedValue(
        new Error('ffprobe output is not valid JSON'),
      );

      await expect(service.inspect(STORAGE_KEY)).resolves.toEqual({
        ok: false,
        reason: CORRUPTED_AUDIO_REASON,
      });
    });

    it('never leaks server paths or tool details in the reason', async () => {
      toolchain.probe.mockRejectedValue(
        new Error(
          'Audio probe failed: Command failed: ffprobe /storage/audio/2026/10/broken.flac',
        ),
      );

      const inspection = await service.inspect(STORAGE_KEY);

      expect(inspection.ok).toBe(false);

      if (!inspection.ok) {
        expect(inspection.reason).not.toContain('/');
        expect(inspection.reason).not.toContain('\\');
        expect(inspection.reason).not.toContain('ffprobe');
      }
    });
  });

  describe('safe failure reason', () => {
    it('returns the probe reason for validation errors', () => {
      const error = new MediaMetadataError(
        'INVALID_DURATION',
        'Audio file has no readable duration: it may be corrupted',
      );

      expect(toSafeFailureReason(error)).toBe(
        'Audio file has no readable duration: it may be corrupted',
      );
    });

    it('keeps the toolchain message but strips file paths', () => {
      const reason = toSafeFailureReason(
        new Error(
          'Media processing failed: cannot write C:\\storage\\audio\\out.flac',
        ),
      );

      expect(reason).toBe('Processing failed: cannot write <file>');
    });

    it('keeps a plain ffmpeg error readable for the content manager', () => {
      const reason = toSafeFailureReason(new Error('ffmpeg is not installed'));

      expect(reason).toContain('ffmpeg is not installed');
    });
  });

  describe('asset fields', () => {
    it('maps extracted metadata onto the media record', () => {
      expect(
        assetMetadataFields({
          ok: true,
          metadata: {
            durationSeconds: 214.36,
            codec: 'flac',
            sampleRateHz: 44100,
            channels: 2,
            bitrateKbps: 1014,
            tags: { title: 'Midnight Drive' },
          },
        }),
      ).toEqual({
        durationSeconds: 214.36,
        codec: 'flac',
        sampleRateHz: 44100,
        channels: 2,
        bitrateKbps: 1014,
        tags: { title: 'Midnight Drive' },
        metadataStatus: 'EXTRACTED',
        metadataError: null,
      });
    });

    it('marks the media record as FAILED for a corrupted file', () => {
      expect(
        assetMetadataFields({ ok: false, reason: CORRUPTED_AUDIO_REASON }),
      ).toEqual({
        metadataStatus: 'FAILED',
        metadataError: CORRUPTED_AUDIO_REASON,
      });
    });
  });
});
