package com.ccapp.prayer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record AdminUpdateDailyPrayerRequest(
        LocalDate prayerDate,

        @Size(max = 160, message = "title max size is 160")
        String title,

        @Size(max = 160, message = "scriptureReference max size is 160")
        String scriptureReference,

        @Size(max = 4000, message = "content max size is 4000")
        String content,

        @Valid
        List<PrayerMediaAttachmentInput> mediaRefs
) {
}
