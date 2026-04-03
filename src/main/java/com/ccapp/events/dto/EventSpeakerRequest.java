package com.ccapp.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EventSpeakerRequest(
        @NotBlank(message = "speaker name is required")
        @Size(max = 255, message = "speaker name max size is 255")
        String name,

        @Size(max = 255, message = "speaker title max size is 255")
        String title,

        @Size(max = 512, message = "speaker photo max size is 512")
        String photo
) {
}
