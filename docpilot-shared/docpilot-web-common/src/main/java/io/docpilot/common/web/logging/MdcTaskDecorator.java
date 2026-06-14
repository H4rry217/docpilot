package io.docpilot.common.web.logging;

import io.docpilot.common.context.RequestConstants;
import io.docpilot.common.context.RequestIdGenerator;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Propagates MDC context to Spring-managed asynchronous tasks.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> parentContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previousContext = MDC.getCopyOfContextMap();
            try {
                apply(parentContext);
                runnable.run();
            } finally {
                restore(previousContext);
            }
        };
    }

    private void apply(Map<String, String> context) {
        MDC.clear();
        if (context != null && !context.isEmpty()) {
            MDC.setContextMap(context);
        }
        if (MDC.get(RequestConstants.KEY_TRACE_ID) == null || MDC.get(RequestConstants.KEY_TRACE_ID).isBlank()) {
            MDC.put(RequestConstants.KEY_TRACE_ID, RequestIdGenerator.nextId());
        }
    }

    private void restore(Map<String, String> context) {
        MDC.clear();
        if (context != null && !context.isEmpty()) {
            MDC.setContextMap(context);
        }
    }

}
