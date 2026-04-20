package com.ccapp.circles.dto;

import com.ccapp.circles.domain.enums.CircleMemberRole;
import jakarta.validation.constraints.NotNull;

public record UpdateCircleMemberRoleRequest(
        @NotNull CircleMemberRole role
) {
}
