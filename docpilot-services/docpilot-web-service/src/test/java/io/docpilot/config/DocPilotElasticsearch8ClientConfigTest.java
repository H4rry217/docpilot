package io.docpilot.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.ElasticsearchTransport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DocPilotElasticsearch8ClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DocPilotElasticsearch8ClientConfig.class)
            .withPropertyValues("spring.elasticsearch.uris=http://localhost:9200");

    @Test
    void createsEs8ClientWhenLegacyRestClientIsAvailable() {
        assumeTrue(classPresent("org.elasticsearch.client.RestClient"));

        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ElasticsearchClient.class);
            assertThat(context).hasSingleBean(ElasticsearchTransport.class);
            assertThat(context).hasBean("docPilotElasticsearch8RestClient");
        });
    }

    private boolean classPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

}
