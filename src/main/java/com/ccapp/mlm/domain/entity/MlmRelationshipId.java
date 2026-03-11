package com.ccapp.mlm.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MlmRelationshipId implements Serializable {

    private UUID ancestorId;
    private UUID descendantId;
}
