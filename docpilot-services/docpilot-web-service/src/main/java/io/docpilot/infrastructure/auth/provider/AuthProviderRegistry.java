package io.docpilot.infrastructure.auth.provider;

import io.docpilot.auth.AuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fail-fast registry for the single active authentication provider.
 */
public class AuthProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(AuthProviderRegistry.class);

    private final String currentProviderId;
    private final Map<String, AuthProvider> providerById;

    public AuthProviderRegistry(AuthProviderProperties properties, List<AuthProvider> providers) {
        currentProviderId = StringUtils.hasText(properties.getProvider())
                ? properties.getProvider().strip()
                : DefaultUserAuthProvider.PROVIDER_ID;
        providerById = new LinkedHashMap<>();
        for (AuthProvider provider : providers) {
            String providerId = provider.providerId();
            if (!StringUtils.hasText(providerId)) {
                throw new IllegalStateException("Auth provider id is required");
            }
            // Provider ids are deployment configuration, so ambiguity should fail at startup.
            AuthProvider existing = providerById.putIfAbsent(providerId, provider);
            if (existing != null) {
                throw new IllegalStateException("Duplicate auth provider id: " + providerId);
            }
        }
        if (!providerById.containsKey(currentProviderId)) {
            throw new IllegalStateException("Configured auth provider was not found: " + currentProviderId);
        }
        log.info("auth provider registry initialized currentProviderId={} providerIds={}",
                currentProviderId, providerById.keySet());
    }

    public AuthProvider currentProvider() {
        return providerById.get(currentProviderId);
    }

    public String currentProviderId() {
        return currentProviderId;
    }

}
