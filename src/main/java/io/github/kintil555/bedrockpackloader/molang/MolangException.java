package io.github.kintil555.bedrockpackloader.molang;

/** Thrown when a Molang expression cannot be parsed. */
public final class MolangException extends RuntimeException {
    public MolangException(String message) {
        super(message);
    }
}
