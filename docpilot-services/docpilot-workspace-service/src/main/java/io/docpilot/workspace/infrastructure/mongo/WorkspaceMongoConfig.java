package io.docpilot.workspace.infrastructure.mongo;

import io.docpilot.common.enums.BaseEnum;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.List;
import java.util.Set;

@Configuration
public class WorkspaceMongoConfig {

    @Bean
    public static BeanPostProcessor workspaceMongoTypeMapperCustomizer() {
        return new BeanPostProcessor() {

            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof MappingMongoConverter converter) {
                    converter.setTypeMapper(new DefaultMongoTypeMapper(null));
                }
                return bean;
            }

        };
    }

    @Bean
    public MongoCustomConversions workspaceMongoCustomConversions() {
        return new MongoCustomConversions(List.of(new BaseEnumMongoConverter()));
    }

    private static class BaseEnumMongoConverter implements GenericConverter {

        private static final Set<ConvertiblePair> CONVERTIBLE_PAIRS = Set.of(
                new ConvertiblePair(BaseEnum.class, Integer.class),
                new ConvertiblePair(Integer.class, BaseEnum.class)
        );

        @Override
        public Set<ConvertiblePair> getConvertibleTypes() {
            return CONVERTIBLE_PAIRS;
        }

        @Override
        public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
            if (source instanceof BaseEnum<?, ?> baseEnum) {
                return baseEnum.getValue();
            }
            if (source instanceof Integer value && BaseEnum.class.isAssignableFrom(targetType.getType())) {
                return enumByValue(targetType.getType(), value);
            }
            return source;
        }

    }

    private static Object enumByValue(Class<?> enumType, Integer value) {
        if (value == null) {
            return null;
        }
        for (Object candidate : enumType.getEnumConstants()) {
            if (candidate instanceof BaseEnum<?, ?> baseEnum && value.equals(baseEnum.getValue())) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("No matching enum value " + value + " for " + enumType.getName());
    }

}
