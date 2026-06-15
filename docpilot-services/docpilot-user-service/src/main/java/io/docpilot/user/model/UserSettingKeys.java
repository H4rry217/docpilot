package io.docpilot.user.model;

import io.docpilot.common.exception.BadRequestException;
import io.docpilot.common.json.JsonUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Whitelist and validation table for cloud user settings.
 *
 * <p>Each setting is persisted as one raw string value. The API still exposes typed JSON values,
 * but storage intentionally accepts simple strings such as {@code true}, {@code 0}, and {@code 64}
 * so future settings do not have to be JSON-only.</p>
 */
public final class UserSettingKeys {

    public static final String APP_LOCALE = "app.locale";

    public static final String APP_DEVELOPER_MODE = "app.developerMode";

    public static final String INLINE_COMPLETION_ENABLED = "inlineCompletion.enabled";

    public static final String INLINE_COMPLETION_IDLE_DELAY_MS = "inlineCompletion.idleDelayMs";

    public static final String INLINE_COMPLETION_CANDIDATE_COUNT = "inlineCompletion.candidateCount";

    public static final String INLINE_COMPLETION_MAX_OUTPUT_TOKENS_SHORT = "inlineCompletion.maxOutputTokens.short";

    public static final String INLINE_COMPLETION_MAX_OUTPUT_TOKENS_SENTENCE = "inlineCompletion.maxOutputTokens.sentence";

    public static final String INLINE_COMPLETION_MAX_OUTPUT_TOKENS_PARAGRAPH = "inlineCompletion.maxOutputTokens.paragraph";

    public static final String INLINE_COMPLETION_MAX_OUTPUT_TOKENS_LIST_ITEM = "inlineCompletion.maxOutputTokens.listItem";

    public static final String INLINE_COMPLETION_MAX_OUTPUT_TOKENS_TABLE_CELL = "inlineCompletion.maxOutputTokens.tableCell";

    public static final String INLINE_COMPLETION_MAX_OUTPUT_TOKENS_CODE_LINE = "inlineCompletion.maxOutputTokens.codeLine";

    /**
     * The single source of truth for supported setting keys, default values, and server-side limits.
     */
    private static final List<SettingSpec> SETTING_SPECS = List.of(
            setting(APP_LOCALE, String.class, "zh-CN", "Interface language", List.of("zh-CN", "en-US")),
            setting(APP_DEVELOPER_MODE, Boolean.class, false, "Developer mode"),
            setting(INLINE_COMPLETION_ENABLED, Boolean.class, true, "Inline completion enabled"),
            integer(INLINE_COMPLETION_IDLE_DELAY_MS, 500, "Inline completion idle delay in milliseconds",
                    200, 2000, false),
            integer(INLINE_COMPLETION_CANDIDATE_COUNT, 3, "Inline completion candidate count",
                    1, 5, false),
            integer(INLINE_COMPLETION_MAX_OUTPUT_TOKENS_SHORT, 32, "Max output tokens for short inline completions",
                    1, 128, true),
            integer(INLINE_COMPLETION_MAX_OUTPUT_TOKENS_SENTENCE, 64, "Max output tokens for sentence inline completions",
                    1, 256, true),
            integer(INLINE_COMPLETION_MAX_OUTPUT_TOKENS_PARAGRAPH, 160, "Max output tokens for paragraph inline completions",
                    1, 512, true),
            integer(INLINE_COMPLETION_MAX_OUTPUT_TOKENS_LIST_ITEM, 80, "Max output tokens for list item inline completions",
                    1, 256, true),
            integer(INLINE_COMPLETION_MAX_OUTPUT_TOKENS_TABLE_CELL, 32, "Max output tokens for table cell inline completions",
                    1, 128, true),
            integer(INLINE_COMPLETION_MAX_OUTPUT_TOKENS_CODE_LINE, 96, "Max output tokens for code line inline completions",
                    1, 256, true)
    );

    private static final Map<String, SettingSpec> SPECS_BY_KEY = specsByKey();

    private UserSettingKeys() {
        throw new AssertionError("No UserSettingKeys instances");
    }

    public static List<String> allKeys() {
        return List.copyOf(SPECS_BY_KEY.keySet());
    }

    public static String requireKey(String key) {
        return requireSpec(key).key();
    }

    public static Object defaultValue(String key) {
        return requireSpec(key).defaultValue();
    }

    public static String description(String key) {
        return requireSpec(key).description();
    }

    public static String serialize(String key, Object value) {
        Object normalized = normalize(requireSpec(key), value);
        return normalized instanceof String text ? text : String.valueOf(normalized);
    }

    /**
     * Converts the raw persisted value back to the typed value returned by the HTTP API.
     */
    public static Object parseStored(String key, String settingValue) {
        return normalize(requireSpec(key), settingValue);
    }

    private static Map<String, SettingSpec> specsByKey() {
        Map<String, SettingSpec> specs = new LinkedHashMap<>();
        for (SettingSpec spec : SETTING_SPECS) {
            specs.put(spec.key(), spec);
        }
        return Collections.unmodifiableMap(specs);
    }

    private static SettingSpec setting(String key, Class<?> type, Object defaultValue, String description) {
        return setting(key, type, defaultValue, description, List.of());
    }

    private static SettingSpec setting(String key,
                                       Class<?> type,
                                       Object defaultValue,
                                       String description,
                                       List<?> allowedValues) {
        return new SettingSpec(key, type, defaultValue, description, allowedValues, OptionalInt.empty(), OptionalInt.empty(), false);
    }

    private static SettingSpec integer(String key,
                                       int defaultValue,
                                       String description,
                                       int minValue,
                                       int maxValue,
                                       boolean clampAboveMax) {
        return new SettingSpec(
                key,
                Integer.class,
                defaultValue,
                description,
                List.of(),
                OptionalInt.of(minValue),
                OptionalInt.of(maxValue),
                clampAboveMax
        );
    }

    private static SettingSpec requireSpec(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("setting key is required");
        }
        SettingSpec spec = SPECS_BY_KEY.get(key);
        if (spec == null) {
            throw new BadRequestException("Unsupported user setting key: " + key);
        }
        return spec;
    }

    private static Object normalize(SettingSpec spec, Object value) {
        if (value == null) {
            throw new BadRequestException(spec.key() + " must not be null");
        }
        Object normalized;
        if (String.class.equals(spec.type())) {
            normalized = stringSettingValue(spec.key(), value);
        } else if (Boolean.class.equals(spec.type())) {
            normalized = booleanValue(spec.key(), value);
        } else if (Integer.class.equals(spec.type())) {
            normalized = integerRangeValue(spec, value);
        } else {
            throw new BadRequestException("Unsupported user setting type: " + spec.type().getSimpleName());
        }
        if (!spec.allowedValues().isEmpty() && !spec.allowedValues().contains(normalized)) {
            throw new BadRequestException("Unsupported value for " + spec.key() + ": " + normalized);
        }
        return normalized;
    }

    private static String stringSettingValue(String key, Object value) {
        String text = stringValue(value);
        if (text == null) {
            throw invalidType(key, "string");
        }
        return unwrapJsonString(text);
    }

    private static Boolean booleanValue(String key, Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = stringValue(value);
        if (text == null) {
            throw invalidType(key, "boolean");
        }
        return switch (unwrapJsonString(text).trim().toLowerCase()) {
            case "true", "1" -> true;
            case "false", "0" -> false;
            default -> throw invalidType(key, "boolean");
        };
    }

    private static Integer integerRangeValue(SettingSpec spec, Object value) {
        int number = integerValue(spec.key(), value);
        if (spec.minValue().isPresent() && number < spec.minValue().getAsInt()) {
            throw new BadRequestException(spec.key() + " must be at least " + spec.minValue().getAsInt());
        }
        if (spec.maxValue().isPresent() && number > spec.maxValue().getAsInt()) {
            if (spec.clampAboveMax()) {
                return spec.maxValue().getAsInt();
            }
            throw new BadRequestException(spec.key() + " must be at most " + spec.maxValue().getAsInt());
        }
        return number;
    }

    private static int integerValue(String key, Object value) {
        BigDecimal decimal;
        if (value instanceof Number number) {
            decimal = new BigDecimal(number.toString());
        } else {
            String text = stringValue(value);
            if (text == null) {
                throw invalidType(key, "integer");
            }
            try {
                decimal = new BigDecimal(unwrapJsonString(text).trim());
            } catch (NumberFormatException exception) {
                throw invalidType(key, "integer");
            }
        }
        try {
            return decimal.toBigIntegerExact().intValueExact();
        } catch (ArithmeticException exception) {
            throw new BadRequestException(key + " must be an integer");
        }
    }

    private static String stringValue(Object value) {
        return value instanceof String text ? text : null;
    }

    private static String unwrapJsonString(String value) {
        String text = value.strip();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            try {
                return JsonUtils.convert(text, String.class);
            } catch (RuntimeException ignored) {
                return text;
            }
        }
        return text;
    }

    private static BadRequestException invalidType(String key, String expectedType) {
        return new BadRequestException(key + " must be a " + expectedType);
    }

    /**
     * Compact key metadata; add new user settings by appending one row to {@link #SETTING_SPECS}.
     */
    private record SettingSpec(
            String key,
            Class<?> type,
            Object defaultValue,
            String description,
            List<?> allowedValues,
            OptionalInt minValue,
            OptionalInt maxValue,
            boolean clampAboveMax
    ) {
    }
}
