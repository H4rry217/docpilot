package io.docpilot.common.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Common database entity metadata.
 */
@Getter
@Setter
public class BaseEntity {

    private Long id;

    private LocalDateTime createTime;

    private String createBy;

    private Long creatorId;

    private LocalDateTime updateTime;

    private String updateBy;

    private Long updaterId;

    private Boolean isDeleted;

}
