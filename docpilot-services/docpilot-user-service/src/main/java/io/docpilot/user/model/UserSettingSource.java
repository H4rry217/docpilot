package io.docpilot.user.model;

/**
 * Source of an effective user setting value.
 */
public enum UserSettingSource {

    /**
     * Value explicitly saved by the user.
     */
    USER,

    /**
     * Built-in value used when the user has not saved this setting.
     */
    DEFAULT

}
