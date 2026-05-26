package io.docpilot.filesystem.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Maps a workspace virtual path to a provider path root.
 */
@Getter
@Setter
public class PathMapping {

    private String mappingId;
    private String workspaceId;
    private String virtualPath;
    private String providerId;
    private String providerRoot;
    private boolean readonly;
    private boolean enabled = true;
    private Instant createTime;
    private Instant updateTime;

}
