package com.ccapp.admin.invitation;

import com.ccapp.admin.domain.entity.AdminInvitation;
import com.ccapp.admin.domain.entity.AdminUser;
import com.ccapp.admin.domain.enums.AdminInvitationStatus;
import com.ccapp.admin.domain.enums.AdminUserStatus;
import com.ccapp.admin.dto.AdminAccountSummaryResponse;
import com.ccapp.admin.dto.AdminInvitationResponse;
import com.ccapp.admin.dto.CreateAdminInvitationRequest;
import com.ccapp.admin.repository.AdminInvitationRepository;
import com.ccapp.admin.repository.AdminUserRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminInvitationService {

    private final AdminInvitationRepository adminInvitationRepository;
    private final AdminUserRepository adminUserRepository;
    private final AdminInvitationNotifier adminInvitationNotifier;
    private final CountryRepository countryRepository;

    @Value("${admin.invitation.default-expiration-hours:72}")
    private long defaultExpirationHours;

    @Transactional
    public AdminInvitationResponse createInvitation(CreateAdminInvitationRequest request) {
        validateRequest(request);

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        adminUserRepository.findByEmailIgnoreCase(email)
                .ifPresent(existing -> {
                    if (existing.getStatus() != com.ccapp.admin.domain.enums.AdminUserStatus.DISABLED) {
                        throw new BusinessException("Admin user already exists for this email");
                    }
                });

        expirePendingInvitations(email);

        long expiryHours = request.expiresInHours() == null || request.expiresInHours() <= 0
                ? defaultExpirationHours
                : request.expiresInHours();

        AdminInvitation invitation = adminInvitationRepository.save(AdminInvitation.builder()
                .id(UUID.randomUUID())
                .email(email)
                .role(request.role())
                .countryId(request.countryId())
                .invitationToken(generateToken())
                .status(AdminInvitationStatus.PENDING)
                .expiresAt(OffsetDateTime.now().plusHours(expiryHours))
                .build());

        adminInvitationNotifier.sendInvitation(invitation);
        return toResponse(invitation);
    }

    @Transactional(readOnly = true)
    public List<AdminAccountSummaryResponse> listAdminAccounts() {
        Map<UUID, String> countryCodeById = countryRepository.findAll().stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getCode()));

        return adminUserRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(user -> new AdminAccountSummaryResponse(
                        user.getId(),
                        user.getEmail(),
                        user.getRole(),
                        user.getCountryId(),
                        user.getCountryId() != null ? countryCodeById.get(user.getCountryId()) : null,
                        user.getStatus(),
                        user.isTwoFactorEnabled(),
                        user.getCreatedAt(),
                        user.getActivatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminInvitationResponse> listInvitations() {
        return adminInvitationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminInvitationResponse resend(UUID invitationId) {
        AdminInvitation invitation = adminInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));

        if (invitation.getStatus() != AdminInvitationStatus.PENDING) {
            throw new BusinessException("Only pending invitations can be resent");
        }

        if (invitation.getExpiresAt().isBefore(OffsetDateTime.now())) {
            invitation.setStatus(AdminInvitationStatus.EXPIRED);
            throw new BusinessException("Invitation already expired");
        }

        invitation.setInvitationToken(generateToken());
        invitation.setExpiresAt(OffsetDateTime.now().plusHours(defaultExpirationHours));
        AdminInvitation saved = adminInvitationRepository.save(invitation);
        adminInvitationNotifier.sendInvitation(saved);
        return toResponse(saved);
    }

    @Transactional
    public AdminInvitationResponse revoke(UUID invitationId) {
        AdminInvitation invitation = adminInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));

        if (invitation.getStatus() == AdminInvitationStatus.ACCEPTED) {
            throw new BusinessException("Accepted invitation cannot be revoked");
        }

        invitation.setStatus(AdminInvitationStatus.EXPIRED);
        invitation.setExpiresAt(OffsetDateTime.now());
        return toResponse(adminInvitationRepository.save(invitation));
    }

    @Transactional(readOnly = true)
    public AdminInvitation getValidInvitation(String token) {
        AdminInvitation invitation = adminInvitationRepository.findByInvitationToken(token)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));

        if (invitation.getStatus() != AdminInvitationStatus.PENDING) {
            throw new BusinessException("Invitation is not pending");
        }
        if (invitation.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessException("Invitation expired");
        }
        return invitation;
    }

    @Transactional
    public void markAccepted(AdminInvitation invitation) {
        invitation.setStatus(AdminInvitationStatus.ACCEPTED);
        adminInvitationRepository.save(invitation);
    }

    @Transactional
    public AdminAccountSummaryResponse disableAccount(UUID id) {
        AdminUser user = adminUserRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Admin account not found"));
        if (user.getStatus() == AdminUserStatus.DISABLED) {
            throw new BusinessException("Admin account is already disabled");
        }
        user.setStatus(AdminUserStatus.DISABLED);
        return toAccountResponse(adminUserRepository.save(user));
    }

    @Transactional
    public AdminAccountSummaryResponse enableAccount(UUID id) {
        AdminUser user = adminUserRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Admin account not found"));
        if (user.getStatus() != AdminUserStatus.DISABLED) {
            throw new BusinessException("Admin account is not disabled");
        }
        user.setStatus(AdminUserStatus.ACTIVE);
        return toAccountResponse(adminUserRepository.save(user));
    }

    @Transactional
    public AdminAccountSummaryResponse changeRole(UUID id, com.ccapp.admin.domain.enums.AdminRole newRole, UUID newCountryId) {
        if (newRole == com.ccapp.admin.domain.enums.AdminRole.ROLE_SUPER_ADMIN && newCountryId != null) {
            throw new BusinessException("SUPER_ADMIN must not have countryId");
        }
        if (newRole != com.ccapp.admin.domain.enums.AdminRole.ROLE_SUPER_ADMIN && newCountryId == null) {
            throw new BusinessException("countryId is required for non super admin roles");
        }
        AdminUser user = adminUserRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Admin account not found"));
        user.setRole(newRole);
        user.setCountryId(newCountryId);
        return toAccountResponse(adminUserRepository.save(user));
    }

    @Transactional
    public int expireOutdatedInvitations() {
        List<AdminInvitation> expired = adminInvitationRepository.findByStatusAndExpiresAtBefore(
                AdminInvitationStatus.PENDING,
                OffsetDateTime.now()
        );
        expired.forEach(invitation -> invitation.setStatus(AdminInvitationStatus.EXPIRED));
        adminInvitationRepository.saveAll(expired);
        return expired.size();
    }

    private void expirePendingInvitations(String email) {
        List<AdminInvitation> invitations = adminInvitationRepository.findAllByOrderByCreatedAtDesc();
        invitations.stream()
                .filter(inv -> inv.getEmail().equalsIgnoreCase(email) && inv.getStatus() == AdminInvitationStatus.PENDING)
                .forEach(inv -> inv.setStatus(AdminInvitationStatus.EXPIRED));
        adminInvitationRepository.saveAll(invitations);
    }

    private void validateRequest(CreateAdminInvitationRequest request) {
        if (request.role() == com.ccapp.admin.domain.enums.AdminRole.ROLE_SUPER_ADMIN && request.countryId() != null) {
            throw new BusinessException("SUPER_ADMIN must not have countryId");
        }
        if (request.role() != com.ccapp.admin.domain.enums.AdminRole.ROLE_SUPER_ADMIN && request.countryId() == null) {
            throw new BusinessException("countryId is required for non super admin roles");
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private AdminAccountSummaryResponse toAccountResponse(AdminUser user) {
        String countryCode = user.getCountryId() != null
                ? countryRepository.findById(user.getCountryId()).map(c -> c.getCode()).orElse(null)
                : null;
        return new AdminAccountSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getCountryId(),
                countryCode,
                user.getStatus(),
                user.isTwoFactorEnabled(),
                user.getCreatedAt(),
                user.getActivatedAt()
        );
    }

    private AdminInvitationResponse toResponse(AdminInvitation invitation) {
        return new AdminInvitationResponse(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getRole(),
                invitation.getCountryId(),
                invitation.getStatus(),
                invitation.getExpiresAt(),
                invitation.getCreatedAt()
        );
    }
}
