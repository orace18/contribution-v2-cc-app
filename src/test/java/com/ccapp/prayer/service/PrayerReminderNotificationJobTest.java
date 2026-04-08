package com.ccapp.prayer.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.service.NotificationService;
import com.ccapp.prayer.domain.entity.PrayerDailyCompletion;
import com.ccapp.prayer.domain.entity.PrayerDailyContent;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerResponseType;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerStatus;
import com.ccapp.prayer.repository.PrayerDailyCompletionRepository;
import com.ccapp.prayer.repository.PrayerDailyContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrayerReminderNotificationJobTest {

    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private PrayerDailyContentRepository prayerDailyContentRepository;
    @Mock
    private PrayerDailyCompletionRepository prayerDailyCompletionRepository;
    @Mock
    private PrayerNotificationPublisher prayerNotificationPublisher;
    @Mock
    private NotificationService notificationService;

    private PrayerReminderNotificationJob prayerReminderNotificationJob;

    @BeforeEach
    void setUp() {
        prayerReminderNotificationJob = new PrayerReminderNotificationJob(
                accountHolderRepository,
                prayerDailyContentRepository,
                prayerDailyCompletionRepository,
                new PrayerTimezoneResolver(),
                prayerNotificationPublisher,
                notificationService
        );
    }

    @Test
    void processAccountHolder_shouldEmitStreakAtRiskSignalWhenYesterdayWasCompleted() {
        AccountHolder accountHolder = AccountHolder.builder()
                .id(UUID.randomUUID())
                .serviceCountryCode("BJ")
                .build();
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Porto-Novo"));
        PrayerDailyContent dailyPrayer = PrayerDailyContent.builder()
                .id(UUID.randomUUID())
                .prayerDate(today)
                .title("Priere du jour")
                .content("Continue ta marche.")
                .status(PrayerDailyPrayerStatus.PUBLISHED)
                .createdByAdminUserId(UUID.randomUUID())
                .build();

        when(prayerDailyContentRepository.findByPrayerDateAndStatus(today, PrayerDailyPrayerStatus.PUBLISHED))
                .thenReturn(Optional.of(dailyPrayer));
        when(prayerDailyCompletionRepository.findByAccountHolderIdAndPrayerDate(accountHolder.getId(), today))
                .thenReturn(Optional.empty());
        when(prayerDailyCompletionRepository.findByAccountHolderIdOrderByPrayerDateDesc(accountHolder.getId()))
                .thenReturn(List.of(
                        completion(accountHolder.getId(), dailyPrayer.getId(), today.minusDays(1)),
                        completion(accountHolder.getId(), dailyPrayer.getId(), today.minusDays(2))
                ));
        when(notificationService.existsNotificationForToday(
                accountHolder.getId(),
                NotificationType.PRAYER_STREAK_AT_RISK,
                "PRAYER_STREAK_AT_RISK",
                today
        )).thenReturn(false);

        prayerReminderNotificationJob.processAccountHolder(accountHolder);

        verify(prayerNotificationPublisher).publishStreakAtRisk(accountHolder.getId(), dailyPrayer, 2);
        verify(prayerNotificationPublisher, never()).publishDailyPrayerAvailable(accountHolder.getId(), dailyPrayer);
    }

    @Test
    void processAccountHolder_shouldEmitDailyPrayerAvailableWhenNoStreakRiskExists() {
        AccountHolder accountHolder = AccountHolder.builder()
                .id(UUID.randomUUID())
                .serviceCountryCode("BJ")
                .build();
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Porto-Novo"));
        PrayerDailyContent dailyPrayer = PrayerDailyContent.builder()
                .id(UUID.randomUUID())
                .prayerDate(today)
                .title("Priere du jour")
                .content("Nouvelle grace pour aujourd hui.")
                .status(PrayerDailyPrayerStatus.PUBLISHED)
                .createdByAdminUserId(UUID.randomUUID())
                .build();

        when(prayerDailyContentRepository.findByPrayerDateAndStatus(today, PrayerDailyPrayerStatus.PUBLISHED))
                .thenReturn(Optional.of(dailyPrayer));
        when(prayerDailyCompletionRepository.findByAccountHolderIdAndPrayerDate(accountHolder.getId(), today))
                .thenReturn(Optional.empty());
        when(prayerDailyCompletionRepository.findByAccountHolderIdOrderByPrayerDateDesc(accountHolder.getId()))
                .thenReturn(List.of(completion(accountHolder.getId(), dailyPrayer.getId(), today.minusDays(3))));
        when(notificationService.existsNotificationForToday(
                accountHolder.getId(),
                NotificationType.PRAYER_REMINDER,
                "PRAYER_DAILY_AVAILABLE",
                today
        )).thenReturn(false);

        prayerReminderNotificationJob.processAccountHolder(accountHolder);

        verify(prayerNotificationPublisher).publishDailyPrayerAvailable(accountHolder.getId(), dailyPrayer);
        verify(prayerNotificationPublisher, never()).publishStreakAtRisk(eq(accountHolder.getId()), eq(dailyPrayer), eq(1));
    }

    private PrayerDailyCompletion completion(UUID accountHolderId, UUID dailyPrayerId, LocalDate prayerDate) {
        return PrayerDailyCompletion.builder()
                .accountHolderId(accountHolderId)
                .dailyPrayerId(dailyPrayerId)
                .prayerDate(prayerDate)
                .timezone("Africa/Porto-Novo")
                .responseType(PrayerDailyPrayerResponseType.PRAYED)
                .build();
    }
}
