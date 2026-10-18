package io.github.kintil555.bedrockpackloader.pack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Counters and warnings collected while translating one pack. */
public final class TranslationReport {
    private static final int MAX_WARNINGS = 100;

    public int texturesExposed;
    public int texturesMappedToVanilla;
    public int texturesUnmapped;
    public int texturesSkippedPbr;
    public int tgaConverted;
    public int flipbooksApplied;
    public int geometryFiles;
    public int geometries;
    public int geometryFormatNormalised;
    public int animationFiles;
    public int animations;
    public int controllers;
    public int entities;
    public int langFiles;

    private final List<String> warnings = new ArrayList<>();
    private int droppedWarnings;

    public void warn(String message) {
        if (this.warnings.size() < MAX_WARNINGS) {
            this.warnings.add(message);
        } else {
            this.droppedWarnings++;
        }
    }

    public List<String> warnings() {
        return Collections.unmodifiableList(this.warnings);
    }

    public int droppedWarnings() {
        return this.droppedWarnings;
    }

    public String summary() {
        return texturesExposed + " textures (" + texturesMappedToVanilla + " mapped to vanilla, " + texturesUnmapped
                + " unmapped, " + texturesSkippedPbr + " PBR skipped, " + tgaConverted + " TGA converted), "
                + geometries + " geometries, " + animations + " animations in " + animationFiles + " files, "
                + controllers + " controllers, " + entities + " client entities";
    }
}
