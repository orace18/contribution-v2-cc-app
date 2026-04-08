package com.ccapp.prayer.dto;

import com.ccapp.prayer.domain.enums.PrayerOfficialVisibilityScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminCreatePrayerPostRequest(
        @NotBlank(message = "title is required")
        @Size(max = 160, message = "title max size is 160")
        String title,

        @NotBlank(message = "content is required")
        @Size(max = 4000, message = "content max size is 4000")
        String content,

        @NotNull(message = "visibility is required")
        PrayerOfficialVisibilityScope visibility,

        @Size(max = 3, message = "targetCountryCode max size is 3")
        String targetCountryCode,

        @Size(max = 128, message = "targetChurchRef max size is 128")
        String targetChurchRef,

        @Valid
        List<PrayerMediaAttachmentInput> mediaRefs,

        @Valid
        InternalDeepLinkRequest deepLink
) {
}
