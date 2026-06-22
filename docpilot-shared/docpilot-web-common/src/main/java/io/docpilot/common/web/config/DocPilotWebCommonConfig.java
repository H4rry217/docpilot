package io.docpilot.common.web.config;

import tools.jackson.databind.ObjectMapper;
import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.auth.AuthSubjectContext;
import io.docpilot.common.domain.BaseEntityAuditor;
import io.docpilot.common.web.auth.AuthSubjectResolver;
import io.docpilot.common.web.auth.BearerJwtAuthSubjectResolver;
import io.docpilot.common.web.auth.DocPilotJwtConfig;
import io.docpilot.common.web.auth.JwtTokenVerifier;
import io.docpilot.common.web.auth.RequireAuthInterceptor;
import io.docpilot.common.web.filter.RequestLoggingFilter;
import io.docpilot.common.web.filter.RequestLoggingConfig;
import io.docpilot.common.web.filter.RequestTraceFilter;
import io.docpilot.common.web.logging.MdcTaskDecorator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

@Configuration
@EnableConfigurationProperties({DocPilotJwtConfig.class, RequestLoggingConfig.class})
public class DocPilotWebCommonConfig {

    @Bean
    @ConditionalOnMissingBean
    public AuthContextProvider authContextProvider() {
        return AuthSubjectContext::currentSubject;
    }

    @Bean
    public InitializingBean baseEntityAuditorInitializer(AuthContextProvider authContextProvider) {
        return () -> BaseEntityAuditor.setAuditorSupplier(() -> authContextProvider.currentSubject()
                .map(subject -> new BaseEntityAuditor.Auditor(subject.getUserId(), auditorName(subject))));
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenVerifier jwtTokenVerifier(DocPilotJwtConfig config) {
        return new JwtTokenVerifier(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthSubjectResolver authSubjectResolver(JwtTokenVerifier jwtTokenVerifier) {
        return new BearerJwtAuthSubjectResolver(jwtTokenVerifier);
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
    public RequestLoggingFilter requestLoggingFilter(ObjectMapper objectMapper, RequestLoggingConfig config) {
        return new RequestLoggingFilter(objectMapper, config);
    }

    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    @Bean
    public ThreadPoolTaskExecutorCustomizer mdcThreadPoolTaskExecutorCustomizer(TaskDecorator taskDecorator) {
        return executor -> executor.setTaskDecorator(taskDecorator);
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

    private String auditorName(AuthSubject subject) {
        if (subject.getDisplayName() != null && !subject.getDisplayName().isBlank()) {
            return subject.getDisplayName();
        }
        return subject.getUserId() == null ? null : String.valueOf(subject.getUserId());
    }

}
