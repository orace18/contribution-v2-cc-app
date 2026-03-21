package com.ccapp.progress.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.progress.dto.TaskDefinitionRequest;
import com.ccapp.progress.dto.TaskDefinitionResponse;
import com.ccapp.progress.service.TaskDefinitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/admin/engagement/tasks", "/admin/engagement/tasks"})
@RequiredArgsConstructor
public class AdminTaskDefinitionController {

    private final TaskDefinitionService taskDefinitionService;
    private final AdminAuditService adminAuditService;

    @GetMapping
    public ResponseEntity<CommonResponse<List<TaskDefinitionResponse>>> listDefinitions() {
        List<TaskDefinitionResponse> response = taskDefinitionService.listDefinitions();
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "LIST_TASK_DEFINITIONS", "TASK_DEFINITION", null, "count=" + response.size());
        return ResponseEntity.ok(CommonResponse.success("Task definitions", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommonResponse<TaskDefinitionResponse>> getDefinition(@PathVariable UUID id) {
        TaskDefinitionResponse response = taskDefinitionService.getDefinition(id);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "VIEW_TASK_DEFINITION", "TASK_DEFINITION", id.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Task definition", response));
    }

    @PostMapping
    public ResponseEntity<CommonResponse<TaskDefinitionResponse>> createDefinition(@Valid @RequestBody TaskDefinitionRequest request) {
        TaskDefinitionResponse response = taskDefinitionService.createDefinition(request);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "CREATE_TASK_DEFINITION", "TASK_DEFINITION", response.id().toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Task definition created", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CommonResponse<TaskDefinitionResponse>> updateDefinition(@PathVariable UUID id,
                                                                                   @Valid @RequestBody TaskDefinitionRequest request) {
        TaskDefinitionResponse response = taskDefinitionService.updateDefinition(id, request);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "UPDATE_TASK_DEFINITION", "TASK_DEFINITION", id.toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Task definition updated", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<CommonResponse<TaskDefinitionResponse>> disableDefinition(@PathVariable UUID id) {
        TaskDefinitionResponse response = taskDefinitionService.disableDefinition(id);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "DISABLE_TASK_DEFINITION", "TASK_DEFINITION", id.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Task definition disabled", response));
    }
}
