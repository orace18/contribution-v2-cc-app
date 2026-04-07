package com.ccapp.prayer.service;

import com.ccapp.prayer.dto.AdminCreateDailyPrayerRequest;
import com.ccapp.prayer.dto.AdminCreatePrayerPostRequest;
import com.ccapp.prayer.dto.CreatePrayerCommentRequest;
import com.ccapp.prayer.dto.CreatePrayerRequest;
import com.ccapp.prayer.exception.PrayerPhase1NotImplementedException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrayerPhase1SkeletonServiceTest {

    private final PrayerMobileService prayerMobileService = new PrayerMobileService();
    private final PrayerAdminPostService prayerAdminPostService = new PrayerAdminPostService();
    private final PrayerAdminDailyPrayerService prayerAdminDailyPrayerService = new PrayerAdminDailyPrayerService();

    @Test
    void prayerMobileService_shouldExposeExplicitNotImplementedContract() {
        PrayerPhase1NotImplementedException exception = assertThrows(
                PrayerPhase1NotImplementedException.class,
                () -> prayerMobileService.getPrayerFeed(UUID.randomUUID(), UUID.randomUUID(), 0, 20, true)
        );
        assertEquals("PRAYER_PHASE1_NOT_IMPLEMENTED", exception.getMessage());

        assertEquals(
                "PRAYER_PHASE1_NOT_IMPLEMENTED",
                assertThrows(
                        PrayerPhase1NotImplementedException.class,
                        () -> prayerMobileService.createPrayerRequest(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                new CreatePrayerRequest(null, "content", null, List.of())
                        )
                ).getMessage()
        );

        assertEquals(
                "PRAYER_PHASE1_NOT_IMPLEMENTED",
                assertThrows(
                        PrayerPhase1NotImplementedException.class,
                        () -> prayerMobileService.createPrayerComment(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                new CreatePrayerCommentRequest(null, "comment")
                        )
                ).getMessage()
        );
    }

    @Test
    void prayerAdminPostService_shouldExposeExplicitNotImplementedContract() {
        PrayerPhase1NotImplementedException exception = assertThrows(
                PrayerPhase1NotImplementedException.class,
                () -> prayerAdminPostService.createPost(
                        UUID.randomUUID(),
                        new AdminCreatePrayerPostRequest("Title", "Content", null, null, null, List.of(), null)
                )
        );
        assertEquals("PRAYER_PHASE1_NOT_IMPLEMENTED", exception.getMessage());
    }

    @Test
    void prayerAdminDailyPrayerService_shouldExposeExplicitNotImplementedContract() {
        PrayerPhase1NotImplementedException exception = assertThrows(
                PrayerPhase1NotImplementedException.class,
                () -> prayerAdminDailyPrayerService.createDailyPrayer(
                        UUID.randomUUID(),
                        new AdminCreateDailyPrayerRequest(LocalDate.of(2026, 4, 7), null, null, "Content", List.of())
                )
        );
        assertEquals("PRAYER_PHASE1_NOT_IMPLEMENTED", exception.getMessage());
    }
}
