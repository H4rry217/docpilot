package io.docpilot.document.user;

import lombok.Getter;
import lombok.Setter;

/**
 * User information required by the document module.
 */
@Getter
@Setter
public class UserProfile {

    /**
     * Stable user id.
     */
    private String userId;

    /**
     * User-facing display name.
     */
    private String displayName;

    /**
     * Email address when the user layer chooses to expose it.
     */
    private String email;

}
