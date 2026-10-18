package io.github.kintil555.bedrockpackloader.convert;

import java.io.IOException;

/**
 * Decoder for the Truevision TGA files Bedrock packs use for many entity textures.
 * Supports uncompressed and run-length encoded true-colour (24/32 bit) and grey-scale (8 bit) images
 * with any origin. The result is always RGBA, row-major, top row first.
 */
public final class TgaDecoder {
    private static final int HEADER_SIZE = 18;

    /** Decoded pixels in RGBA byte order. */
    public record Image(int width, int height, byte[] rgba) {
    }

    private TgaDecoder() {
    }

    public static Image decode(byte[] data) throws IOException {
        if (data.length < HEADER_SIZE) {
            throw new IOException("TGA file too short");
        }

        int idLength = Byte.toUnsignedInt(data[0]);
        int colorMapType = Byte.toUnsignedInt(data[1]);
        int imageType = Byte.toUnsignedInt(data[2]);
        int colorMapLength = u16(data, 5);
        int colorMapEntryBits = Byte.toUnsignedInt(data[7]);
        int width = u16(data, 12);
        int height = u16(data, 14);
        int bitsPerPixel = Byte.toUnsignedInt(data[16]);
        int descriptor = Byte.toUnsignedInt(data[17]);

        boolean rle = imageType == 10 || imageType == 11;
        boolean grey = imageType == 3 || imageType == 11;
        boolean trueColor = imageType == 2 || imageType == 10;
        if (!grey && !trueColor) {
            throw new IOException("Unsupported TGA image type " + imageType);
        }
        if (width <= 0 || height <= 0) {
            throw new IOException("TGA has empty dimensions");
        }

        int bytesPerPixel = bitsPerPixel / 8;
        boolean validDepth = grey ? bitsPerPixel == 8 : (bitsPerPixel == 24 || bitsPerPixel == 32);
        if (!validDepth) {
            throw new IOException("Unsupported TGA depth " + bitsPerPixel);
        }

        int position = HEADER_SIZE + idLength;
        if (colorMapType == 1) {
            position += colorMapLength * ((colorMapEntryBits + 7) / 8);
        }

        byte[] raw = new byte[Math.multiplyExact(Math.multiplyExact(width, height), bytesPerPixel)];
        if (rle) {
            position = readRle(data, position, raw, bytesPerPixel);
        } else {
            if (position + raw.length > data.length) {
                throw new IOException("TGA pixel data truncated");
            }
            System.arraycopy(data, position, raw, 0, raw.length);
        }

        boolean topToBottom = (descriptor & 0x20) != 0;
        boolean rightToLeft = (descriptor & 0x10) != 0;
        byte[] rgba = new byte[width * height * 4];

        for (int y = 0; y < height; y++) {
            int sourceRow = topToBottom ? y : height - 1 - y;
            for (int x = 0; x < width; x++) {
                int sourceColumn = rightToLeft ? width - 1 - x : x;
                int source = (sourceRow * width + sourceColumn) * bytesPerPixel;
                int target = (y * width + x) * 4;
                if (grey) {
                    byte value = raw[source];
                    rgba[target] = value;
                    rgba[target + 1] = value;
                    rgba[target + 2] = value;
                    rgba[target + 3] = (byte) 0xFF;
                } else {
                    rgba[target] = raw[source + 2];
                    rgba[target + 1] = raw[source + 1];
                    rgba[target + 2] = raw[source];
                    rgba[target + 3] = bytesPerPixel == 4 ? raw[source + 3] : (byte) 0xFF;
                }
            }
        }
        return new Image(width, height, rgba);
    }

    private static int readRle(byte[] data, int position, byte[] raw, int bytesPerPixel) throws IOException {
        int out = 0;
        while (out < raw.length) {
            if (position >= data.length) {
                throw new IOException("TGA RLE data truncated");
            }

            int packet = Byte.toUnsignedInt(data[position++]);
            int count = (packet & 0x7F) + 1;
            int bytes = count * bytesPerPixel;
            if (out + bytes > raw.length) {
                throw new IOException("TGA RLE packet overruns the image");
            }

            if ((packet & 0x80) != 0) {
                if (position + bytesPerPixel > data.length) {
                    throw new IOException("TGA RLE data truncated");
                }
                for (int i = 0; i < count; i++) {
                    System.arraycopy(data, position, raw, out, bytesPerPixel);
                    out += bytesPerPixel;
                }
                position += bytesPerPixel;
            } else {
                if (position + bytes > data.length) {
                    throw new IOException("TGA RLE data truncated");
                }
                System.arraycopy(data, position, raw, out, bytes);
                out += bytes;
                position += bytes;
            }
        }
        return position;
    }

    private static int u16(byte[] data, int offset) {
        return Byte.toUnsignedInt(data[offset]) | (Byte.toUnsignedInt(data[offset + 1]) << 8);
    }
}
