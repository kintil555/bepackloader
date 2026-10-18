package io.github.kintil555.bedrockpackloader.brarchive;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only view of a Bedrock {@code .brarchive} container.
 *
 * <p>Layout (all integers little-endian):
 * <pre>
 *   header  (16 bytes)  u64 magic | u32 entryCount | u32 version
 *   table   (256 bytes per entry)  u8 nameLength | 247 bytes name (UTF-8, zero padded) | u32 offset | u32 length
 *   payload              entry data, {@code offset} is relative to the end of the table
 * </pre>
 *
 * <p>The archive keeps the raw bytes it was created from and hands out views into them, so opening an
 * entry never copies its payload.
 */
public final class BrArchive {
    public static final long MAGIC = 0x267052A0B125277DL;
    public static final int HEADER_SIZE = 16;
    public static final int ENTRY_SIZE = 256;
    private static final int NAME_FIELD_SIZE = 247;
    private static final int NAME_FIELD_OFFSET = 1;
    private static final int OFFSET_FIELD_OFFSET = 248;
    private static final int LENGTH_FIELD_OFFSET = 252;

    /** One file stored inside the archive. {@code offset} is relative to the payload start. */
    public record Entry(String name, int offset, int length) {
    }

    private final byte[] data;
    private final int payloadStart;
    private final int version;
    private final List<Entry> entries;

    private BrArchive(byte[] data, int payloadStart, int version, List<Entry> entries) {
        this.data = data;
        this.payloadStart = payloadStart;
        this.version = version;
        this.entries = Collections.unmodifiableList(entries);
    }

    /** Returns true when the bytes start with the brarchive magic number. */
    public static boolean hasMagic(byte[] data) {
        return data.length >= HEADER_SIZE
                && ByteBuffer.wrap(data, 0, 8).order(ByteOrder.LITTLE_ENDIAN).getLong() == MAGIC;
    }

    /**
     * Parses an archive.
     *
     * @throws IOException if the data is not a well-formed brarchive
     */
    public static BrArchive parse(byte[] data) throws IOException {
        if (!hasMagic(data)) {
            throw new IOException("Not a brarchive: bad magic or truncated header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        long count = Integer.toUnsignedLong(buffer.getInt(8));
        int version = buffer.getInt(12);

        long tableEnd = HEADER_SIZE + count * ENTRY_SIZE;
        if (tableEnd > data.length) {
            throw new IOException("Corrupt brarchive: entry table (" + count + " entries) exceeds file size");
        }

        int payloadStart = (int) tableEnd;
        long payloadSize = data.length - tableEnd;
        List<Entry> entries = new ArrayList<>((int) count);

        for (int i = 0; i < count; i++) {
            int base = HEADER_SIZE + i * ENTRY_SIZE;
            int nameLength = Byte.toUnsignedInt(data[base]);
            if (nameLength == 0 || nameLength > NAME_FIELD_SIZE) {
                throw new IOException("Corrupt brarchive: invalid name length " + nameLength + " in entry " + i);
            }

            String name = new String(data, base + NAME_FIELD_OFFSET, nameLength, StandardCharsets.UTF_8);
            long offset = Integer.toUnsignedLong(buffer.getInt(base + OFFSET_FIELD_OFFSET));
            long length = Integer.toUnsignedLong(buffer.getInt(base + LENGTH_FIELD_OFFSET));
            if (offset + length > payloadSize) {
                throw new IOException("Corrupt brarchive: entry '" + name + "' lies outside the payload");
            }
            if (!isSafeName(name)) {
                throw new IOException("Unsafe entry name in brarchive: '" + name + "'");
            }

            entries.add(new Entry(name, (int) offset, (int) length));
        }

        return new BrArchive(data, payloadStart, version, entries);
    }

    /** Names must stay inside the virtual folder the archive expands into. */
    private static boolean isSafeName(String name) {
        if (name.startsWith("/") || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
            return false;
        }
        for (String segment : name.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    public int version() {
        return this.version;
    }

    public List<Entry> entries() {
        return this.entries;
    }

    /** Opens a stream over the entry payload without copying it. */
    public InputStream open(Entry entry) {
        return new ByteArrayInputStream(this.data, this.payloadStart + entry.offset(), entry.length());
    }

    /** Copies the entry payload into a fresh array. */
    public byte[] read(Entry entry) {
        int from = this.payloadStart + entry.offset();
        return java.util.Arrays.copyOfRange(this.data, from, from + entry.length());
    }
}
