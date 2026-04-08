package com.ccapp.prayer.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.service.NotificationService;
import com.ccapp.prayer.domain.entity.PrayerDailyCompletion;
import com.ccapp.prayer.domain.entity.PrayerDailyContent;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerStatus;
import com.ccapp.prayer.repository.PrayerDailyCompletionRepository;
import com.ccapp.prayer.repository.PrayerDailyContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class PrayerReminderNotificationJob {

    private final AccountHolderRepository accountHolderRepository;
    private final PrayerDailyContentRepository prayerDailyContentRepository;
    private final PrayerDailyCompletionRepository prayerDailyCompletionRepository;
    private final PrayerTimezoneResolver prayerTimezoneResolver;
    private final PrayerNotificationPublisher prayerNotificationPublisher;
    private final NotificationService notificationService;

    @Scheduled(cron = "${prayer.notifications.reminder.cron:0 0 */6 * * *}")
    @Transactional
    public void run() {
        for (AccountHolder accountHolder : accountHolderRepository.findAll()) {
            processAccountHolder(accountHolder);
        }
    }

    void processAccountHolder(AccountHolder accountHolder) {
        PrayerTimezoneResolver.PrayerLocalContext localContext = prayerTimezoneResolver.resolve(accountHolder, null);
        PrayerDailyContent dailyPrayer = prayerDailyContentRepository
                .findByPrayerDateAndStatus(localContext.localDate(), PrayerDailyPrayerStatus.PUBLISHED)
                .orElse(null);
        if (dailyPrayer == null) {
            return;
        }
        if (prayerDailyCompletionRepository.findByAccountHolderIdAndPrayerDate(accountHolder.getId(), localContext.localDate()).isPresent()) {
            return;
        }

        List<PrayerDailyCompletion> completions = prayerDailyCompletionRepository
                .findByAccountHolderIdOrderByPrayerDateDesc(accountHolder.getId());
        LocalDate lastCompletedDate = completions.isEmpty() ? null : completions.getFirst().getPrayerDate();

        if (lastCompletedDate != null && lastCompletedDate.equals(localContext.localDate().minusDays(1))) {
            if (!notificationService.existsNotificationForToday(
                    accountHolder.getId(),
                    NotificationType.PRAYER_STREAK_AT_RISK,
                    "PRAYER_STREAK_AT_RISK",
                    localContext.localDate())) {
                prayerNotificationPublisher.publishStreakAtRisk(
                        accountHolder.getId(),
                        dailyPrayer,
                        calculateCurrentStreak(completions, lastCompletedDate)
                );
            }
            return;
        }

        if (!notificationService.existsNotificationForToday(
                accountHolder.getId(),
                NotificationType.PRAYER_REMINDER,
                "PRAYER_DAILY_AVAILABLE",
                localContext.localDate())) {
            prayerNotificationPublisher.publishDailyPrayerAvailable(accountHolder.getId(), dailyPrayer);
        }
    }

    private int calculateCurrentStreak(List<PrayerDailyCompletion> completions, LocalDate lastCompletedDate) {
        Set<LocalDate> completionDates = new HashSet<>();
        completions.forEach(completion -> completionDates.add(completion.getPrayerDate()));

        int streak = 1;
        LocalDate cursor = lastCompletedDate;
        while (completionDates.contains(cursor.minusDays(1))) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
