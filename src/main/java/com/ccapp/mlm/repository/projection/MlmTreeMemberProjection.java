package com.ccapp.mlm.repository.projection;

import java.time.OffsetDateTime;

public interface MlmTreeMemberProjection {

    String getMemberId();

    String getParentId();

    Integer getGeneration();

    OffsetDateTime getLinkedAt();
}
