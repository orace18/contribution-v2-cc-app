package com.ccapp.payments.subscription.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembershipReconciliationJobTest {

    @Mock
    private MembershipReconciliationService membershipReconciliationService;

    @InjectMocks
    private MembershipReconciliationJob membershipReconciliationJob;

    @Test
    void reconcilePendingMemberships_shouldInvokeBatchReconciliation() {
        when(membershipReconciliationService.reconcilePendingMembershipBatch()).thenReturn(List.of(
                new MembershipReconciliationService.ReconciliationResult(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "PENDING",
                        "ACTIVE",
                        "FINALIZED"
                )
        ));

        membershipReconciliationJob.reconcilePendingMemberships();

        verify(membershipReconciliationService).reconcilePendingMembershipBatch();
    }
}
