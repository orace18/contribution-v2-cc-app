package com.ccapp.agent.service;

import com.ccapp.agent.domain.entity.AssistedSession;
import com.ccapp.agent.dto.AssistedSessionResponse;
import com.ccapp.agent.dto.CreateAssistedSessionRequest;
import com.ccapp.agent.repository.AssistedSessionRepository;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.dto.CreateNotificationRequest;
import com.ccapp.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AssistedSessionService {

    private final AssistedSessionRepository assistedSessionRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final AgentService agentService;
    private final NotificationService notificationService;

    @Transactional
    public AssistedSessionResponse createSession(UUID actorUserId, CreateAssistedSessionRequest request) {
        agentService.assertActiveAgent(actorUserId);

        if (!accountHolderRepository.existsById(request.accountHolderId())) {
            throw new NotFoundException("Account holder not found");
        }

        AssistedSession saved = assistedSessionRepository.save(AssistedSession.builder()
                .agentUserId(actorUserId)
                .accountHolderId(request.accountHolderId())
                .sessionType(request.sessionType())
                .build());
        emitAgentActionNotification(saved);

        return toResponse(saved);
    }

    private void emitAgentActionNotification(AssistedSession session) {
        try {
            notificationService.createNotification(new CreateNotificationRequest(
                    session.getAccountHolderId(),
                    session.getAgentUserId(),
                    NotificationType.AGENT_ACTION,
                    NotificationChannel.IN_APP,
                    "AGENT_ACTION_ASSISTED_SESSION",
                    "An assisted session has been started by your agent.",
                    null,
                    true
            ));
        } catch (RuntimeException ex) {
            log.warn("Failed to emit AGENT_ACTION notification for assistedSessionId={}", session.getId(), ex);
        }
    }

    private AssistedSessionResponse toResponse(AssistedSession session) {
        return new AssistedSessionResponse(
                session.getId(),
                session.getAgentUserId(),
                session.getAccountHolderId(),
                session.getSessionType(),
                session.getStartedAt(),
                session.getEndedAt()
        );
    }
}
