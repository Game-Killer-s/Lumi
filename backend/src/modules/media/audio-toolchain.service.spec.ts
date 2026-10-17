import { parseProbeOutput } from './audio-toolchain.service';

// Скорочений, але реальний за формою вивід ffprobe для FLAC.
const FLAC_OUTPUT = JSON.stringify({
  streams: [
    {
      codec_name: 'flac',
      codec_type: 'audio',
      sample_rate: '44100',
      channels: 2,
      duration: '214.360000',
      tags: { title: 'Midnight Drive' },
    },
  ],
  format: {
    duration: '214.360000',
    bit_rate: '1014000',
    tags: { artist: 'Lumi' },
  },
});

// WAV тегів не має - у відповіді їх просто немає.
const WAV_OUTPUT = JSON.stringify({
  streams: [
    {
      codec_name: 'pcm_s16le',
      codec_type: 'audio',
      sample_rate: '48000',
      channels: 1,
      duration: '12.500000',
    },
  ],
  format: { duration: '12.500000', bit_rate: '768000' },
});

describe('parseProbeOutput', () => {
  it('parses a FLAC ffprobe payload with tags', () => {
    const probe = parseProbeOutput(FLAC_OUTPUT);

    expect(probe.streams).toHaveLength(1);
    expect(probe.streams[0].codec_name).toBe('flac');
    expect(probe.streams[0].sample_rate).toBe('44100');
    expect(probe.streams[0].channels).toBe(2);
    expect(probe.format?.duration).toBe('214.360000');
    expect(probe.format?.tags).toEqual({ artist: 'Lumi' });
  });

  it('parses a WAV payload without tags', () => {
    const probe = parseProbeOutput(WAV_OUTPUT);

    expect(probe.streams[0].codec_name).toBe('pcm_s16le');
    expect(probe.format?.tags).toBeUndefined();
    expect(probe.format?.bit_rate).toBe('768000');
  });

  it('returns an empty stream list when ffprobe found no streams', () => {
    expect(parseProbeOutput('{"streams":[]}').streams).toEqual([]);
  });

  it('rejects output that is not valid JSON', () => {
    expect(() => parseProbeOutput('ffprobe: not a json')).toThrow(
      /not valid JSON/,
    );
    expect(() => parseProbeOutput('')).toThrow(/not valid JSON/);
  });

  it('rejects output that is not an object', () => {
    expect(() => parseProbeOutput('null')).toThrow(/empty/);
    expect(() => parseProbeOutput('"text"')).toThrow(/empty/);
  });
});
