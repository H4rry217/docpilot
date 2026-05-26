package io.docpilot.common.constant;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class DocPilotConstants {

    public static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");

    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

}
