package com.ccapp.admin.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.dto.AdminPlatformUserResponse;
import com.ccapp.admin.dto.AdminUserDetailsResponse;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.admin.service.AdminUserManagementService;
import com.ccapp.common.api.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserManagementService adminUserManagementService;
    private final AdminAuditService adminAuditService;

    @GetMapping
    public ResponseEntity<CommonResponse<List<AdminPlatformUserResponse>>> listUsers() {
        List<AdminPlatformUserResponse> response = adminUserManagementService.listUsers();
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "LIST_USERS", "ACCOUNT_HOLDER", null, "count=" + response.size());
        return ResponseEntity.ok(CommonResponse.success("Users", response));
    }

    @GetMapping("/{accountHolderId}")
    public ResponseEntity<CommonResponse<AdminUserDetailsResponse>> getUser(@PathVariable UUID accountHolderId) {
        AdminUserDetailsResponse response = adminUserManagementService.getUser(accountHolderId);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "VIEW_USER", "ACCOUNT_HOLDER", accountHolderId.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("User details", response));
    }

    @PostMapping("/{accountHolderId}/block")
    public ResponseEntity<CommonResponse<Void>> block(@PathVariable UUID accountHolderId) {
        adminUserManagementService.blockUser(accountHolderId);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "BLOCK_USER", "ACCOUNT_HOLDER", accountHolderId.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("User blocked"));
    }

    @PostMapping("/{accountHolderId}/unblock")
    public ResponseEntity<CommonResponse<Void>> unblock(@PathVariable UUID accountHolderId) {
        adminUserManagementService.unblockUser(accountHolderId);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "UNBLOCK_USER", "ACCOUNT_HOLDER", accountHolderId.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("User unblocked"));
    }
}
