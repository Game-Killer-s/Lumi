import { readImageDimensions } from './avatar-image.util';

function pngBuffer(width: number, height: number): Buffer {
  const buffer = Buffer.alloc(33);
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]).copy(buffer, 0);
  buffer.writeUInt32BE(13, 8);
  buffer.write('IHDR', 12, 'latin1');
  buffer.writeUInt32BE(width, 16);
  buffer.writeUInt32BE(height, 20);

  return buffer;
}

function jpegBuffer(width: number, height: number): Buffer {
  const app0 = Buffer.from([
    0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00,
    0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
  ]);
  const sof = Buffer.from([
    0xff, 0xc0, 0x00, 0x11, 0x08, 0x00, 0x00, 0x00, 0x00, 0x03, 0x01, 0x11,
    0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
  ]);

  sof.writeUInt16BE(height, 5);
  sof.writeUInt16BE(width, 7);

  return Buffer.concat([
    Buffer.from([0xff, 0xd8]),
    app0,
    sof,
    Buffer.from([0xff, 0xd9]),
  ]);
}

describe('readImageDimensions', () => {
  it('reads PNG dimensions from IHDR', () => {
    expect(readImageDimensions(pngBuffer(640, 480))).toEqual({
      width: 640,
      height: 480,
    });
  });

  it('reads JPEG dimensions from a SOF segment', () => {
    expect(readImageDimensions(jpegBuffer(300, 200))).toEqual({
      width: 300,
      height: 200,
    });
  });

  it('rejects files without a supported signature', () => {
    expect(readImageDimensions(Buffer.from('GIF89a'))).toBeNull();
  });

  it('rejects a PNG with a broken header', () => {
    const buffer = pngBuffer(100, 100);
    buffer.write('XXXX', 12, 'latin1');

    expect(readImageDimensions(buffer)).toBeNull();
  });
});
