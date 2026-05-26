package io.docpilot.common.context;

import java.util.UUID;

public final class RequestIdGenerator {

    public static String nextId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

}
