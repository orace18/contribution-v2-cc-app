package com.ccapp.circles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCircleRequest(
        @NotBlank(message = "name is required")
        @Size(max = 128, message = "name max size is 128")
        String name
) {
}
