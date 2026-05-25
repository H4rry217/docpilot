package io.docpilot.ai.openai;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
public class OpenAiChatCompletionResponse {

    private String id;
    private String object;
    private Long created;
    private String model;
    private List<OpenAiChoice> choices = new ArrayList<>();
    private OpenAiUsage usage;
    @Getter(onMethod_ = @JsonAnyGetter)
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        additionalProperties.put(name, value);
    }

}
