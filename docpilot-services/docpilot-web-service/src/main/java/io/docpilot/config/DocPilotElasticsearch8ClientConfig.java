package io.docpilot.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.ElasticsearchTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch 8 Java client compatibility wiring for ES 8 clusters.
 */
@Configuration
@ConditionalOnClass(name = "org.elasticsearch.client.RestClient")
@ConditionalOnMissingBean(ElasticsearchClient.class)
@EnableConfigurationProperties(DocPilotElasticsearch8ClientConfig.SpringElasticsearchProperties.class)
public class DocPilotElasticsearch8ClientConfig {

    private static final Logger log = LoggerFactory.getLogger(DocPilotElasticsearch8ClientConfig.class);

    private static final String ES8_PROFILE_HINT = "Elasticsearch 8 client compatibility requires "
            + "elasticsearch-java 8.15.5 on the classpath. Pin elasticsearch-client.version=8.15.5.";

    @Bean(destroyMethod = "close")
    public Object docPilotElasticsearch8RestClient(SpringElasticsearchProperties properties) {
        log.info("docpilot elasticsearch client mode=es8(classpath) uris={}", properties.getUris());
        try {
            Class<?> restClientClass = requiredClass("org.elasticsearch.client.RestClient");
            Object hosts = httpHosts(properties);
            Method builderMethod = restClientClass.getMethod("builder", hosts.getClass());
            Object builder = builderMethod.invoke(null, hosts);
            if (StringUtils.hasText(properties.getUsername())) {
                configureBasicAuth(builder, properties);
            }
            return builder.getClass().getMethod("build").invoke(builder);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create Elasticsearch 8 RestClient. " + ES8_PROFILE_HINT,
                    exception);
        }
    }

    @Bean(destroyMethod = "close")
    @Primary
    public ElasticsearchTransport docPilotElasticsearch8Transport(
            @Qualifier("docPilotElasticsearch8RestClient") Object restClient) {
        try {
            Class<?> restClientClass = requiredClass("org.elasticsearch.client.RestClient");
            Class<?> jsonpMapperClass = requiredClass("co.elastic.clients.json.JsonpMapper");
            Class<?> jacksonJsonpMapperClass = requiredClass("co.elastic.clients.json.jackson.JacksonJsonpMapper");
            Class<?> transportClass = requiredClass("co.elastic.clients.transport.rest_client.RestClientTransport");
            Object mapper = jacksonJsonpMapperClass.getConstructor().newInstance();
            Object transport = transportClass.getConstructor(restClientClass, jsonpMapperClass)
                    .newInstance(restClient, mapper);
            return (ElasticsearchTransport) transport;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create Elasticsearch 8 transport. " + ES8_PROFILE_HINT,
                    exception);
        }
    }

    @Bean
    @Primary
    public ElasticsearchClient docPilotElasticsearch8Client(
            @Qualifier("docPilotElasticsearch8Transport") ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }

    private Object httpHosts(SpringElasticsearchProperties properties) throws ReflectiveOperationException {
        Class<?> httpHostClass = requiredClass("org.apache.http.HttpHost");
        Method createMethod = httpHostClass.getMethod("create", String.class);
        List<String> uris = CollectionUtils.isEmpty(properties.getUris())
                ? List.of("http://localhost:9200")
                : properties.getUris();
        Object hosts = Array.newInstance(httpHostClass, uris.size());
        for (int index = 0; index < uris.size(); index++) {
            Array.set(hosts, index, createMethod.invoke(null, uris.get(index)));
        }
        return hosts;
    }

    private void configureBasicAuth(Object builder, SpringElasticsearchProperties properties)
            throws ReflectiveOperationException {
        Class<?> authScopeClass = requiredClass("org.apache.http.auth.AuthScope");
        Class<?> credentialsClass = requiredClass("org.apache.http.auth.Credentials");
        Class<?> credentialsProviderClass = requiredClass("org.apache.http.client.CredentialsProvider");
        Class<?> basicCredentialsProviderClass = requiredClass("org.apache.http.impl.client.BasicCredentialsProvider");
        Class<?> usernamePasswordCredentialsClass = requiredClass("org.apache.http.auth.UsernamePasswordCredentials");

        Object credentialsProvider = basicCredentialsProviderClass.getConstructor().newInstance();
        Object credentials = usernamePasswordCredentialsClass.getConstructor(String.class, String.class)
                .newInstance(properties.getUsername(), properties.getPassword());
        Object anyAuthScope = authScopeClass.getField("ANY").get(null);
        credentialsProvider.getClass()
                .getMethod("setCredentials", authScopeClass, credentialsClass)
                .invoke(credentialsProvider, anyAuthScope, credentials);

        Class<?> callbackClass = requiredClass("org.elasticsearch.client.RestClientBuilder$HttpClientConfigCallback");
        Object callback = Proxy.newProxyInstance(
                callbackClass.getClassLoader(),
                new Class<?>[]{callbackClass},
                httpClientConfigCallback(credentialsProviderClass, credentialsProvider));
        builder.getClass()
                .getMethod("setHttpClientConfigCallback", callbackClass)
                .invoke(builder, callback);
    }

    private InvocationHandler httpClientConfigCallback(Class<?> credentialsProviderClass, Object credentialsProvider) {
        return (proxy, method, arguments) -> {
            if ("customizeHttpClient".equals(method.getName()) && arguments != null && arguments.length == 1) {
                Object httpClientBuilder = arguments[0];
                httpClientBuilder.getClass()
                        .getMethod("setDefaultCredentialsProvider", credentialsProviderClass)
                        .invoke(httpClientBuilder, credentialsProvider);
                return httpClientBuilder;
            }
            if ("toString".equals(method.getName())) {
                return "DocPilotElasticsearch8HttpClientConfigCallback";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == arguments[0];
            }
            return null;
        };
    }

    private Class<?> requiredClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Missing class " + className + ". " + ES8_PROFILE_HINT, exception);
        }
    }

    @ConfigurationProperties(prefix = "spring.elasticsearch")
    public static class SpringElasticsearchProperties {

        private List<String> uris = new ArrayList<>(List.of("http://localhost:9200"));

        private String username;

        private String password = "";

        public List<String> getUris() {
            return uris;
        }

        public void setUris(List<String> uris) {
            this.uris = uris;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

    }

}
