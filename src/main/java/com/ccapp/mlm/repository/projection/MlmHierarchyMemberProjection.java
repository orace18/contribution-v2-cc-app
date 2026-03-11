package com.ccapp.mlm.repository.projection;

import java.time.OffsetDateTime;

public interface MlmHierarchyMemberProjection {
    String getMemberId();

    Integer getLevel();

    String getFirstName();

    String getLastName();

    OffsetDateTime getLinkedAt();
}
