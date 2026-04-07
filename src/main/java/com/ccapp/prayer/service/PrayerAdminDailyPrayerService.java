package com.ccapp.prayer.service;

import com.ccapp.common.api.PageResponse;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerStatus;
import com.ccapp.prayer.dto.AdminCreateDailyPrayerRequest;
import com.ccapp.prayer.dto.AdminDailyPrayerResponse;
import com.ccapp.prayer.dto.AdminUpdateDailyPrayerRequest;
import com.ccapp.prayer.exception.PrayerPhase1NotImplementedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class PrayerAdminDailyPrayerService {

    private static final String NOT_IMPLEMENTED_MESSAGE = "PRAYER_PHASE1_NOT_IMPLEMENTED";

    public PageResponse<AdminDailyPrayerResponse> listDailyPrayers(int page,
                                                                   int size,
                                                                   LocalDate fromDate,
                                                                   LocalDate toDate,
                                                                   PrayerDailyPrayerStatus status) {
        throw notImplemented();
    }

    public AdminDailyPrayerResponse createDailyPrayer(UUID adminUserId, AdminCreateDailyPrayerRequest request) {
        throw notImplemented();
    }

    public AdminDailyPrayerResponse getDailyPrayer(UUID dailyPrayerId) {
        throw notImplemented();
    }

    public AdminDailyPrayerResponse updateDailyPrayer(UUID adminUserId,
                                                      UUID dailyPrayerId,
                                                      AdminUpdateDailyPrayerRequest request) {
        throw notImplemented();
    }

    public AdminDailyPrayerResponse publishDailyPrayer(UUID adminUserId, UUID dailyPrayerId) {
        throw notImplemented();
    }

    public AdminDailyPrayerResponse archiveDailyPrayer(UUID adminUserId, UUID dailyPrayerId) {
        throw notImplemented();
    }

    private PrayerPhase1NotImplementedException notImplemented() {
        return new PrayerPhase1NotImplementedException(NOT_IMPLEMENTED_MESSAGE);
    }
}
