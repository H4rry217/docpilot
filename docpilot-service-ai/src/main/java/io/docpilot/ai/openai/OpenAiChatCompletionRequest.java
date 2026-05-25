package io.docpilot.ai.openai;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
public class OpenAiChatCompletionRequest {

    private String model;
    private List<OpenAiChatMessage> messages = new ArrayList<>();
    private Double temperature;
    @JsonProperty("top_p")
    private Double topP;
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    private Boolean stream;
    private Object stop;
    @Getter(onMethod_ = @JsonAnyGetter)
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        additionalProperties.put(name, value);
    }

}
