package io.docpilot.ai.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider-neutral embedding response.
 */
@Getter
@Setter
public class EmbeddingResponse implements AdditionalPropertiesCarrier {

    /**
     * Provider-facing model name used for the response.
     */
    private String model;

    /**
     * Embeddings returned in the same order as the request inputs.
     */
    private List<float[]> embeddings = new ArrayList<>();

    /**
     * Optional usage payload when reported by the provider.
     */
    private Object usage;

    /**
     * Extension fields preserved from provider responses.
     */
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

}
