package com.ccapp.devtools.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.security.AdminPrincipal;
import com.ccapp.common.exception.GlobalExceptionHandler;
import com.ccapp.devtools.service.DevToolsEnvironmentGuard;
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

import java.util.List;
import java.util.UUID;

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
    }

    private MockMvc mockMvcFor(MockEnvironment environment) {
        DevToolsController controller = new DevToolsController(
                devUserResetService,
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
