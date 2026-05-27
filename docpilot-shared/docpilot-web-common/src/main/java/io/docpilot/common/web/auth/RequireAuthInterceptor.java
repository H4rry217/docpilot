package io.docpilot.common.web.auth;

import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.auth.AuthSubjectContext;
import io.docpilot.common.context.RequestConstants;
import io.docpilot.common.exception.ForbiddenException;
import io.docpilot.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

public class RequireAuthInterceptor implements HandlerInterceptor {

    private final AuthSubjectResolver authSubjectResolver;

    public RequireAuthInterceptor(AuthSubjectResolver authSubjectResolver) {
        this.authSubjectResolver = authSubjectResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Optional<AuthSubject> subject = authSubjectResolver.resolve(request);
        subject.ifPresent(AuthSubjectContext::set);

        RequireAuth requireAuth = findRequireAuth(handler);
        if (requireAuth == null) {
            return true;
        }

        AuthSubject authenticatedSubject = subject.orElseThrow(() -> new UnauthorizedException("Authentication is required"));
        for (String role : requireAuth.roles()) {
            if (!authenticatedSubject.hasRole(role)) {
                throw new ForbiddenException("Required role is missing: " + role);
            }
        }

        String userId = String.valueOf(authenticatedSubject.getUserId());
        MDC.put(RequestConstants.KEY_USER_ID, userId);
        MDC.put(RequestConstants.KEY_USER, userId);
        return true;
    }

    private RequireAuth findRequireAuth(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return null;
        }

        RequireAuth methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), RequireAuth.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }

        return AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequireAuth.class);
    }

}
