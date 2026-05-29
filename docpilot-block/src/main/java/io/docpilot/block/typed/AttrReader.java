package io.docpilot.block.typed;

import io.docpilot.block.model.HtmlDisplayMode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Typed attr accessors for the canonical map-based block model.
 */
public enum AttrReader {
    ;

    public static String string(Map<String, Object> attrs, String key, String fallback) {
        Object value = value(attrs, key);
        return value instanceof String string ? string : fallback;
    }

    public static int integer(Map<String, Object> attrs, String key, int fallback) {
        Object value = value(attrs, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static int boundedInteger(Map<String, Object> attrs, String key, int fallback, int min, int max) {
        int value = integer(attrs, key, fallback);
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }

    public static boolean bool(Map<String, Object> attrs, String key, boolean fallback) {
        Object value = value(attrs, key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            if ("true".equalsIgnoreCase(string)) {
                return true;
            }
            if ("false".equalsIgnoreCase(string)) {
                return false;
            }
        }
        return fallback;
    }

    public static HtmlDisplayMode htmlDisplayMode(Map<String, Object> attrs, String key, HtmlDisplayMode fallback) {
        Object value = value(attrs, key);
        if (value instanceof HtmlDisplayMode displayMode) {
            return displayMode == HtmlDisplayMode.AUTO ? HtmlDisplayMode.AUTO : HtmlDisplayMode.FIXED;
        }
        if (value instanceof String string) {
            return switch (string.trim().toLowerCase(Locale.ROOT)) {
                case "auto" -> HtmlDisplayMode.AUTO;
                case "fixed", "fit" -> HtmlDisplayMode.FIXED;
                default -> fallback;
            };
        }
        return fallback;
    }

    public static Object object(Map<String, Object> attrs, String key) {
        return value(attrs, key);
    }

    public static Map<String, Object> extraAttrs(Map<String, Object> attrs, String... knownKeys) {
        Map<String, Object> extraAttrs = new HashMap<>(attrs == null ? Map.of() : attrs);
        Set<String> keys = new HashSet<>(Set.of(knownKeys));
        keys.forEach(extraAttrs::remove);
        return extraAttrs;
    }

    public static boolean isStringLike(Object value) {
        return value == null || value instanceof String;
    }

    public static boolean isIntegerLike(Object value) {
        if (value == null || value instanceof Number) {
            return true;
        }
        if (value instanceof String string) {
            try {
                Integer.parseInt(string.trim());
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    public static boolean isBooleanLike(Object value) {
        if (value == null || value instanceof Boolean) {
            return true;
        }
        if (value instanceof String string) {
            return "true".equalsIgnoreCase(string) || "false".equalsIgnoreCase(string);
        }
        return false;
    }

    public static boolean isHtmlDisplayModeLike(Object value) {
        if (value == null || value instanceof HtmlDisplayMode) {
            return true;
        }
        if (value instanceof String string) {
            String normalized = string.trim().toLowerCase(Locale.ROOT);
            return "auto".equals(normalized) || "fixed".equals(normalized) || "fit".equals(normalized);
        }
        return false;
    }

    public static boolean isAlignmentLike(Object value) {
        if (value == null) {
            return true;
        }
        if (!(value instanceof String string)) {
            return false;
        }
        return Set.of("none", "left", "center", "right").contains(string.trim().toLowerCase(Locale.ROOT));
    }

    private static Object value(Map<String, Object> attrs, String key) {
        return attrs == null ? null : attrs.get(key);
    }

}
