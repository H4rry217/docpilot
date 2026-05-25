package io.docpilot.ai.openai;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
public class OpenAiChatDelta {

    private String role;
    private Object content;
    @JsonProperty("tool_calls")
    private Object toolCalls;
    @Getter(onMethod_ = @JsonAnyGetter)
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        additionalProperties.put(name, value);
    }

}
