package io.docpilot.ai.model;

import java.util.Map;

/**
 * Common contract for provider-neutral models that preserve extension fields.
 */
public interface AdditionalPropertiesCarrier {

    /**
     * Returns mutable extension fields that are not part of the stable core model.
     */
    Map<String, Object> getAdditionalProperties();

    /**
     * Adds or removes one extension field.
     */
    default void setAdditionalProperty(String name, Object value) {
        if (value == null) {
            getAdditionalProperties().remove(name);
            return;
        }
        getAdditionalProperties().put(name, value);
    }

}
