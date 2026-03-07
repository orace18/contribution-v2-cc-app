package com.ccapp.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AdminSetupTwoFactorRequest(
        @NotBlank String challengeToken,
        @Pattern(regexp = "^$|\\d{6}") String code
) {
}
