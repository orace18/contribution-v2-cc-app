package com.ccapp.prayer.dto;

import com.ccapp.prayer.domain.enums.PrayerVisibilityScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreatePrayerRequest(
        UUID clientRequestId,

        @NotBlank(message = "content is required")
        @Size(max = 2000, message = "content max size is 2000")
        String content,

        @NotNull(message = "visibility is required")
        PrayerVisibilityScope visibility,

        @Valid
        List<PrayerMediaAttachmentInput> mediaRefs
) {
}
