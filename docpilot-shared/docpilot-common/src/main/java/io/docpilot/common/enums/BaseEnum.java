package io.docpilot.common.enums;

public interface BaseEnum<T, E extends Enum<?>> {

    T getValue();

}
