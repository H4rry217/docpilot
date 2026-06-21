package io.docpilot.auth;

/**
 * User-facing entry points that an authentication provider can offer.
 */
public enum AuthLoginFlow {
    PASSWORD_FORM,
    HOST_TOKEN,
    REMOTE_USER
}
