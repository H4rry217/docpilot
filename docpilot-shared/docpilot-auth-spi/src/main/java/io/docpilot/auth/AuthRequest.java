package io.docpilot.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Framework-neutral request facts passed to an {@link AuthProvider}.
 */
public record AuthRequest(
        Map<String, List<String>> headers,
        String remoteAddress,
        String scheme,
        String method,
        String path
) {

    public AuthRequest {
        headers = copyHeaders(headers);
    }

    public Optional<String> firstHeader(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String expected = name.toLowerCase(Locale.ROOT);
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().toLowerCase(Locale.ROOT).equals(expected))
                .flatMap(entry -> entry.getValue().stream())
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    public Optional<String> bearerToken() {
        return firstHeader("Authorization")
                .filter(value -> value.regionMatches(true, 0, "Bearer ", 0, 7))
                .map(value -> value.substring(7).trim())
                .filter(token -> !token.isBlank());
    }

    private static Map<String, List<String>> copyHeaders(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        headers.forEach((name, values) -> copy.put(name, values == null ? List.of() : List.copyOf(values)));
        return Map.copyOf(copy);
    }

}
