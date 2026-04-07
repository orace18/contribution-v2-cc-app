package com.ccapp.admin.service;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.domain.entity.AdminInvitation;
import com.ccapp.admin.domain.entity.AdminUser;
import com.ccapp.admin.domain.enums.AdminUserStatus;
import com.ccapp.admin.dto.*;
import com.ccapp.admin.invitation.AdminInvitationService;
import com.ccapp.admin.repository.AdminUserRepository;
import com.ccapp.admin.security.AdminJwtService;
import com.ccapp.admin.security.AdminTokenType;
import com.ccapp.admin.security.TotpService;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthService {

    private final AdminAuditService adminAuditService;
    private final AdminUserRepository adminUserRepository;
    private final AdminInvitationService adminInvitationService;
    private final PasswordEncoder passwordEncoder;
    private final AdminJwtService adminJwtService;
    private final TotpService totpService;

    @Value("${admin.jwt.access-token-ttl-minutes:480}")
    private long accessTokenTtlMinutes;

    @Transactional(readOnly = true)
    public AdminLoginResponse login(AdminLoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        log.info("Admin login attempt email={}", normalizedEmail);

        AdminUser adminUser = adminUserRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (adminUser == null) {
            log.warn("Admin login failed: unknown email={}", normalizedEmail);
            throw new UnauthorizedException("Invalid admin credentials");
        }

        if (!passwordEncoder.matches(request.password(), adminUser.getPasswordHash())) {
            log.warn("Admin login failed: invalid password adminUserId={} email={}", adminUser.getId(), normalizedEmail);
            throw new UnauthorizedException("Invalid admin credentials");
        }

        if (adminUser.getStatus() != AdminUserStatus.ACTIVE) {
            log.warn("Admin login failed: inactive account adminUserId={} status={}", adminUser.getId(), adminUser.getStatus());
            throw new UnauthorizedException("Admin account is not active");
        }

        String challengeToken = adminJwtService.generateChallengeToken(adminUser);
        adminAuditService.logAction(adminUser.getId(), "ADMIN_LOGIN_PASSWORD_OK", "ADMIN_USER", adminUser.getId().toString(), null);
        log.info("Admin login password step ok adminUserId={} requires2faSetup={}", adminUser.getId(), !adminUser.isTwoFactorEnabled());
        return new AdminLoginResponse(
                challengeToken,
                true,
                !adminUser.isTwoFactorEnabled(),
                adminUser.getRole(),
                adminUser.getStatus()
        );
    }

    @Transactional
    public AdminTokenResponse verifyTwoFactor(AdminVerifyTwoFactorRequest request) {
        AdminUser adminUser = resolveFromChallenge(request.challengeToken());
        log.info("Admin verify-2fa attempt adminUserId={} enabled={}", adminUser.getId(), adminUser.isTwoFactorEnabled());
        if (!adminUser.isTwoFactorEnabled()) {
            log.warn("Admin verify-2fa denied: 2FA not activated adminUserId={}", adminUser.getId());
            throw new BusinessException("2FA is not activated for this admin account");
        }
        try {
            totpService.assertValidCode(adminUser.getTwoFactorSecret(), request.code());
        } catch (BusinessException ex) {
            log.warn("Admin verify-2fa failed: invalid code adminUserId={}", adminUser.getId());
            throw ex;
        }

        String accessToken = adminJwtService.generateAccessToken(adminUser);
        adminAuditService.logAction(adminUser.getId(), "ADMIN_LOGIN_2FA_OK", "ADMIN_USER", adminUser.getId().toString(), null);
        log.info("Admin verify-2fa success adminUserId={}", adminUser.getId());
        return new AdminTokenResponse(accessToken, "Bearer", accessTokenTtlMinutes * 60);
    }

    @Transactional
    public AdminSetupTwoFactorResponse setupTwoFactor(AdminSetupTwoFactorRequest request) {
        AdminUser adminUser = resolveFromChallenge(request.challengeToken());
        log.info("Admin setup-2fa request adminUserId={} enabled={}", adminUser.getId(), adminUser.isTwoFactorEnabled());

        if (adminUser.getTwoFactorSecret() == null || adminUser.getTwoFactorSecret().isBlank()) {
            adminUser.setTwoFactorSecret(totpService.generateSecret());
            adminUserRepository.save(adminUser);
            log.info("Admin setup-2fa secret generated adminUserId={}", adminUser.getId());
        }

        String code = request.code();
        if (code == null || code.isBlank()) {
            String otpAuthUri = totpService.buildOtpAuthUri(adminUser.getEmail(), adminUser.getTwoFactorSecret());
            log.info("Admin setup-2fa challenge issued adminUserId={}", adminUser.getId());
            return new AdminSetupTwoFactorResponse(
                    adminUser.isTwoFactorEnabled(),
                    adminUser.getTwoFactorSecret(),
                    otpAuthUri,
                    totpService.buildQrCodeDataUrl(otpAuthUri),
                    null,
                    null,
                    0L
            );
        }

        try {
            totpService.assertValidCode(adminUser.getTwoFactorSecret(), code);
        } catch (BusinessException ex) {
            log.warn("Admin setup-2fa failed: invalid code adminUserId={}", adminUser.getId());
            throw ex;
        }
        adminUser.setTwoFactorEnabled(true);
        if (adminUser.getActivatedAt() == null) {
            adminUser.setActivatedAt(OffsetDateTime.now());
        }
        adminUserRepository.save(adminUser);

        String accessToken = adminJwtService.generateAccessToken(adminUser);
        adminAuditService.logAction(adminUser.getId(), "ADMIN_2FA_ACTIVATED", "ADMIN_USER", adminUser.getId().toString(), null);
        log.info("Admin setup-2fa success adminUserId={}", adminUser.getId());
        return new AdminSetupTwoFactorResponse(
                true,
                null,
                null,
                null,
                accessToken,
                "Bearer",
                accessTokenTtlMinutes * 60
        );
    }

    @Transactional
    public AdminAcceptInvitationResponse acceptInvitation(AdminAcceptInvitationRequest request) {
        AdminInvitation invitation = adminInvitationService.getValidInvitation(request.invitationToken());
        log.info("Admin invitation acceptance email={} role={}", invitation.getEmail(), invitation.getRole());

        AdminUser adminUser = adminUserRepository.findByEmailIgnoreCase(invitation.getEmail())
                .orElseGet(() -> AdminUser.builder()
                        .email(invitation.getEmail())
                        .build());

        adminUser.setEmail(invitation.getEmail().trim().toLowerCase(Locale.ROOT));
        adminUser.setRole(invitation.getRole());
        adminUser.setCountryId(invitation.getCountryId());
        adminUser.setPasswordHash(passwordEncoder.encode(request.password()));
        adminUser.setStatus(AdminUserStatus.ACTIVE);
        adminUser.setTwoFactorEnabled(false);
        adminUser.setTwoFactorSecret(null);
        adminUser.setActivatedAt(OffsetDateTime.now());

        AdminUser saved = adminUserRepository.save(adminUser);
        adminInvitationService.markAccepted(invitation);
        adminAuditService.logAction(saved.getId(), "ADMIN_INVITATION_ACCEPTED", "ADMIN_USER", saved.getId().toString(), null);
        log.info("Admin invitation accepted adminUserId={} email={}", saved.getId(), saved.getEmail());

        return new AdminAcceptInvitationResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getRole(),
                saved.getStatus(),
                true
        );
    }

    private AdminUser resolveFromChallenge(String challengeToken) {
        AdminJwtService.AdminTokenClaims claims;
        try {
            claims = adminJwtService.verify(challengeToken, AdminTokenType.CHALLENGE);
        } catch (RuntimeException ex) {
            log.warn("Admin challenge verification failed: {}", ex.getMessage());
            throw ex;
        }
        AdminUser adminUser = adminUserRepository.findById(claims.adminUserId())
                .orElseThrow(() -> {
                    log.warn("Admin challenge verification failed: admin user not found adminUserId={}", claims.adminUserId());
                    return new UnauthorizedException("Admin user not found");
                });

        if (adminUser.getStatus() != AdminUserStatus.ACTIVE) {
            log.warn("Admin challenge verification failed: inactive adminUserId={} status={}", adminUser.getId(), adminUser.getStatus());
            throw new UnauthorizedException("Admin account is not active");
        }
        return adminUser;
    }
}
