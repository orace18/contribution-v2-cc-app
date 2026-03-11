package com.ccapp.identity.service;

import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.entity.UserSession;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.identity.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityAuthService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;

    @Transactional
    public void blockAccount(UUID userId, String reason) {
        log.info("Identity blockAccount userId={} reason={}", userId, reason);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.setAccountStatus(AccountStatus.BLOCKED);
        user.setBlockedReason(reason);
        userRepository.save(user);

        OffsetDateTime revokedAt = OffsetDateTime.now();
        List<UserSession> activeSessions = userSessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(user.getId(), revokedAt);
        activeSessions.forEach(session -> session.setRevokedAt(revokedAt));
        userSessionRepository.saveAll(activeSessions);
        log.info("Identity blockAccount success userId={} revokedSessions={}", userId, activeSessions.size());
    }
}
