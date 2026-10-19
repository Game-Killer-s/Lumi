export interface ImageDimensions {
  width: number;
  height: number;
}

const PNG_SIGNATURE = Buffer.from([
  0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
]);

// SOF-маркери JPEG (C4, C8 і CC не є SOF).
const JPEG_SOF_MARKERS = new Set([
  0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf,
]);

// Читає реальні розміри зображення, не довіряючи розширенню та mime.
export function readImageDimensions(buffer: Buffer): ImageDimensions | null {
  if (buffer.length >= 24 && buffer.subarray(0, 8).equals(PNG_SIGNATURE)) {
    if (buffer.subarray(12, 16).toString('latin1') !== 'IHDR') {
      return null;
    }

    const width = buffer.readUInt32BE(16);
    const height = buffer.readUInt32BE(20);

    return width > 0 && height > 0 ? { width, height } : null;
  }

  if (buffer.length < 4 || buffer[0] !== 0xff || buffer[1] !== 0xd8) {
    return null;
  }

  return readJpegDimensions(buffer);
}

function readJpegDimensions(buffer: Buffer): ImageDimensions | null {
  let offset = 2;

  while (offset + 2 < buffer.length) {
    // Між сегментами може бути кілька 0xFF-байтів вирівнювання.
    if (buffer[offset] !== 0xff) {
      offset += 1;
      continue;
    }

    while (offset < buffer.length && buffer[offset] === 0xff) {
      offset += 1;
    }

    if (offset >= buffer.length) {
      return null;
    }

    const marker = buffer[offset];
    offset += 1;

    // Маркери без сегмента довжини.
    if (marker === 0x01 || (marker >= 0xd0 && marker <= 0xd9)) {
      continue;
    }

    if (marker === 0xda || offset + 2 > buffer.length) {
      return null;
    }

    const length = buffer.readUInt16BE(offset);

    if (length < 2 || offset + length > buffer.length) {
      return null;
    }

    if (JPEG_SOF_MARKERS.has(marker)) {
      if (length < 7) {
        return null;
      }

      const height = buffer.readUInt16BE(offset + 3);
      const width = buffer.readUInt16BE(offset + 5);

      return width > 0 && height > 0 ? { width, height } : null;
    }

    offset += length;
  }

  return null;
}
