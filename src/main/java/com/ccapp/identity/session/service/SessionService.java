package com.ccapp.identity.session.service;

import com.ccapp.identity.domain.entity.UserSession;
import com.ccapp.identity.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserSessionRepository userSessionRepository;

    @Transactional(readOnly = true)
    public List<UserSession> getActiveSessionsByAppUserId(UUID appUserId) {
        return userSessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(appUserId, OffsetDateTime.now());
    }
}
