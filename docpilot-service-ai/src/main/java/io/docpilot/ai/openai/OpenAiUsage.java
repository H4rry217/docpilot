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
public class OpenAiUsage {

    @JsonProperty("prompt_tokens")
    private Integer promptTokens;
    @JsonProperty("completion_tokens")
    private Integer completionTokens;
    @JsonProperty("total_tokens")
    private Integer totalTokens;
    @JsonProperty("prompt_tokens_details")
    private Object promptTokensDetails;
    @JsonProperty("completion_tokens_details")
    private Object completionTokensDetails;
    @Getter(onMethod_ = @JsonAnyGetter)
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        additionalProperties.put(name, value);
    }

}
