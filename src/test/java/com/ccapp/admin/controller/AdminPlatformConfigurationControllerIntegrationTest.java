package com.ccapp.admin.controller;

import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.dto.ConfigMutationApprovalRequest;
import com.ccapp.admin.repository.AdminAuditLogRepository;
import com.ccapp.admin.security.AdminPrincipal;
import com.ccapp.admin.service.AdminConfigMutationSecurityService;
import com.ccapp.admin.service.ConfigAuditMetadataService;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.ccapp.platform.config.dto.PlatformConfigVersionResponse;
import com.ccapp.platform.config.dto.SecureUpsertPlatformConfigRequest;
import com.ccapp.platform.config.service.PlatformConfigReadinessService;
import com.ccapp.platform.config.service.PlatformConfigurationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        AdminPlatformConfigurationController.class,
        PlatformConfigurationService.class,
        PlatformConfigReadinessService.class,
        ConfigAuditMetadataService.class,
        com.ccapp.admin.audit.AdminAuditService.class,
        TestJacksonAndCachingConfig.class
})
class AdminPlatformConfigurationControllerIntegrationTest {

    @Autowired
    private AdminPlatformConfigurationController controller;
    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @MockBean
    private AdminConfigMutationSecurityService adminConfigMutationSecurityService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID adminId;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        AdminPrincipal principal = AdminPrincipal.builder()
                .adminUserId(adminId)
                .email("qa-admin@ccapp.io")
                .role(AdminRole.ROLE_SUPER_ADMIN)
                .countryId(null)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(AdminRole.ROLE_SUPER_ADMIN.name()))
                )
        );

        when(adminConfigMutationSecurityService.validateMutationApproval(eq(adminId), any()))
                .thenReturn(new AdminConfigMutationSecurityService.ConfigMutationApprovalContext(
                        adminId,
                        adminId,
                        "Final QA"
                ));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void upsertPlatformConfig_shouldCreateAuditLogEntry() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("taxRate", 18);
        payload.put("marketplaceDefaultCommission", 5);
        payload.set("featureFlags", objectMapper.createObjectNode().put("chatEnabled", true));
        payload.set("systemLimits", objectMapper.createObjectNode().put("minMembers", 5).put("maxMembers", 50));

        SecureUpsertPlatformConfigRequest request = new SecureUpsertPlatformConfigRequest(
                payload,
                OffsetDateTime.now(),
                new ConfigMutationApprovalRequest("ignored", "ignored", "Final QA", adminId)
        );

        ResponseEntity<CommonResponse<PlatformConfigVersionResponse>> response = controller
                .upsertPlatformConfig(PlatformConfigType.GLOBAL_GENERAL.externalName(), request);
        PlatformConfigVersionResponse version = response.getBody().data();
        assertNotNull(version);

        long auditCount = adminAuditLogRepository.countByEntityTypeAndEntityIdAndAction(
                "PLATFORM_CONFIG",
                version.id().toString(),
                "UPSERT_PLATFORM_CONFIG"
        );
        assertEquals(1L, auditCount);
    }
}
