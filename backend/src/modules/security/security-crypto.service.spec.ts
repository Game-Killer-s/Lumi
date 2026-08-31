import { ConfigService } from '@nestjs/config';

import { SecurityCryptoService } from './security-crypto.service';

describe('SecurityCryptoService', () => {
  let service: SecurityCryptoService;

  beforeEach(() => {
    /*
     * 32 bytes для AES-256.
     *
     * Це ТІЛЬКИ test key.
     * Production secret тут
     * ніколи не використовуємо.
     */
    const encryptionKey = Buffer.alloc(32, 7).toString('base64');

    const config = new ConfigService({
      security: {
        twoFactorEncryptionKey: encryptionKey,

        hmacKey: 'unit-test-hmac-key-that-is-long-enough-for-tests',
      },
    });

    service = new SecurityCryptoService(config);
  });

  it('encrypts and decrypts a secret', () => {
    const secret = 'TEST-TOTP-SECRET';

    const encrypted = service.encrypt(secret);

    expect(encrypted).not.toBe(secret);

    const decrypted = service.decrypt(encrypted);

    expect(decrypted).toBe(secret);
  });

  it('uses a random IV for each encryption', () => {
    const secret = 'same-secret';

    const first = service.encrypt(secret);

    const second = service.encrypt(secret);

    /*
     * AES-GCM encryption
     * не повинно давати
     * однаковий ciphertext
     * для однакового secret.
     */
    expect(first).not.toBe(second);

    expect(service.decrypt(first)).toBe(secret);

    expect(service.decrypt(second)).toBe(secret);
  });

  it('creates deterministic HMAC values', () => {
    const first = service.hmac('192.168.0.1');

    const second = service.hmac('192.168.0.1');

    expect(first).toBe(second);

    expect(first).toMatch(/^[a-f0-9]{64}$/);
  });

  it('creates different HMAC values for different inputs', () => {
    const first = service.hmac('192.168.0.1');

    const second = service.hmac('192.168.0.2');

    expect(first).not.toBe(second);
  });

  it('rejects tampered encrypted data', () => {
    const encrypted = service.encrypt('secret');

    /*
     * Міняємо останній символ
     * ciphertext.
     */
    const last = encrypted.slice(-1);

    const replacement = last === 'A' ? 'B' : 'A';

    const tampered = `${encrypted.slice(0, -1)}${replacement}`;

    expect(() => service.decrypt(tampered)).toThrow();
  });
});
