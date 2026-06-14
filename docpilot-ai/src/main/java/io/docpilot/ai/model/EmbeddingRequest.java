package io.docpilot.ai.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider-neutral embedding request.
 */
@Getter
@Setter
public class EmbeddingRequest {

    /**
     * Optional provider-facing model override.
     */
    private String model;

    /**
     * Text inputs to embed in order.
     */
    private List<String> inputs = new ArrayList<>();

    /**
     * Output vector dimensions requested from providers that support configurable embeddings.
     */
    private Integer dimensions;

    /**
     * Provider-specific options merged into the wire payload by adapters.
     */
    private final Map<String, Object> options = new LinkedHashMap<>();

    public EmbeddingRequest copy() {
        EmbeddingRequest copy = new EmbeddingRequest();
        copy.setModel(model);
        copy.setInputs(inputs == null ? null : new ArrayList<>(inputs));
        copy.setDimensions(dimensions);
        copy.getOptions().putAll(options);
        return copy;
    }

    public void setOption(String name, Object value) {
        if (value == null) {
            options.remove(name);
            return;
        }
        options.put(name, value);
    }

}
