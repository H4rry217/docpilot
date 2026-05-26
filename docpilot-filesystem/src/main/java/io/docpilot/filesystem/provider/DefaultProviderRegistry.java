package io.docpilot.filesystem.provider;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultProviderRegistry implements ProviderRegistry {

    private final ConcurrentMap<String, FilesystemProvider> providers = new ConcurrentHashMap<>();

    public DefaultProviderRegistry() {
    }

    public DefaultProviderRegistry(Collection<FilesystemProvider> providers) {
        providers.forEach(this::register);
    }

    @Override
    public void register(FilesystemProvider provider) {
        providers.put(provider.providerId(), provider);
    }

    @Override
    public Optional<FilesystemProvider> findById(String providerId) {
        return Optional.ofNullable(providers.get(providerId));
    }

}
