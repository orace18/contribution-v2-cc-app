package com.ccapp.payments.subscription.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MembershipReconciliationJob {

    private final MembershipReconciliationService membershipReconciliationService;

    @Scheduled(fixedDelayString = "${membership.reconciliation.fixed-delay-ms:60000}")
    public void reconcilePendingMemberships() {
        List<MembershipReconciliationService.ReconciliationResult> results =
                membershipReconciliationService.reconcilePendingMembershipBatch();
        if (results.isEmpty()) {
            return;
        }

        long finalized = results.stream().filter(result -> "FINALIZED".equals(result.action())).count();
        long failed = results.stream().filter(result -> "MARKED_FAILED".equals(result.action())).count();
        long waiting = results.stream().filter(result -> "WAITING".equals(result.action())).count();
        long errors = results.stream().filter(result -> "ERROR".equals(result.action())).count();
        log.info("membership_reconciliation_job_completed scanned={} finalized={} failed={} waiting={} errors={}",
                results.size(),
                finalized,
                failed,
                waiting,
                errors);
    }
}
