package com.ccapp.mlm.scheduler;

import com.ccapp.mlm.domain.entity.MlmWeeklyBonusJobRun;
import com.ccapp.mlm.domain.enums.WeeklyBonusJobRunStatus;
import com.ccapp.mlm.repository.MlmWeeklyBonusJobRunRepository;
import com.ccapp.observability.PlatformMonitoringHooks;
import com.ccapp.mlm.service.WeeklyGroupBonusProcessingResult;
import com.ccapp.mlm.service.WeeklyGroupBonusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.IsoFields;

@Component
@RequiredArgsConstructor
@Slf4j
public class WeeklyGroupBonusJob {

    private final WeeklyGroupBonusService weeklyGroupBonusService;
    private final MlmWeeklyBonusJobRunRepository mlmWeeklyBonusJobRunRepository;

    @Value("${mlm.group-bonus.zone:Africa/Porto-Novo}")
    private String groupBonusZone;

    @Scheduled(cron = "${mlm.group-bonus.cron:0 55 23 * * SUN}", zone = "${mlm.group-bonus.zone:Africa/Porto-Novo}")
    public void run() {
        OffsetDateTime startedAt = OffsetDateTime.now();
        try {
            WeeklyGroupBonusProcessingResult result = weeklyGroupBonusService.processCurrentWeek();
            OffsetDateTime finishedAt = OffsetDateTime.now();
            saveRun(result.weekId(), WeeklyBonusJobRunStatus.SUCCESS, startedAt, finishedAt,
                    result.membersProcessed(), result.commissionsCreated(), result.carryoversStored(), null);
            PlatformMonitoringHooks.onWeeklyBonusJob(
                    WeeklyBonusJobRunStatus.SUCCESS.name(),
                    result.membersProcessed(),
                    result.commissionsCreated(),
                    Duration.between(startedAt, finishedAt)
            );
            log.info("weekly_group_bonus_job_succeeded weekId={} membersProcessed={} commissionsCreated={} carryoversStored={} durationMs={}",
                    result.weekId(),
                    result.membersProcessed(),
                    result.commissionsCreated(),
                    result.carryoversStored(),
                    Duration.between(startedAt, finishedAt).toMillis());
        } catch (RuntimeException ex) {
            OffsetDateTime finishedAt = OffsetDateTime.now();
            int fallbackWeekId = currentWeekId();
            saveRun(fallbackWeekId, WeeklyBonusJobRunStatus.FAILED, startedAt, finishedAt, 0, 0, 0, truncate(ex.getMessage()));
            PlatformMonitoringHooks.onWeeklyBonusJob(
                    WeeklyBonusJobRunStatus.FAILED.name(),
                    0,
                    0,
                    Duration.between(startedAt, finishedAt)
            );
            log.error("weekly_group_bonus_job_failed weekId={} durationMs={} error={}",
                    fallbackWeekId,
                    Duration.between(startedAt, finishedAt).toMillis(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    private int currentWeekId() {
        LocalDate localDate = LocalDate.now(ZoneId.of(groupBonusZone));
        int weekYear = localDate.get(IsoFields.WEEK_BASED_YEAR);
        int week = localDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return (weekYear * 100) + week;
    }

    private void saveRun(int weekId,
                         WeeklyBonusJobRunStatus status,
                         OffsetDateTime startedAt,
                         OffsetDateTime finishedAt,
                         int membersProcessed,
                         int commissionsCreated,
                         int carryoversStored,
                         String errorMessage) {
        MlmWeeklyBonusJobRun run = mlmWeeklyBonusJobRunRepository.findByWeekId(weekId)
                .orElseGet(() -> MlmWeeklyBonusJobRun.builder().weekId(weekId).build());
        run.setStatus(status);
        run.setStartedAt(startedAt);
        run.setFinishedAt(finishedAt);
        run.setMembersProcessed(membersProcessed);
        run.setCommissionsCreated(commissionsCreated);
        run.setCarryoversStored(carryoversStored);
        run.setErrorMessage(errorMessage);
        mlmWeeklyBonusJobRunRepository.save(run);
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }
}
