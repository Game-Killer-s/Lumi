import { ConfigService } from '@nestjs/config';
import { promises as fs } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { EICAR_MARKER } from './media.constants';
import { MediaSecurityService } from './media-security.service';
import { MediaStorageService } from './media-storage.service';

describe('MediaSecurityService', () => {
  let service: MediaSecurityService;
  let dir: string;

  beforeAll(async () => {
    dir = join(
      tmpdir(),
      `lumi-scan-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    );
    await fs.mkdir(dir, { recursive: true });
  });

  afterAll(async () => {
    await fs.rm(dir, { recursive: true, force: true });
  });

  beforeEach(() => {
    const configService = {
      get: jest.fn().mockReturnValue(join(dir, 'storage')),
    };

    service = new MediaSecurityService(
      new MediaStorageService(configService as unknown as ConfigService),
    );
  });

  // Кладемо байти у тимчасовий файл - так само це робить multer.
  async function writeFile(name: string, content: Buffer): Promise<string> {
    const filePath = join(dir, name);

    await fs.writeFile(filePath, content);

    return filePath;
  }

  describe('clean files', () => {
    it('accepts a real FLAC file', async () => {
      const filePath = await writeFile(
        'ok.flac',
        Buffer.concat([Buffer.from('fLaC'), Buffer.alloc(600, 3)]),
      );

      await expect(service.scan(filePath, 'AUDIO')).resolves.toEqual({
        clean: true,
      });
    });

    it('accepts a real WAV file', async () => {
      const content = Buffer.alloc(600);
      content.write('RIFF', 0, 'latin1');
      content.write('WAVE', 8, 'latin1');

      const filePath = await writeFile('ok.wav', content);

      await expect(service.scan(filePath, 'AUDIO')).resolves.toEqual({
        clean: true,
      });
    });

    it('accepts a real PNG cover', async () => {
      const filePath = await writeFile(
        'ok.png',
        Buffer.concat([
          Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
          Buffer.alloc(600, 9),
        ]),
      );

      await expect(service.scan(filePath, 'COVER')).resolves.toEqual({
        clean: true,
      });
    });
  });

  describe('antivirus pre-check', () => {
    it('rejects a file with the EICAR signature', async () => {
      const filePath = await writeFile(
        'infected.flac',
        Buffer.concat([
          Buffer.from('fLaC'),
          Buffer.from(EICAR_MARKER),
          Buffer.alloc(600, 1),
        ]),
      );

      const result = await service.scan(filePath, 'AUDIO');

      expect(result.clean).toBe(false);
      expect(result.reason).toContain('Antivirus signature detected');
    });

    it('rejects an empty file', async () => {
      const filePath = await writeFile('empty.flac', Buffer.alloc(0));

      const result = await service.scan(filePath, 'AUDIO');

      expect(result.clean).toBe(false);
      expect(result.reason).toBe('File is empty');
    });
  });

  describe('real content type', () => {
    it('rejects audio that is actually another format', async () => {
      const filePath = await writeFile(
        'fake.flac',
        Buffer.concat([Buffer.from('ID3'), Buffer.alloc(600, 2)]),
      );

      const result = await service.scan(filePath, 'AUDIO');

      expect(result.clean).toBe(false);
      expect(result.reason).toContain('does not match audio format');
    });

    it('rejects a cover that is actually audio', async () => {
      const filePath = await writeFile(
        'cover.png',
        Buffer.concat([Buffer.from('fLaC'), Buffer.alloc(600, 4)]),
      );

      const result = await service.scan(filePath, 'COVER');

      expect(result.clean).toBe(false);
      expect(result.reason).toContain('does not match cover format');
    });
  });
});
