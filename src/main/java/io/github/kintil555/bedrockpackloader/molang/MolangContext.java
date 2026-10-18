package io.github.kintil555.bedrockpackloader.molang;

import java.util.List;

/**
 * Supplies the entity-dependent part of a Molang evaluation.
 *
 * <p>Query names are lower-case, without the {@code q.}/{@code query.} prefix (e.g. {@code is_on_ground}).
 * Arguments are {@link Double} or {@link String}. Implementations return a {@link Double} or {@link String},
 * or {@code null} when the query is not supported (it then evaluates to 0 and is reported through
 * {@link #unsupportedQuery(String)} once per scope).</p>
 */
public interface MolangContext {
    MolangContext EMPTY = (name, args) -> null;

    Object query(String name, Object[] args);

    /** Read-only array defined outside the expression (e.g. render controller {@code array.skins}). */
    default List<Object> array(String name) {
        return null;
    }

    default void unsupportedQuery(String name) {
    }
}
