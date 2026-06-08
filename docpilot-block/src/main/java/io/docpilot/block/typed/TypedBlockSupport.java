package io.docpilot.block.typed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared defensive-copy helpers for typed block value objects.
 */
public final class TypedBlockSupport {

    private TypedBlockSupport() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Copies nullable lists into immutable lists.
     *
     * @param values source values.
     * @return immutable list copy.
     */
    public static <T> List<T> list(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values == null ? List.of() : values));
    }

    /**
     * Copies nullable attrs into immutable maps.
     *
     * @param values source attrs.
     * @return immutable map copy.
     */
    public static Map<String, Object> attrs(Map<String, Object> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values == null ? Map.of() : values));
    }

    /**
     * Copies nullable attrs into mutable maps.
     *
     * @param values source attrs.
     * @return mutable map copy.
     */
    public static Map<String, Object> mutableAttrs(Map<String, Object> values) {
        return new LinkedHashMap<>(values == null ? Map.of() : values);
    }

}
