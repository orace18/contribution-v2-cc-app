package com.ccapp.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record EventBatchCheckinItemRequest(
        @Size(max = 64, message = "clientScanId max size is 64")
        String clientScanId,

        @NotBlank(message = "qrCode is required")
        String qrCode,

        OffsetDateTime scannedAt,

        @Size(max = 128, message = "sourceDeviceId max size is 128")
        String sourceDeviceId
) {
}
