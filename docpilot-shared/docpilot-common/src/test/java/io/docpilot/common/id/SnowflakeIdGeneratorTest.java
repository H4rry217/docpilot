package io.docpilot.common.id;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdGeneratorTest {

    @Test
    void generatesPositiveUniqueIncreasingIdsInSingleProcess() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        List<Long> ids = new ArrayList<>();

        for (int i = 0; i < 1000; i++) {
            ids.add(generator.nextId());
        }

        assertThat(ids).allMatch(id -> id > 0);
        assertThat(new HashSet<>(ids)).hasSameSizeAs(ids);
        assertThat(ids).isSorted();
    }

    @Test
    void rejectsOutOfRangeDatacenterAndMachineIds() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(-1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnowflakeIdGenerator(32, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnowflakeIdGenerator(1, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnowflakeIdGenerator(1, 32))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
