package io.docpilot.workspace.processing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceIdCodecTest {

    private final WorkspaceIdCodec codec = new WorkspaceIdCodec();

    @Test
    void parsesAndFormatsPositiveLongIds() {
        assertThat(codec.parseRequired("123", "workspaceId")).isEqualTo(123L);
        assertThat(codec.format(123L)).isEqualTo("123");
    }

    @Test
    void rejectsInvalidRequiredIds() {
        assertThatThrownBy(() -> codec.parseRequired(null, "workspaceId"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.parseRequired("abc", "workspaceId"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.parseRequired("-1", "workspaceId"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.parseRequired("0", "workspaceId"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.parseRequired("9223372036854775808", "workspaceId"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
