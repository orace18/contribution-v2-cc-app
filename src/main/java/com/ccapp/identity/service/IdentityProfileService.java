package com.ccapp.identity.service;

import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.dto.MeResponse;
import com.ccapp.identity.dto.UserDeviceDto;
import com.ccapp.identity.dto.UserProfileDto;
import com.ccapp.identity.dto.UserSessionDto;
import com.ccapp.identity.mapper.IdentityMapper;
import com.ccapp.identity.repository.UserDeviceRepository;
import com.ccapp.identity.repository.UserProfileRepository;
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
public class IdentityProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final UserSessionRepository userSessionRepository;
    private final IdentityMapper identityMapper;

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        log.debug("Identity me userId={}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UserProfileDto profile = userProfileRepository.findByUserId(userId)
                .map(identityMapper::toDto)
                .orElse(null);
        List<UserDeviceDto> devices = userDeviceRepository.findByUserIdAndDeletedFalse(userId).stream()
                .map(identityMapper::toDto)
                .toList();
        List<UserSessionDto> sessions = userSessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(userId, OffsetDateTime.now())
                .stream()
                .map(identityMapper::toDto)
                .toList();

        return new MeResponse(
                user.getId(),
                user.getFirebaseUid(),
                user.getEmail(),
                user.getRole(),
                user.getAccountStatus(),
                user.getKycStatus(),
                profile,
                devices,
                sessions
        );
    }
}
