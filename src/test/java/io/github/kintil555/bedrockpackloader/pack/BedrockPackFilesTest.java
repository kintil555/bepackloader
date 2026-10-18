package io.github.kintil555.bedrockpackloader.pack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BedrockPackFilesTest {
    private static final long MAGIC = 0x267052A0B125277DL;

    /** One-entry archive whose single file has the given payload (empty payload = placeholder stub). */
    private static byte[] archive(String name, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(16 + 256 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(MAGIC).putInt(1).putInt(0);
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        buffer.put((byte) nameBytes.length).put(nameBytes);
        buffer.position(16 + 248).putInt(0).putInt(payload.length);
        buffer.position(16 + 256).put(payload);
        return buffer.array();
    }

    @Test
    void emptyArchiveStubDoesNotReplaceLooseFile(@TempDir Path dir) throws IOException {
        byte[] real = {1, 2, 3, 4};
        Files.writeString(dir.resolve("manifest.json"), "{}");
        Files.createDirectories(dir.resolve("textures/blocks"));
        Files.createDirectories(dir.resolve("__brarchive/textures"));
        Files.write(dir.resolve("textures/blocks/stone.png"), real);
        Files.write(dir.resolve("__brarchive/textures/blocks.brarchive"), archive("stone.png", new byte[0]));

        try (BedrockPackRoot root = BedrockPackRoot.open(dir)) {
            BedrockPackFiles files = BedrockPackFiles.build(root, null);
            assertArrayEquals(real, files.readBytes("textures/blocks/stone.png"));
        }
    }

    @Test
    void nonEmptyArchiveEntryIsExpanded(@TempDir Path dir) throws IOException {
        byte[] payload = {9, 8, 7};
        Files.writeString(dir.resolve("manifest.json"), "{}");
        Files.createDirectories(dir.resolve("__brarchive/textures"));
        Files.write(dir.resolve("__brarchive/textures/blocks.brarchive"), archive("dirt.png", payload));

        try (BedrockPackRoot root = BedrockPackRoot.open(dir)) {
            BedrockPackFiles files = BedrockPackFiles.build(root, null);
            assertTrue(files.contains("textures/blocks/dirt.png"));
            assertArrayEquals(payload, files.readBytes("textures/blocks/dirt.png"));
        }
    }
}
