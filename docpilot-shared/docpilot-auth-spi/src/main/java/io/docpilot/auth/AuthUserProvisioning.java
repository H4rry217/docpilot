package io.docpilot.auth;

/**
 * Optional local DocPilot user fields supplied by an AuthProvider.
 */
public record AuthUserProvisioning(
        Long userId,
        String email,
        String displayName
) {

    public static AuthUserProvisioning from(AuthPrincipal principal) {
        if (principal == null) {
            return new AuthUserProvisioning(null, null, null);
        }
        return new AuthUserProvisioning(null, principal.email(), principal.displayName());
    }

}
