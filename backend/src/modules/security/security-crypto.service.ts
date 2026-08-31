import { Injectable, InternalServerErrorException } from '@nestjs/common';

import { ConfigService } from '@nestjs/config';

import {
  createCipheriv,
  createDecipheriv,
  createHmac,
  randomBytes,
} from 'crypto';

@Injectable()
export class SecurityCryptoService {
  constructor(private readonly config: ConfigService) {}

  encrypt(value: string): string {
    const iv = randomBytes(12);

    const cipher = createCipheriv('aes-256-gcm', this.encryptionKey, iv);

    const encrypted = Buffer.concat([
      cipher.update(value, 'utf8'),
      cipher.final(),
    ]);

    const tag = cipher.getAuthTag();

    return [
      'v1',
      iv.toString('base64url'),
      tag.toString('base64url'),
      encrypted.toString('base64url'),
    ].join('.');
  }

  decrypt(value: string): string {
    const [version, ivEncoded, tagEncoded, encryptedEncoded] = value.split('.');

    if (version !== 'v1' || !ivEncoded || !tagEncoded || !encryptedEncoded) {
      throw new InternalServerErrorException('Invalid encrypted secret');
    }

    const decipher = createDecipheriv(
      'aes-256-gcm',
      this.encryptionKey,
      Buffer.from(ivEncoded, 'base64url'),
    );

    decipher.setAuthTag(Buffer.from(tagEncoded, 'base64url'));

    return Buffer.concat([
      decipher.update(Buffer.from(encryptedEncoded, 'base64url')),
      decipher.final(),
    ]).toString('utf8');
  }

  hmac(value: string): string {
    return createHmac('sha256', this.hmacKey).update(value).digest('hex');
  }

  private get encryptionKey(): Buffer {
    const encoded = this.config.getOrThrow<string>(
      'security.twoFactorEncryptionKey',
    );

    const key = Buffer.from(encoded, 'base64');

    if (key.length !== 32) {
      throw new InternalServerErrorException(
        'TWO_FACTOR_ENCRYPTION_KEY must contain exactly 32 bytes',
      );
    }

    return key;
  }

  private get hmacKey(): string {
    return this.config.getOrThrow<string>('security.hmacKey');
  }
}
