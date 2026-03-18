package com.ccapp.admin.service;

import com.ccapp.admin.domain.entity.AdminUser;
import com.ccapp.admin.domain.enums.AdminUserStatus;
import com.ccapp.admin.dto.ConfigMutationApprovalRequest;
import com.ccapp.admin.repository.AdminUserRepository;
import com.ccapp.admin.security.TotpService;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminConfigMutationSecurityService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;

    @Transactional(readOnly = true)
    public ConfigMutationApprovalContext validateMutationApproval(UUID actorAdminUserId,
                                                                  ConfigMutationApprovalRequest request) {
        if (actorAdminUserId == null) {
            throw new UnauthorizedException("Admin authentication required");
        }
        AdminUser actor = adminUserRepository.findById(actorAdminUserId)
                .orElseThrow(() -> new UnauthorizedException("Admin user not found"));
        if (actor.getStatus() != AdminUserStatus.ACTIVE) {
            throw new UnauthorizedException("Admin account is not active");
        }

        if (!passwordEncoder.matches(request.password(), actor.getPasswordHash())) {
            throw new UnauthorizedException("Invalid admin password");
        }
        if (!actor.isTwoFactorEnabled() || actor.getTwoFactorSecret() == null || actor.getTwoFactorSecret().isBlank()) {
            throw new BusinessException("2FA is not activated for this admin account");
        }
        totpService.assertValidCode(actor.getTwoFactorSecret(), request.twoFactorCode());

        UUID approvedByAdminUserId = actorAdminUserId;
        if (request.approvedByAdminUserId() != null) {
            AdminUser approver = adminUserRepository.findById(request.approvedByAdminUserId())
                    .orElseThrow(() -> new BusinessException("Approved by admin user not found"));
            if (approver.getStatus() != AdminUserStatus.ACTIVE) {
                throw new BusinessException("Approved by admin user is not active");
            }
            approvedByAdminUserId = approver.getId();
        }

        return new ConfigMutationApprovalContext(
                actorAdminUserId,
                approvedByAdminUserId,
                request.reasonForChange().trim()
        );
    }

    public record ConfigMutationApprovalContext(
            UUID changedByAdminUserId,
            UUID approvedByAdminUserId,
            String reasonForChange
    ) {
    }
}
