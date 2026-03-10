package com.ccapp.identity.session.service;

import com.ccapp.identity.domain.entity.UserSession;
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
public class SessionService {

    private final UserSessionRepository userSessionRepository;

    @Transactional(readOnly = true)
    public List<UserSession> getActiveSessionsByAppUserId(UUID appUserId) {
        log.debug("Identity session getActiveSessions appUserId={}", appUserId);
        List<UserSession> sessions =
                userSessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(appUserId, OffsetDateTime.now());
        log.debug("Identity session active count appUserId={} count={}", appUserId, sessions.size());
        return sessions;
    }
}
