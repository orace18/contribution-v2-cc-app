package com.ccapp.agent.service;

import com.ccapp.agent.domain.entity.OfflineTransaction;
import com.ccapp.agent.domain.enums.TransactionType;
import com.ccapp.agent.dto.CreateOfflineTransactionRequest;
import com.ccapp.agent.repository.OfflineTransactionRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfflineTransactionServiceTest {

    @Mock
    private OfflineTransactionRepository offlineTransactionRepository;
    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private AgentService agentService;

    @InjectMocks
    private OfflineTransactionService offlineTransactionService;

    @Test
    void createOfflineTransaction_shouldRejectContributionForMvp() {
        UUID actorUserId = UUID.randomUUID();
        UUID accountHolderId = UUID.randomUUID();

        doNothing().when(agentService).assertActiveAgent(actorUserId);
        when(accountHolderRepository.existsById(accountHolderId)).thenReturn(true);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> offlineTransactionService.createOfflineTransaction(actorUserId, new CreateOfflineTransactionRequest(
                        accountHolderId,
                        TransactionType.CONTRIBUTION,
                        new BigDecimal("2500"),
                        "xof"
                ))
        );

        assertEquals("CIRCLE_CONTRIBUTIONS_DISABLED_FOR_MVP", ex.getMessage());
        verify(offlineTransactionRepository, never()).save(any(OfflineTransaction.class));
    }
}
