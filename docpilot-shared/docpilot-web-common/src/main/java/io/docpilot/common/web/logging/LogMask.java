package io.docpilot.common.web.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface LogMask {

    Mode mode() default Mode.MASK;

    String maskText() default "";

    enum Mode {
        MASK,
        OMIT
    }

}
