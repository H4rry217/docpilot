package io.docpilot.infrastructure.auth.provider;

import io.docpilot.auth.AuthRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapts Servlet request data to the framework-neutral auth SPI request model.
 */
public final class ServletAuthRequestAdapter {

    public AuthRequest adapt(HttpServletRequest request) {
        return new AuthRequest(
                readHeaders(request),
                request.getRemoteAddr(),
                request.getScheme(),
                request.getMethod(),
                request.getRequestURI()
        );
    }

    private Map<String, List<String>> readHeaders(HttpServletRequest request) {
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return Map.of();
        }

        Map<String, List<String>> headers = new LinkedHashMap<>();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            List<String> values = new ArrayList<>();
            Enumeration<String> headerValues = request.getHeaders(name);
            while (headerValues != null && headerValues.hasMoreElements()) {
                values.add(headerValues.nextElement());
            }
            headers.put(name, values);
        }
        return headers;
    }

}
