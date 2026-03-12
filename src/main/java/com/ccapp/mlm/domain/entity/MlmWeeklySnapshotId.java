package com.ccapp.mlm.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class MlmWeeklySnapshotId implements Serializable {
    private Integer weekId;
    private UUID accountHolderId;
}
