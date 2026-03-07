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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
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
        AdminUser adminUser = adminUserRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid admin credentials"));

        if (!passwordEncoder.matches(request.password(), adminUser.getPasswordHash())) {
            throw new UnauthorizedException("Invalid admin credentials");
        }

        if (adminUser.getStatus() != AdminUserStatus.ACTIVE) {
            throw new UnauthorizedException("Admin account is not active");
        }

        String challengeToken = adminJwtService.generateChallengeToken(adminUser);
        adminAuditService.logAction(adminUser.getId(), "ADMIN_LOGIN_PASSWORD_OK", "ADMIN_USER", adminUser.getId().toString(), null);
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
        if (!adminUser.isTwoFactorEnabled()) {
            throw new BusinessException("2FA is not activated for this admin account");
        }
        totpService.assertValidCode(adminUser.getTwoFactorSecret(), request.code());

        String accessToken = adminJwtService.generateAccessToken(adminUser);
        adminAuditService.logAction(adminUser.getId(), "ADMIN_LOGIN_2FA_OK", "ADMIN_USER", adminUser.getId().toString(), null);
        return new AdminTokenResponse(accessToken, "Bearer", accessTokenTtlMinutes * 60);
    }

    @Transactional
    public AdminSetupTwoFactorResponse setupTwoFactor(AdminSetupTwoFactorRequest request) {
        AdminUser adminUser = resolveFromChallenge(request.challengeToken());

        if (adminUser.getTwoFactorSecret() == null || adminUser.getTwoFactorSecret().isBlank()) {
            adminUser.setTwoFactorSecret(totpService.generateSecret());
            adminUserRepository.save(adminUser);
        }

        String code = request.code();
        if (code == null || code.isBlank()) {
            return new AdminSetupTwoFactorResponse(
                    adminUser.isTwoFactorEnabled(),
                    adminUser.getTwoFactorSecret(),
                    totpService.buildOtpAuthUri(adminUser.getEmail(), adminUser.getTwoFactorSecret()),
                    null,
                    null,
                    0L
            );
        }

        totpService.assertValidCode(adminUser.getTwoFactorSecret(), code);
        adminUser.setTwoFactorEnabled(true);
        if (adminUser.getActivatedAt() == null) {
            adminUser.setActivatedAt(OffsetDateTime.now());
        }
        adminUserRepository.save(adminUser);

        String accessToken = adminJwtService.generateAccessToken(adminUser);
        adminAuditService.logAction(adminUser.getId(), "ADMIN_2FA_ACTIVATED", "ADMIN_USER", adminUser.getId().toString(), null);
        return new AdminSetupTwoFactorResponse(
                true,
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

        return new AdminAcceptInvitationResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getRole(),
                saved.getStatus(),
                true
        );
    }

    private AdminUser resolveFromChallenge(String challengeToken) {
        AdminJwtService.AdminTokenClaims claims = adminJwtService.verify(challengeToken, AdminTokenType.CHALLENGE);
        AdminUser adminUser = adminUserRepository.findById(claims.adminUserId())
                .orElseThrow(() -> new UnauthorizedException("Admin user not found"));

        if (adminUser.getStatus() != AdminUserStatus.ACTIVE) {
            throw new UnauthorizedException("Admin account is not active");
        }
        return adminUser;
    }
}
