package com.ccapp.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WalletPinSetupRequest(
        @Pattern(regexp = "\\d{4,8}", message = "Current PIN must be 4 to 8 digits")
        String currentPin,

        @NotBlank
        @Pattern(regexp = "\\d{4,8}", message = "New PIN must be 4 to 8 digits")
        String newPin,

        @NotBlank
        @Pattern(regexp = "\\d{4,8}", message = "PIN confirmation must be 4 to 8 digits")
        String confirmPin
) {
}
