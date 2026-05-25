package io.docpilot.document.auth;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Authenticated subject visible to the document module.
 */
@Getter
@Setter
public class AuthSubject {

    /**
     * Authenticated user id.
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
