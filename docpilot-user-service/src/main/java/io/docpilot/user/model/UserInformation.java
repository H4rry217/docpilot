package io.docpilot.user.model;

import lombok.Getter;
import lombok.Setter;

/**
 * User information exposed to other DocPilot modules.
 */
@Getter
@Setter
public class UserInformation {

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
