package io.docpilot.common.web.filter;

import io.docpilot.common.auth.AuthSubjectContext;
import io.docpilot.common.context.RequestConstants;
import io.docpilot.common.context.RequestIdGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(RequestConstants.HEADER_REQUEST_ID);
        if (!StringUtils.hasText(requestId)) {
            requestId = RequestIdGenerator.nextId();
        }

        MDC.put(RequestConstants.KEY_REQUEST_ID, requestId);
        MDC.put(RequestConstants.KEY_REQUEST_URI, request.getRequestURI());
        response.setHeader(RequestConstants.HEADER_REQUEST_ID, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            AuthSubjectContext.clear();
            MDC.clear();
        }
    }

}
