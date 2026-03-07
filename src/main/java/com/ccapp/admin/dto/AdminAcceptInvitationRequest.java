package com.ccapp.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminAcceptInvitationRequest(
        @NotBlank String invitationToken,
        @NotBlank @Size(min = 8, max = 128) String password
) {
}
