package io.docpilot.common.web.config;

import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.auth.AuthSubjectContext;
import io.docpilot.common.web.auth.AuthSubjectResolver;
import io.docpilot.common.web.auth.BearerJwtAuthSubjectResolver;
import io.docpilot.common.web.auth.DocPilotJwtProperties;
import io.docpilot.common.web.auth.RequireAuthInterceptor;
import io.docpilot.common.web.filter.RequestLoggingFilter;
import io.docpilot.common.web.filter.RequestTraceFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

@Configuration
@EnableConfigurationProperties(DocPilotJwtProperties.class)
public class DocPilotWebCommonConfig {

    @Bean
    @ConditionalOnMissingBean
    public AuthContextProvider authContextProvider() {
        return AuthSubjectContext::currentSubject;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthSubjectResolver authSubjectResolver(DocPilotJwtProperties properties) {
        return new BearerJwtAuthSubjectResolver(properties);
    }

    @Bean
    public RequireAuthInterceptor requireAuthInterceptor(AuthSubjectResolver authSubjectResolver) {
        return new RequireAuthInterceptor(authSubjectResolver);
    }

    @Bean
    public RequestTraceFilter requestTraceFilter() {
        return new RequestTraceFilter();
    }

    @Bean
    public RequestLoggingFilter requestLoggingFilter() {
        return new RequestLoggingFilter();
    }

    @Bean
    public org.springframework.web.servlet.config.annotation.WebMvcConfigurer requireAuthWebMvcConfigurer(
            RequireAuthInterceptor requireAuthInterceptor) {
        return new org.springframework.web.servlet.config.annotation.WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(requireAuthInterceptor)
                        .addPathPatterns("/**");
            }
        };
    }

}
