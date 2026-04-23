package com.ccapp.devtools.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.security.AdminPrincipal;
import com.ccapp.common.exception.GlobalExceptionHandler;
import com.ccapp.devtools.dto.DevDomainResetResponse;
import com.ccapp.devtools.dto.TargetedResetRequest;
import com.ccapp.devtools.service.DevToolsEnvironmentGuard;
import com.ccapp.devtools.service.DevTargetedUserResetService;
import com.ccapp.devtools.service.DevUserResetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DevToolsControllerTest {

    private final DevUserResetService devUserResetService = mock(DevUserResetService.class);
    private final DevTargetedUserResetService devTargetedUserResetService = mock(DevTargetedUserResetService.class);
    private final AdminAuditService adminAuditService = mock(AdminAuditService.class);

    private UUID adminId;
    private String adminEmail;

    @BeforeEach
    void setUpSecurity() {
        adminId = UUID.randomUUID();
        adminEmail = "qa-admin@clubchretien.dev";
        AdminPrincipal principal = AdminPrincipal.builder()
                .adminUserId(adminId)
                .email(adminEmail)
                .role(AdminRole.ROLE_SUPER_ADMIN)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(AdminRole.ROLE_SUPER_ADMIN.name()))
        ));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deleteUser_shouldWorkInDev() throws Exception {
        MockMvc mockMvc = mockMvcFor(environmentWithDevProfile());
        UUID userId = UUID.randomUUID();
        UUID accountHolderId = UUID.randomUUID();
        when(devUserResetService.deleteUser(eq("qa.user@clubchretien.dev"), isNull(UUID.class), eq(adminId), eq(adminEmail)))
                .thenReturn(DevUserResetService.DevUserResetResult.deleted(userId, accountHolderId, "qa.user@clubchretien.dev"));

        mockMvc.perform(post("/dev-tools/delete-user")
                        .contentType(APPLICATION_JSON)
                        .header(DevToolsController.DEV_TOOLS_HEADER, "ENABLED")
                        .content("""
                                {"email":"qa.user@clubchretien.dev"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User deleted"));

        verify(devUserResetService).deleteUser("qa.user@clubchretien.dev", null, adminId, adminEmail);
    }

    @Test
    void resetKyc_shouldWorkInDev() throws Exception {
        MockMvc mockMvc = mockMvcFor(environmentWithDevProfile());
        UUID accountHolderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TargetedResetRequest request = new TargetedResetRequest(accountHolderId, null);
        DevDomainResetResponse response = new DevDomainResetResponse(
                true,
                "KYC",
                accountHolderId,
                userId,
                OffsetDateTime.parse("2026-04-23T10:15:30Z"),
                "KYC reset completed",
                Map.of("accountHolderRowsUpdated", 1),
                List.of()
        );
        when(devTargetedUserResetService.resetKyc(eq(request), eq(adminId), eq(adminEmail)))
                .thenReturn(response);

        mockMvc.perform(post("/dev-tools/reset-kyc")
                        .contentType(APPLICATION_JSON)
                        .header(DevToolsController.DEV_TOOLS_HEADER, "ENABLED")
                        .content("""
                                {"accountHolderId":"%s"}
                                """.formatted(accountHolderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("KYC reset completed"))
                .andExpect(jsonPath("$.data.applied").value(true))
                .andExpect(jsonPath("$.data.domain").value("KYC"))
                .andExpect(jsonPath("$.data.accountHolderId").value(accountHolderId.toString()))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.summary.accountHolderRowsUpdated").value(1));

        verify(devTargetedUserResetService).resetKyc(request, adminId, adminEmail);
        verify(adminAuditService).logAction(
                eq(adminId),
                eq("DEV_RESET_KYC"),
                eq("DEV_TOOLS"),
                eq(accountHolderId.toString()),
                any()
        );
    }

    @Test
    void deleteUser_shouldReturnAlreadyDeletedWhenTargetIsMissing() throws Exception {
        MockMvc mockMvc = mockMvcFor(environmentWithDevProfile());
        when(devUserResetService.deleteUser(eq("gone.user@clubchretien.dev"), isNull(UUID.class), eq(adminId), eq(adminEmail)))
                .thenReturn(DevUserResetService.DevUserResetResult.alreadyDeleted(null, "gone.user@clubchretien.dev"));

        mockMvc.perform(post("/dev-tools/delete-user")
                        .contentType(APPLICATION_JSON)
                        .header(DevToolsController.DEV_TOOLS_HEADER, "ENABLED")
                        .content("""
                                {"email":"gone.user@clubchretien.dev"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User already deleted"));
    }

    @Test
    void deleteUser_shouldBeBlockedOutsideDev() throws Exception {
        MockMvc mockMvc = mockMvcFor(new MockEnvironment().withProperty("app.environment", "prod"));

        mockMvc.perform(post("/dev-tools/delete-user")
                        .contentType(APPLICATION_JSON)
                        .header(DevToolsController.DEV_TOOLS_HEADER, "ENABLED")
                        .content("""
                                {"email":"qa.user@clubchretien.dev"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Dev tools are disabled in this environment"));

        verifyNoInteractions(devUserResetService);
        verifyNoInteractions(devTargetedUserResetService);
    }

    private MockMvc mockMvcFor(MockEnvironment environment) {
        DevToolsController controller = new DevToolsController(
                devUserResetService,
                devTargetedUserResetService,
                new DevToolsEnvironmentGuard(environment),
                adminAuditService
        );
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private MockEnvironment environmentWithDevProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        return environment;
    }
}
