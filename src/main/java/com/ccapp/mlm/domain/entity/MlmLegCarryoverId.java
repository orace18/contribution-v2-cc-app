package com.ccapp.mlm.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class MlmLegCarryoverId implements Serializable {
    private UUID memberId;
    private UUID placementChildId;
    private Integer weekId;
}
