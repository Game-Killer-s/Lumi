import {
  extensionForMedia,
  matchesMediaSignature,
  mediaPublicUrl,
} from './media.constants';

describe('media format validation', () => {
  describe('extensionForMedia', () => {
    it('accepts FLAC and WAV for audio', () => {
      expect(extensionForMedia('AUDIO', 'track.flac', 'audio/flac')).toBe(
        '.flac',
      );
      expect(extensionForMedia('AUDIO', 'track.wav', 'audio/wav')).toBe('.wav');
      expect(extensionForMedia('AUDIO', 'track.wav', 'audio/x-wav')).toBe(
        '.wav',
      );
    });

    it('accepts JPG and PNG for covers', () => {
      expect(extensionForMedia('COVER', 'cover.jpg', 'image/jpeg')).toBe(
        '.jpg',
      );
      expect(extensionForMedia('COVER', 'cover.jpeg', 'image/jpeg')).toBe(
        '.jpg',
      );
      expect(extensionForMedia('COVER', 'cover.png', 'image/png')).toBe('.png');
    });

    it('rejects audio formats that are not FLAC/WAV', () => {
      expect(extensionForMedia('AUDIO', 'track.mp3', 'audio/mpeg')).toBeNull();
      expect(extensionForMedia('AUDIO', 'track.aac', 'audio/aac')).toBeNull();
      // mime аудіо, але розширення обкладинки
      expect(extensionForMedia('AUDIO', 'track.png', 'audio/flac')).toBeNull();
    });

    it('rejects covers in a format other than JPG/PNG', () => {
      expect(extensionForMedia('COVER', 'cover.gif', 'image/gif')).toBeNull();
      expect(extensionForMedia('COVER', 'cover.webp', 'image/webp')).toBeNull();
      // підміна: png-розширення, але аудіо-mime
      expect(extensionForMedia('COVER', 'cover.png', 'audio/wav')).toBeNull();
    });

    it('rejects the wrong extension for the chosen kind', () => {
      expect(extensionForMedia('COVER', 'track.flac', 'image/jpeg')).toBeNull();
      expect(extensionForMedia('AUDIO', 'cover.jpg', 'audio/flac')).toBeNull();
    });
  });

  describe('matchesMediaSignature', () => {
    it('recognizes a real FLAC header', () => {
      const head = Buffer.concat([Buffer.from('fLaC'), Buffer.alloc(60)]);

      expect(matchesMediaSignature('AUDIO', head)).toBe(true);
    });

    it('recognizes a real WAV header', () => {
      const head = Buffer.alloc(64);
      head.write('RIFF', 0, 'latin1');
      head.write('WAVE', 8, 'latin1');

      expect(matchesMediaSignature('AUDIO', head)).toBe(true);
    });

    it('rejects a file that only pretends to be audio', () => {
      const head = Buffer.concat([Buffer.from('ID3'), Buffer.alloc(61, 0x01)]);

      expect(matchesMediaSignature('AUDIO', head)).toBe(false);
    });

    it('recognizes JPEG and PNG covers', () => {
      const jpeg = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0])]);
      const png = Buffer.from([
        0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00,
      ]);

      expect(matchesMediaSignature('COVER', jpeg)).toBe(true);
      expect(matchesMediaSignature('COVER', png)).toBe(true);
    });

    it('rejects a cover with an unknown signature', () => {
      expect(matchesMediaSignature('COVER', Buffer.from('GIF89a'))).toBe(false);
    });

    it('does not mix up audio and cover signatures', () => {
      const flac = Buffer.concat([Buffer.from('fLaC'), Buffer.alloc(20)]);
      const png = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

      expect(matchesMediaSignature('COVER', flac)).toBe(false);
      expect(matchesMediaSignature('AUDIO', png)).toBe(false);
    });
  });

  describe('mediaPublicUrl', () => {
    it('builds an url without leaking the user file name', () => {
      expect(mediaPublicUrl('audio/2026/10/abc.flac')).toBe(
        '/media/audio/2026/10/abc.flac',
      );
    });
  });
});
