package com.ccapp.agent.service;

import com.ccapp.agent.domain.entity.OfflineTransaction;
import com.ccapp.agent.domain.enums.TransactionType;
import com.ccapp.agent.dto.CreateOfflineTransactionRequest;
import com.ccapp.agent.repository.OfflineTransactionRepository;
import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.events.DomainEvent;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.service.OperationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfflineTransactionServiceTest {

    @Mock
    private OfflineTransactionRepository offlineTransactionRepository;
    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private CircleRepository circleRepository;
    @Mock
    private AgentService agentService;
    @Mock
    private OperationService operationService;
    @Mock
    private AccountingPostingService accountingPostingService;
    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private OfflineTransactionService offlineTransactionService;

    @Test
    void createOfflineTransaction_shouldPublishCircleContributionEvent_whenContributionRecorded() {
        UUID actorUserId = UUID.randomUUID();
        UUID accountHolderId = UUID.randomUUID();
        UUID circleId = UUID.randomUUID();

        doNothing().when(agentService).assertActiveAgent(actorUserId);
        when(accountHolderRepository.existsById(accountHolderId)).thenReturn(true);
        when(offlineTransactionRepository.save(any(OfflineTransaction.class))).thenAnswer(invocation -> {
            OfflineTransaction tx = invocation.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });
        when(circleRepository.findActiveCirclesForUser(accountHolderId))
                .thenReturn(List.of(Circle.builder().id(circleId).build()));

        offlineTransactionService.createOfflineTransaction(actorUserId, new CreateOfflineTransactionRequest(
                accountHolderId,
                TransactionType.CONTRIBUTION,
                new BigDecimal("2500"),
                "xof"
        ));

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventPublisher, org.mockito.Mockito.atLeastOnce()).publish(captor.capture());
        boolean foundContributionEvent = captor.getAllValues().stream()
                .anyMatch(event -> event.getClass().getSimpleName().equals("CircleContributionEvent"));
        assertTrue(foundContributionEvent);
    }
}
