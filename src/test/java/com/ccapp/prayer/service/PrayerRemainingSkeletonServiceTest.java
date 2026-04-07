package com.ccapp.prayer.service;

import com.ccapp.prayer.dto.AdminCreateDailyPrayerRequest;
import com.ccapp.prayer.exception.PrayerPhase1NotImplementedException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrayerRemainingSkeletonServiceTest {

    private final PrayerAdminDailyPrayerService prayerAdminDailyPrayerService = new PrayerAdminDailyPrayerService();

    @Test
    void adminDailyPrayerService_shouldRemainExplicitlyUnimplementedInPhase2() {
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
