package io.docpilot.common.auth;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Authenticated platform subject visible to application modules.
 */
@Getter
@Setter
public class AuthSubject {

    /**
     * Stable authenticated user id.
     */
    private String userId;

    /**
     * Display name supplied by the user layer when available.
     */
    private String displayName;

    /**
     * Coarse-grained platform roles, such as admin.
     */
    private Set<String> platformRoles = new HashSet<>();

    public boolean hasRole(String role) {
        return role != null && platformRoles.contains(role);
    }

}
