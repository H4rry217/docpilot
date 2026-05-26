package io.docpilot.common.web.auth;

import io.docpilot.common.auth.AuthSubject;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public interface AuthSubjectResolver {

    Optional<AuthSubject> resolve(HttpServletRequest request);

}
