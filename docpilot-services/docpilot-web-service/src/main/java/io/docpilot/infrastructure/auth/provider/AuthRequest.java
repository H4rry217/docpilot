package io.docpilot.infrastructure.auth.provider;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Request data passed to an {@link AuthProvider}.
 *
 * @param token extracted Bearer token without the {@code Bearer } prefix
 * @param servletRequest original request for providers that need extra headers
 */
public record AuthRequest(String token, HttpServletRequest servletRequest) {
}
