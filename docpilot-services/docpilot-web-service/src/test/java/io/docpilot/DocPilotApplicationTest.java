package io.docpilot;

import io.docpilot.filesystem.provider.ProviderRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DocPilotApplicationTest {

    @Autowired
    private ProviderRegistry providerRegistry;

    @Test
    void contextLoads() {
    }

    @Test
    void filesystemDefaultsToLocalProvider() {
        assertThat(providerRegistry.findById("local")).isPresent();
    }

}
