package io.docpilot.auth;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generic provider capabilities exposed to API clients.
 */
public record AuthProviderCapabilities(
        Set<AuthLoginFlow> loginFlows,
        Set<AuthAccountAction> accountActions
) {

    public AuthProviderCapabilities {
        loginFlows = immutableCopy(loginFlows);
        accountActions = immutableCopy(accountActions);
    }

    public boolean hasLoginFlow(AuthLoginFlow flow) {
        return loginFlows.contains(flow);
    }

    public boolean hasAccountAction(AuthAccountAction action) {
        return accountActions.contains(action);
    }

    private static <T> Set<T> immutableCopy(Set<T> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

}
