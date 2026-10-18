package io.github.kintil555.bedrockpackloader.convert;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/** Minimal PNG writer (8-bit RGBA, no interlacing) so no AWT classes are needed. */
public final class PngEncoder {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private PngEncoder() {
    }

    public static byte[] encode(int width, int height, byte[] rgba) throws IOException {
        if (rgba.length != width * height * 4) {
            throw new IllegalArgumentException("Pixel buffer does not match " + width + "x" + height);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(rgba.length / 2 + 128);
        out.write(SIGNATURE);

        ByteArrayOutputStream header = new ByteArrayOutputStream(13);
        DataOutputStream headerData = new DataOutputStream(header);
        headerData.writeInt(width);
        headerData.writeInt(height);
        headerData.writeByte(8);
        headerData.writeByte(6);
        headerData.writeByte(0);
        headerData.writeByte(0);
        headerData.writeByte(0);
        writeChunk(out, "IHDR", header.toByteArray());

        int stride = width * 4;
        ByteArrayOutputStream compressed = new ByteArrayOutputStream(rgba.length / 2 + 64);
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            for (int y = 0; y < height; y++) {
                deflater.write(0);
                deflater.write(rgba, y * stride, stride);
            }
        }
        writeChunk(out, "IDAT", compressed.toByteArray());
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        DataOutputStream stream = new DataOutputStream(out);
        stream.writeInt(data.length);

        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        stream.write(typeBytes);
        stream.write(data);

        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        stream.writeInt((int) crc.getValue());
    }
}
