package com.ccapp.devtools.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.security.AdminPrincipal;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.devtools.dto.DeleteUserRequest;
import com.ccapp.devtools.service.DevToolsEnvironmentGuard;
import com.ccapp.devtools.service.DevUserResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/dev-tools")
@RequiredArgsConstructor
public class DevToolsController {

    static final String DEV_TOOLS_HEADER = "X-DEV-TOOLS";
    private static final String DEV_TOOLS_HEADER_VALUE = "ENABLED";

    private final DevUserResetService devUserResetService;
    private final DevToolsEnvironmentGuard devToolsEnvironmentGuard;
    private final AdminAuditService adminAuditService;

    @PostMapping("/delete-user")
    public ResponseEntity<CommonResponse<Void>> deleteUser(
            @RequestBody DeleteUserRequest request,
            @RequestHeader(value = DEV_TOOLS_HEADER, required = false) String devToolsHeader
    ) {
        devToolsEnvironmentGuard.assertEnabled();
        assertHeaderEnabled(devToolsHeader);
        request.validate();

        AdminPrincipal principal = AdminSecurityUtils.currentPrincipal();
        DevUserResetService.DevUserResetResult result = devUserResetService.deleteUser(
                request.normalizedEmail(),
                request.userId(),
                principal.adminUserId(),
                principal.email()
        );

        adminAuditService.logAction(
                principal.adminUserId(),
                "DEV_USER_DELETE",
                "DEV_TOOLS",
                result.accountHolderId() != null ? result.accountHolderId().toString() : null,
                auditPayload(result, request)
        );

        String message = result.deleted() ? "User deleted" : "User already deleted";
        return ResponseEntity.ok(CommonResponse.success(message));
    }

    private void assertHeaderEnabled(String devToolsHeader) {
        if (!DEV_TOOLS_HEADER_VALUE.equalsIgnoreCase(devToolsHeader == null ? null : devToolsHeader.trim())) {
            throw new AccessDeniedException("Dev tools header is required");
        }
    }

    private Map<String, Object> auditPayload(DevUserResetService.DevUserResetResult result, DeleteUserRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deleted", result.deleted());
        payload.put("userId", result.userId() != null
                ? result.userId().toString()
                : (request.userId() != null ? request.userId().toString() : null));
        payload.put("accountHolderId", result.accountHolderId() != null ? result.accountHolderId().toString() : null);
        payload.put("email", result.email() != null ? result.email() : request.normalizedEmail());
        return payload;
    }
}
