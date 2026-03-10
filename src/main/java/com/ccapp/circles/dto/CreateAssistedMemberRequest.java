package com.ccapp.circles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAssistedMemberRequest(
        @NotBlank(message = "fullName is required")
        @Size(max = 240, message = "fullName max size is 240")
        String fullName,

        @NotBlank(message = "phoneNumber is required")
        @Size(max = 32, message = "phoneNumber max size is 32")
        String phoneNumber,

        @NotBlank(message = "city is required")
        @Size(max = 120, message = "city max size is 120")
        String city,

        @NotBlank(message = "activityType is required")
        @Size(max = 64, message = "activityType max size is 64")
        String activityType,

        @NotBlank(message = "projectDescription is required")
        @Size(max = 1000, message = "projectDescription max size is 1000")
        String projectDescription
) {
}
