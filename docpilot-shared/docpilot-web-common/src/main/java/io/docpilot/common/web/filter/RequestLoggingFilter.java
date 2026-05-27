package io.docpilot.common.web.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.docpilot.common.web.logging.LogMask;
import io.docpilot.common.web.support.ClientIpUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.Order;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String NO_PAYLOAD = "-";
    private static final String DISABLED_PAYLOAD = "<disabled>";
    private static final String UNSUPPORTED_PAYLOAD = "<unsupported>";
    private static final String DEFAULT_MASK_TEXT = "***";

    private final ObjectMapper objectMapper;
    private final RequestLoggingConfig config;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RequestLoggingFilter(ObjectMapper objectMapper, RequestLoggingConfig config) {
        this.objectMapper = objectMapper;
        this.config = config;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!config.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpServletRequest requestToUse = wrapRequestIfNeeded(request);
        ContentCachingResponseWrapper responseToUse = new ContentCachingResponseWrapper(response);
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(requestToUse, responseToUse);
        } finally {
            try {
                HandlerMethod handlerMethod = handlerMethod(requestToUse);
                logRequest(requestToUse, requestMaskingRules(handlerMethod));
                logResponse(requestToUse, responseToUse, start, responseMaskingRules(handlerMethod));
            } finally {
                responseToUse.copyBodyToResponse();
            }
        }
    }

    private void logRequest(HttpServletRequest request, MaskingRules maskingRules) {
        log.info("logRequest method={} uri={} ip={} requestParam={} body={}",
                request.getMethod(),
                request.getRequestURI(),
                ClientIpUtils.getClientIp(request),
                requestParamToLog(request, maskingRules),
                requestBodyToLog(request, maskingRules));
    }

    private void logResponse(HttpServletRequest request,
                             ContentCachingResponseWrapper response,
                             long start,
                             MaskingRules maskingRules) {
        log.info("logResponse method={} uri={} status={} costMs={} body={}",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                System.currentTimeMillis() - start,
                responseBodyToLog(request, response, maskingRules));
    }

    private HttpServletRequest wrapRequestIfNeeded(HttpServletRequest request) throws IOException {
        if (!config.isRequestPayloadEnabled()
                || isPayloadExcluded(request)
                || !hasRequestBody(request)
                || !isVisibleContent(request.getContentType())) {
            return request;
        }
        return new CachedBodyRequest(request);
    }

    private String requestParamToLog(HttpServletRequest request, MaskingRules maskingRules) {
        if (!config.isRequestPayloadEnabled() || isPayloadExcluded(request)) {
            return DISABLED_PAYLOAD;
        }
        Map<String, Object> requestParams = sanitizeRequestParameters(request.getQueryString(), maskingRules);
        if (requestParams.isEmpty()) {
            return "{}";
        }
        return writeJson(requestParams);
    }

    private String requestBodyToLog(HttpServletRequest request, MaskingRules maskingRules) {
        if (!config.isRequestPayloadEnabled() || isPayloadExcluded(request)) {
            return DISABLED_PAYLOAD;
        }
        if (!hasRequestBody(request)) {
            return NO_PAYLOAD;
        }
        if (!isVisibleContent(request.getContentType())) {
            return UNSUPPORTED_PAYLOAD;
        }
        if (request instanceof CachedBodyRequest cachedBodyRequest) {
            return payloadToLog(
                    cachedBodyRequest.getCachedBody(),
                    request.getContentType(),
                    charset(request.getCharacterEncoding()),
                    maskingRules
            );
        }
        return "<unavailable>";
    }

    private String responseBodyToLog(HttpServletRequest request,
                                     ContentCachingResponseWrapper response,
                                     MaskingRules maskingRules) {
        if (!config.isResponsePayloadEnabled() || isPayloadExcluded(request)) {
            return DISABLED_PAYLOAD;
        }
        byte[] body = response.getContentAsByteArray();
        if (body.length == 0) {
            return NO_PAYLOAD;
        }
        String contentType = response.getContentType();
        if (StringUtils.hasText(contentType) && !isVisibleContent(contentType)) {
            return UNSUPPORTED_PAYLOAD;
        }
        return payloadToLog(body, contentType, charset(response.getCharacterEncoding()), maskingRules);
    }

    private String payloadToLog(byte[] payload, String contentType, Charset charset, MaskingRules maskingRules) {
        if (payload.length == 0) {
            return NO_PAYLOAD;
        }

        String text = new String(payload, charset);
        if (isJsonContent(contentType) || looksLikeJson(text)) {
            return truncate(sanitizeJson(text, maskingRules));
        }
        if (isFormContent(contentType)) {
            return truncate(writeJson(sanitizeFormBody(text, charset, maskingRules)));
        }
        return truncate(text);
    }

    private String sanitizeJson(String payload, MaskingRules maskingRules) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            sanitizeJsonNode(node, maskingRules);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return payload;
        }
    }

    private void sanitizeJsonNode(JsonNode node, MaskingRules maskingRules) {
        if (node instanceof ObjectNode objectNode) {
            List<String> fieldsToRemove = new ArrayList<>();
            objectNode.properties().forEach(entry -> {
                String fieldName = entry.getKey();
                FieldMaskRule rule = fieldMaskRule(fieldName, maskingRules);
                if (rule != null && rule.mode() == LogMask.Mode.OMIT) {
                    fieldsToRemove.add(fieldName);
                    return;
                }
                if (rule != null) {
                    objectNode.set(fieldName, TextNode.valueOf(rule.maskText()));
                    return;
                }
                sanitizeJsonNode(entry.getValue(), maskingRules);
            });
            fieldsToRemove.forEach(objectNode::remove);
            return;
        }

        if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(item -> sanitizeJsonNode(item, maskingRules));
        }
    }

    private Map<String, Object> sanitizeRequestParameters(String queryString, MaskingRules maskingRules) {
        if (!StringUtils.hasText(queryString)) {
            return Map.of();
        }

        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String pair : queryString.split("&")) {
            if (!StringUtils.hasText(pair)) {
                continue;
            }
            int separator = pair.indexOf('=');
            String rawKey = separator >= 0 ? pair.substring(0, separator) : pair;
            String rawValue = separator >= 0 ? pair.substring(separator + 1) : "";
            String key = urlDecode(rawKey, StandardCharsets.UTF_8);
            FieldMaskRule rule = fieldMaskRule(key, maskingRules);
            if (rule != null && rule.mode() == LogMask.Mode.OMIT) {
                continue;
            }
            String value = rule == null ? urlDecode(rawValue, StandardCharsets.UTF_8) : rule.maskText();
            values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        return flattenParameters(values);
    }

    private Map<String, Object> sanitizeFormBody(String payload, Charset charset, MaskingRules maskingRules) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String pair : payload.split("&")) {
            if (!StringUtils.hasText(pair)) {
                continue;
            }
            int separator = pair.indexOf('=');
            String rawKey = separator >= 0 ? pair.substring(0, separator) : pair;
            String rawValue = separator >= 0 ? pair.substring(separator + 1) : "";
            String key = urlDecode(rawKey, charset);
            FieldMaskRule rule = fieldMaskRule(key, maskingRules);
            if (rule != null && rule.mode() == LogMask.Mode.OMIT) {
                continue;
            }
            String value = rule == null ? urlDecode(rawValue, charset) : rule.maskText();
            values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }

        return flattenParameters(values);
    }

    private Map<String, Object> flattenParameters(Map<String, List<String>> values) {
        Map<String, Object> flattened = new LinkedHashMap<>();
        values.forEach((key, fieldValues) -> flattened.put(key,
                fieldValues.size() == 1 ? fieldValues.getFirst() : fieldValues));
        return flattened;
    }

    private String urlDecode(String value, Charset charset) {
        return URLDecoder.decode(value, charset);
    }

    private boolean isPayloadExcluded(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return Optional.ofNullable(config.getPayloadExcludedPaths()).stream()
                .flatMap(Set::stream)
                .filter(StringUtils::hasText)
                .anyMatch(pattern -> pathMatcher.match(pattern, uri));
    }

    private boolean hasRequestBody(HttpServletRequest request) {
        if (request.getContentLengthLong() > 0) {
            return true;
        }
        return switch (request.getMethod()) {
            case "POST", "PUT", "PATCH" -> StringUtils.hasText(request.getContentType());
            default -> false;
        };
    }

    private boolean isVisibleContent(String contentType) {
        return isJsonContent(contentType) || isFormContent(contentType) || isTextContent(contentType);
    }

    private boolean isJsonContent(String contentType) {
        MediaType mediaType = parseMediaType(contentType);
        if (mediaType == null) {
            return false;
        }
        return MediaType.APPLICATION_JSON.includes(mediaType) || mediaType.getSubtype().endsWith("+json");
    }

    private boolean isFormContent(String contentType) {
        MediaType mediaType = parseMediaType(contentType);
        return mediaType != null && MediaType.APPLICATION_FORM_URLENCODED.includes(mediaType);
    }

    private boolean isTextContent(String contentType) {
        MediaType mediaType = parseMediaType(contentType);
        if (mediaType == null) {
            return false;
        }
        return "text".equalsIgnoreCase(mediaType.getType())
                || MediaType.APPLICATION_XML.includes(mediaType)
                || mediaType.getSubtype().endsWith("+xml");
    }

    private MediaType parseMediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return null;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return null;
        }
    }

    private boolean looksLikeJson(String text) {
        String trimmed = text.stripLeading();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private HandlerMethod handlerMethod(HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod handlerMethod) {
            return handlerMethod;
        }
        return null;
    }

    private MaskingRules requestMaskingRules(HandlerMethod handlerMethod) {
        MaskingRules rules = new MaskingRules();
        if (handlerMethod == null) {
            return rules;
        }
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            LogMask parameterMask = parameter.getParameterAnnotation(LogMask.class);
            if (parameterMask != null) {
                requestParameterName(parameter).ifPresent(name -> rules.add(name, parameterMask));
            }
            if (parameter.hasParameterAnnotation(RequestBody.class)) {
                collectMaskingRules(ResolvableType.forMethodParameter(parameter), rules, new HashSet<>());
            }
        }
        return rules;
    }

    private Optional<String> requestParameterName(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            if (StringUtils.hasText(requestParam.name())) {
                return Optional.of(requestParam.name());
            }
            if (StringUtils.hasText(requestParam.value())) {
                return Optional.of(requestParam.value());
            }
        }
        return Optional.ofNullable(parameter.getParameterName()).filter(StringUtils::hasText);
    }

    private MaskingRules responseMaskingRules(HandlerMethod handlerMethod) {
        MaskingRules rules = new MaskingRules();
        if (handlerMethod == null) {
            return rules;
        }
        collectMaskingRules(ResolvableType.forMethodReturnType(handlerMethod.getMethod()), rules, new HashSet<>());
        return rules;
    }

    private void collectMaskingRules(ResolvableType type, MaskingRules rules, Set<Class<?>> visited) {
        Class<?> resolved = type.resolve();
        if (resolved != null) {
            collectMaskingRules(resolved, rules, visited);
        }
        for (ResolvableType generic : type.getGenerics()) {
            collectMaskingRules(generic, rules, visited);
        }
    }

    private void collectMaskingRules(Class<?> type, MaskingRules rules, Set<Class<?>> visited) {
        if (type == null || type.isPrimitive() || type.isEnum()) {
            return;
        }
        if (type.isArray()) {
            collectMaskingRules(type.getComponentType(), rules, visited);
            return;
        }
        if (!shouldInspectType(type) || !visited.add(type)) {
            return;
        }

        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                addMaskRule(component.getName(), component, rules);
                collectMaskingRules(ResolvableType.forType(component.getGenericType()), rules, visited);
            }
        }

        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            addMaskRule(field.getName(), field, rules);
            collectMaskingRules(ResolvableType.forField(field), rules, visited);
        }
    }

    private boolean shouldInspectType(Class<?> type) {
        Package typePackage = type.getPackage();
        String packageName = typePackage == null ? "" : typePackage.getName();
        return !packageName.startsWith("java.")
                && !packageName.startsWith("javax.")
                && !packageName.startsWith("jakarta.")
                && !packageName.startsWith("org.springframework.");
    }

    private void addMaskRule(String fieldName, AnnotatedElement element, MaskingRules rules) {
        LogMask mask = element.getAnnotation(LogMask.class);
        if (mask != null) {
            rules.add(fieldName, mask);
        }
    }

    private FieldMaskRule fieldMaskRule(String fieldName, MaskingRules rules) {
        if (rules == null) {
            return null;
        }
        return rules.ruleFor(fieldName);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private String truncate(String value) {
        int maxLength = Math.max(config.getMaxPayloadLength(), 0);
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...<truncated>";
    }

    private Charset charset(String characterEncoding) {
        if (!StringUtils.hasText(characterEncoding)) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(characterEncoding);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private static final class MaskingRules {

        private final Map<String, FieldMaskRule> rules = new LinkedHashMap<>();

        private void add(String fieldName, LogMask mask) {
            if (!StringUtils.hasText(fieldName)) {
                return;
            }
            String maskText = StringUtils.hasText(mask.maskText()) ? mask.maskText() : DEFAULT_MASK_TEXT;
            rules.put(fieldName.toLowerCase(Locale.ROOT), new FieldMaskRule(mask.mode(), maskText));
        }

        private FieldMaskRule ruleFor(String fieldName) {
            if (!StringUtils.hasText(fieldName)) {
                return null;
            }
            return rules.get(fieldName.toLowerCase(Locale.ROOT));
        }

    }

    private record FieldMaskRule(LogMask.Mode mode, String maskText) {
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        private CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
        }

        private byte[] getCachedBody() {
            return cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return inputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // Synchronous request processing does not need read callbacks.
                }

                @Override
                public int read() {
                    return inputStream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), charset(getCharacterEncoding())));
        }

        private Charset charset(String characterEncoding) {
            if (!StringUtils.hasText(characterEncoding)) {
                return StandardCharsets.UTF_8;
            }
            try {
                return Charset.forName(characterEncoding);
            } catch (Exception e) {
                return StandardCharsets.UTF_8;
            }
        }
    }

}
