import { ConfigService } from '@nestjs/config';
import { createHash } from 'node:crypto';
import { promises as fs } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { MediaStorageService } from './media-storage.service';

describe('MediaStorageService', () => {
  let service: MediaStorageService;
  let uploadDir: string;

  beforeEach(() => {
    uploadDir = join(
      tmpdir(),
      `lumi-storage-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    );

    const configService = {
      get: jest.fn().mockReturnValue(uploadDir),
    };

    service = new MediaStorageService(
      configService as unknown as ConfigService,
    );
  });

  afterEach(async () => {
    await fs.rm(uploadDir, { recursive: true, force: true });
  });

  describe('storage keys', () => {
    it('does not use the original file name in the key', () => {
      const key = service.buildStorageKey('AUDIO', '.flac');

      // kind/рік/місяць/uuid.ext - жодного поля від користувача
      expect(key.split('/')).toHaveLength(4);
      expect(key.startsWith('audio/')).toBe(true);
      expect(key.endsWith('.flac')).toBe(true);
      expect(key).not.toContain(' ');
      expect(key).not.toContain('..');
    });

    it('generates a unique key for every upload', () => {
      const first = service.buildStorageKey('AUDIO', '.wav');
      const second = service.buildStorageKey('AUDIO', '.wav');

      expect(first).not.toBe(second);
    });

    it('keeps covers in their own folder', () => {
      expect(
        service.buildStorageKey('COVER', '.png').startsWith('cover/'),
      ).toBe(true);
    });

    it('resolves the key inside the upload dir', () => {
      expect(service.absolutePath('audio/2026/10/file.flac')).toBe(
        join(uploadDir, 'audio', '2026', '10', 'file.flac'),
      );
    });
  });

  describe('file operations', () => {
    it('moves the file from temp storage and reads only its head', async () => {
      const tempPath = join(service.tempDir, `upload-${Date.now()}-head.tmp`);

      await fs.writeFile(
        tempPath,
        Buffer.concat([Buffer.from('fLaC'), Buffer.alloc(2048, 7)]),
      );

      const key = service.buildStorageKey('AUDIO', '.flac');
      await service.moveIntoStorage(tempPath, key);

      const head = await service.readHead(service.absolutePath(key), 16);

      expect(head).toHaveLength(16);
      expect(head.subarray(0, 4).toString('latin1')).toBe('fLaC');

      // тимчасового файлу більше немає
      await expect(fs.stat(tempPath)).rejects.toThrow();
    });

    it('computes sha256 checksum of the stored file', async () => {
      const tempPath = join(service.tempDir, `upload-${Date.now()}-sum.tmp`);
      await fs.writeFile(tempPath, Buffer.from('lumi'));

      const key = service.buildStorageKey('COVER', '.jpg');
      await service.moveIntoStorage(tempPath, key);

      expect(await service.checksum(key)).toBe(
        createHash('sha256').update('lumi').digest('hex'),
      );
    });

    it('reports size of the stored file', async () => {
      const tempPath = join(service.tempDir, `upload-${Date.now()}-size.tmp`);
      await fs.writeFile(tempPath, Buffer.alloc(1234));

      const key = service.buildStorageKey('AUDIO', '.wav');
      await service.moveIntoStorage(tempPath, key);

      expect(await service.statSize(key)).toBe(1234);
    });

    it('returns null size when the file is missing', async () => {
      expect(await service.statSize('audio/2026/10/missing.flac')).toBeNull();
    });

    it('removes files without throwing when they are already gone', async () => {
      await expect(
        service.remove('audio/2026/10/missing.flac'),
      ).resolves.toBeUndefined();

      await expect(
        service.removeTemp(join(service.tempDir, 'missing-temp-file')),
      ).resolves.toBeUndefined();
    });
  });
});
