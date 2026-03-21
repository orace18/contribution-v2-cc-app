package com.ccapp.admin.controller;

import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.repository.AdminAuditLogRepository;
import com.ccapp.admin.security.AdminPrincipal;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.progress.domain.enums.TaskTriggerEvent;
import com.ccapp.progress.domain.enums.TaskType;
import com.ccapp.progress.dto.TaskDefinitionRequest;
import com.ccapp.progress.dto.TaskDefinitionResponse;
import com.ccapp.progress.service.TaskDefinitionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        AdminTaskDefinitionController.class,
        TaskDefinitionService.class,
        com.ccapp.admin.audit.AdminAuditService.class,
        TestJacksonAndCachingConfig.class
})
class AdminTaskDefinitionControllerIntegrationTest {

    @Autowired
    private AdminTaskDefinitionController controller;
    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private UUID adminId;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        AdminPrincipal principal = AdminPrincipal.builder()
                .adminUserId(adminId)
                .email("ops-admin@ccapp.io")
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
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUpdateAndDisableDefinition_shouldPersistAndAudit() {
        ResponseEntity<CommonResponse<TaskDefinitionResponse>> createdResponse = controller.createDefinition(request(
                TaskType.COMPLETE_PROFILE,
                "Complete ton profil",
                "Ajoute les informations manquantes",
                true
        ));
        TaskDefinitionResponse created = createdResponse.getBody().data();

        assertNotNull(created);
        assertEquals(TaskType.COMPLETE_PROFILE, created.taskType());
        assertEquals(1L, adminAuditLogRepository.countByEntityTypeAndEntityIdAndAction(
                "TASK_DEFINITION",
                created.id().toString(),
                "CREATE_TASK_DEFINITION"
        ));

        ResponseEntity<CommonResponse<TaskDefinitionResponse>> updatedResponse = controller.updateDefinition(
                created.id(),
                request(
                        TaskType.COMPLETE_PROFILE,
                        "Profil complet",
                        "Mets a jour ton profil utilisateur",
                        true
                )
        );
        TaskDefinitionResponse updated = updatedResponse.getBody().data();

        assertEquals("Profil complet", updated.titleTemplate());
        assertEquals(1L, adminAuditLogRepository.countByEntityTypeAndEntityIdAndAction(
                "TASK_DEFINITION",
                created.id().toString(),
                "UPDATE_TASK_DEFINITION"
        ));

        ResponseEntity<CommonResponse<TaskDefinitionResponse>> disabledResponse = controller.disableDefinition(created.id());
        TaskDefinitionResponse disabled = disabledResponse.getBody().data();

        assertFalse(disabled.isActive());
        assertEquals(1L, adminAuditLogRepository.countByEntityTypeAndEntityIdAndAction(
                "TASK_DEFINITION",
                created.id().toString(),
                "DISABLE_TASK_DEFINITION"
        ));
    }

    private TaskDefinitionRequest request(TaskType taskType,
                                          String title,
                                          String description,
                                          boolean active) {
        ObjectNode actionJson = objectMapper.createObjectNode();
        actionJson.put("type", "FLOW");
        actionJson.put("flow", taskType.name() + "_FLOW");

        ObjectNode metadataJson = objectMapper.createObjectNode();
        metadataJson.put("basePriority", taskType.defaultPriority());

        return new TaskDefinitionRequest(
                taskType,
                title,
                description,
                TaskTriggerEvent.MANUAL,
                actionJson,
                25,
                active,
                metadataJson
        );
    }
}
