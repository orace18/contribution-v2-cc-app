package com.ccapp.admin.service;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.domain.entity.AdminUser;
import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.domain.enums.AdminUserStatus;
import com.ccapp.admin.dto.AdminLoginRequest;
import com.ccapp.admin.dto.AdminLoginResponse;
import com.ccapp.admin.dto.AdminTokenResponse;
import com.ccapp.admin.dto.AdminVerifyTwoFactorRequest;
import com.ccapp.admin.invitation.AdminInvitationService;
import com.ccapp.admin.repository.AdminUserRepository;
import com.ccapp.admin.security.AdminJwtService;
import com.ccapp.admin.security.AdminTokenType;
import com.ccapp.admin.security.TotpService;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthFlowRegressionTest {

    @Mock
    private AdminAuditService adminAuditService;
    @Mock
    private AdminUserRepository adminUserRepository;
    @Mock
    private AdminInvitationService adminInvitationService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AdminJwtService adminJwtService;
    @Mock
    private TotpService totpService;

    @InjectMocks
    private AdminAuthService adminAuthService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adminAuthService, "accessTokenTtlMinutes", 480L);
    }

    @Test
    void login_shouldRequireTwoFactorAndReturnChallenge_whenCredentialsAreValid() {
        AdminUser adminUser = adminUser(false, AdminUserStatus.ACTIVE);
        when(adminUserRepository.findByEmailIgnoreCase("admin@test.com")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("PlainPass#1", adminUser.getPasswordHash())).thenReturn(true);
        when(adminJwtService.generateChallengeToken(adminUser)).thenReturn("challenge-token");

        AdminLoginResponse response = adminAuthService.login(new AdminLoginRequest("Admin@Test.com", "PlainPass#1"));

        assertEquals("challenge-token", response.challengeToken());
        assertTrue(response.requiresTwoFactor());
        assertTrue(response.requiresTwoFactorSetup());
        assertEquals(AdminRole.ROLE_SUPER_ADMIN, response.role());
        assertEquals(AdminUserStatus.ACTIVE, response.status());
        verify(adminAuditService).logAction(eq(adminUser.getId()), eq("ADMIN_LOGIN_PASSWORD_OK"), any(), any(), eq(null));
    }

    @Test
    void verifyTwoFactor_shouldIssueAccessToken_whenChallengeAndCodeAreValid() {
        AdminUser adminUser = adminUser(true, AdminUserStatus.ACTIVE);
        adminUser.setTwoFactorSecret("OTPSECRET");
        UUID adminId = adminUser.getId();

        when(adminJwtService.verify("challenge-token", AdminTokenType.CHALLENGE))
                .thenReturn(new AdminJwtService.AdminTokenClaims(
                        adminId,
                        adminUser.getEmail(),
                        adminUser.getRole().name(),
                        adminUser.getCountryId(),
                        999_999_999L,
                        AdminTokenType.CHALLENGE.name()
                ));
        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        doNothing().when(totpService).assertValidCode("OTPSECRET", "123456");
        when(adminJwtService.generateAccessToken(adminUser)).thenReturn("access-token");

        AdminTokenResponse response = adminAuthService.verifyTwoFactor(
                new AdminVerifyTwoFactorRequest("challenge-token", "123456")
        );

        assertEquals("access-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(480L * 60L, response.expiresInSeconds());
        verify(adminAuditService).logAction(eq(adminId), eq("ADMIN_LOGIN_2FA_OK"), any(), any(), eq(null));
    }

    @Test
    void verifyTwoFactor_shouldReject_whenAccountHasNoTwoFactorEnabled() {
        AdminUser adminUser = adminUser(false, AdminUserStatus.ACTIVE);
        UUID adminId = adminUser.getId();

        when(adminJwtService.verify("challenge-token", AdminTokenType.CHALLENGE))
                .thenReturn(new AdminJwtService.AdminTokenClaims(
                        adminId,
                        adminUser.getEmail(),
                        adminUser.getRole().name(),
                        adminUser.getCountryId(),
                        999_999_999L,
                        AdminTokenType.CHALLENGE.name()
                ));
        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));

        assertThrows(
                BusinessException.class,
                () -> adminAuthService.verifyTwoFactor(new AdminVerifyTwoFactorRequest("challenge-token", "123456"))
        );
        verify(adminJwtService, never()).generateAccessToken(any());
    }

    @Test
    void login_shouldReject_whenPasswordIsInvalid() {
        AdminUser adminUser = adminUser(true, AdminUserStatus.ACTIVE);
        when(adminUserRepository.findByEmailIgnoreCase("admin@test.com")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("WrongPass", adminUser.getPasswordHash())).thenReturn(false);

        assertThrows(
                UnauthorizedException.class,
                () -> adminAuthService.login(new AdminLoginRequest("admin@test.com", "WrongPass"))
        );
        verify(adminJwtService, never()).generateChallengeToken(any());
    }

    private AdminUser adminUser(boolean twoFactorEnabled, AdminUserStatus status) {
        return AdminUser.builder()
                .id(UUID.randomUUID())
                .email("admin@test.com")
                .passwordHash("encoded-password")
                .role(AdminRole.ROLE_SUPER_ADMIN)
                .status(status)
                .twoFactorEnabled(twoFactorEnabled)
                .build();
    }
}
