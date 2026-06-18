package io.docpilot.common.json;

import io.docpilot.common.enums.BaseEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class JsonUtilsTest {

    @Test
    void objectMapperUsesMoonpxCompatibleRules() {
        LocalDateTime time = LocalDateTime.of(2026, 5, 26, 12, 0, 0);
        long epochSecond = time.atZone(ZoneId.systemDefault()).toEpochSecond();

        String json = JsonUtils.toJson(new SamplePayload(123L, time, SampleStatus.ENABLED));

        assertThat(json).contains("\"id\":\"123\"");
        assertThat(json).contains("\"createTime\":" + epochSecond);
        assertThat(json).contains("\"status\":1");

        SamplePayload payload = JsonUtils.convert("""
                {"id":"123","createTime":"2026-05-26 12:00:00","status":1,"ignored":"field"}
                """, SamplePayload.class);

        assertThat(payload.id()).isEqualTo(123L);
        assertThat(payload.createTime()).isEqualTo(time);
        assertThat(payload.status()).isEqualTo(SampleStatus.ENABLED);
    }

    @Test
    void numericLocalDateTimeUsesJvmDefaultTimeZone() {
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            LocalDateTime time = LocalDateTime.of(1970, 1, 1, 8, 0, 0);

            String json = JsonUtils.toJson(new SamplePayload(123L, time, SampleStatus.ENABLED));

            assertThat(json).contains("\"createTime\":0");
            SamplePayload payload = JsonUtils.convert("""
                    {"id":"123","createTime":0,"status":1}
                    """, SamplePayload.class);
            assertThat(payload.createTime()).isEqualTo(time);
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    private record SamplePayload(Long id, LocalDateTime createTime, SampleStatus status) {
    }

    private enum SampleStatus implements BaseEnum<Integer, SampleStatus> {
        ENABLED(1),
        DISABLED(0);

        private final Integer value;

        SampleStatus(Integer value) {
            this.value = value;
        }

        @Override
        public Integer getValue() {
            return value;
        }
    }

}
