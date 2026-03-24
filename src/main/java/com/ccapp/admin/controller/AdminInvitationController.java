package com.ccapp.admin.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.dto.AdminAccountSummaryResponse;
import com.ccapp.admin.dto.AdminInvitationResponse;
import com.ccapp.admin.dto.ChangeAdminRoleRequest;
import com.ccapp.admin.dto.CreateAdminInvitationRequest;
import com.ccapp.admin.invitation.AdminInvitationService;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.common.api.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/invitations")
@RequiredArgsConstructor
public class AdminInvitationController {

    private final AdminInvitationService adminInvitationService;
    private final AdminAuditService adminAuditService;

    @GetMapping("/admin-accounts")
    public ResponseEntity<CommonResponse<List<AdminAccountSummaryResponse>>> listAdminAccounts() {
        List<AdminAccountSummaryResponse> response = adminInvitationService.listAdminAccounts();
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "LIST_ADMIN_ACCOUNTS", "ADMIN_USER", null, "count=" + response.size());
        return ResponseEntity.ok(CommonResponse.success("Admin accounts", response));
    }

    @PostMapping
    public ResponseEntity<CommonResponse<AdminInvitationResponse>> createInvitation(
            @Valid @RequestBody CreateAdminInvitationRequest request) {
        requireSuperAdmin();
        AdminInvitationResponse response = adminInvitationService.createInvitation(request);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "CREATE_INVITATION", "ADMIN_INVITATION", response.id().toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Invitation created", response));
    }

    @GetMapping
    public ResponseEntity<CommonResponse<List<AdminInvitationResponse>>> listInvitations() {
        List<AdminInvitationResponse> response = adminInvitationService.listInvitations();
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "LIST_INVITATIONS", "ADMIN_INVITATION", null, "count=" + response.size());
        return ResponseEntity.ok(CommonResponse.success("Invitations", response));
    }

    @PostMapping("/{id}/resend")
    public ResponseEntity<CommonResponse<AdminInvitationResponse>> resend(@PathVariable UUID id) {
        requireSuperAdmin();
        AdminInvitationResponse response = adminInvitationService.resend(id);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "RESEND_INVITATION", "ADMIN_INVITATION", id.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Invitation resent", response));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<CommonResponse<AdminInvitationResponse>> revoke(@PathVariable UUID id) {
        requireSuperAdmin();
        AdminInvitationResponse response = adminInvitationService.revoke(id);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "REVOKE_INVITATION", "ADMIN_INVITATION", id.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Invitation revoked", response));
    }

    @PostMapping("/admin-accounts/{id}/disable")
    public ResponseEntity<CommonResponse<AdminAccountSummaryResponse>> disableAccount(@PathVariable UUID id) {
        requireSuperAdmin();
        AdminAccountSummaryResponse response = adminInvitationService.disableAccount(id);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "DISABLE_ADMIN_ACCOUNT", "ADMIN_USER", id.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Admin account disabled", response));
    }

    @PostMapping("/admin-accounts/{id}/enable")
    public ResponseEntity<CommonResponse<AdminAccountSummaryResponse>> enableAccount(@PathVariable UUID id) {
        requireSuperAdmin();
        AdminAccountSummaryResponse response = adminInvitationService.enableAccount(id);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "ENABLE_ADMIN_ACCOUNT", "ADMIN_USER", id.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Admin account enabled", response));
    }

    @PutMapping("/admin-accounts/{id}/role")
    public ResponseEntity<CommonResponse<AdminAccountSummaryResponse>> changeRole(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeAdminRoleRequest request) {
        requireSuperAdmin();
        AdminAccountSummaryResponse response = adminInvitationService.changeRole(id, request.role(), request.countryId());
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "CHANGE_ADMIN_ROLE", "ADMIN_USER", id.toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Admin role updated", response));
    }

    private void requireSuperAdmin() {
        if (AdminSecurityUtils.currentRole() != AdminRole.ROLE_SUPER_ADMIN) {
            throw new AccessDeniedException("SUPER_ADMIN role required");
        }
    }
}
