package io.docpilot.block.typed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

enum TypedBlockSupport {
    ;

    static <T> List<T> list(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values == null ? List.of() : values));
    }

    static Map<String, Object> attrs(Map<String, Object> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values == null ? Map.of() : values));
    }

    static Map<String, Object> mutableAttrs(Map<String, Object> values) {
        return new LinkedHashMap<>(values == null ? Map.of() : values);
    }

}
