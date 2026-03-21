package com.ccapp.progress.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.progress.domain.enums.TaskType;
import com.ccapp.progress.dto.TaskResponse;
import com.ccapp.progress.dto.TaskCompletionResponse;
import com.ccapp.progress.dto.UserTaskResponse;
import com.ccapp.progress.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Gestion des taches utilisateur")
@SecurityRequirement(name = "bearerAuth")
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    @Operation(summary = "Lister les taches visibles de l'utilisateur courant")
    public ResponseEntity<CommonResponse<List<TaskResponse>>> tasks(@RequestParam(required = false) UUID accountHolderId) {
        UUID targetAccountHolderId = resolveTargetAccountHolderId(accountHolderId);
        return ResponseEntity.ok(CommonResponse.success(
                "Visible tasks",
                taskService.getVisibleTasks(resolveActorUserId(), targetAccountHolderId)
        ));
    }

    @GetMapping("/next")
    @Operation(summary = "Recuperer la prochaine meilleure action")
    public ResponseEntity<CommonResponse<TaskResponse>> next(@RequestParam(required = false) UUID accountHolderId) {
        UUID targetAccountHolderId = resolveTargetAccountHolderId(accountHolderId);
        return ResponseEntity.ok(CommonResponse.success(
                "Next best action",
                taskService.getNextBestAction(resolveActorUserId(), targetAccountHolderId)
        ));
    }

    @GetMapping("/me")
    @Operation(summary = "Lister les taches de l'utilisateur courant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<UserTaskResponse>>> myTasks(@RequestParam(required = false) UUID accountHolderId) {
        UUID targetAccountHolderId = resolveTargetAccountHolderId(accountHolderId);
        return ResponseEntity.ok(CommonResponse.success(
                "User tasks",
                taskService.getUserTasks(resolveActorUserId(), targetAccountHolderId)
        ));
    }

    @PostMapping("/{taskId:[0-9a-fA-F\\-]{36}}/complete")
    @Operation(summary = "Completer une tache par identifiant")
    public ResponseEntity<CommonResponse<TaskResponse>> completeById(@PathVariable UUID taskId,
                                                                     @RequestParam(required = false) UUID accountHolderId) {
        UUID targetAccountHolderId = resolveTargetAccountHolderId(accountHolderId);
        return ResponseEntity.ok(CommonResponse.success(
                "Task completed",
                taskService.completeTaskById(resolveActorUserId(), targetAccountHolderId, taskId)
        ));
    }

    @PostMapping("/{taskType}/complete")
    @Operation(summary = "Completer une tache et recalculer la progression")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "TaskType invalide ou erreur metier", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<TaskCompletionResponse>> complete(@PathVariable TaskType taskType,
                                                                           @RequestParam(required = false) UUID accountHolderId) {
        UUID targetAccountHolderId = resolveTargetAccountHolderId(accountHolderId);
        return ResponseEntity.ok(CommonResponse.success(
                "Task completed",
                taskService.completeTask(resolveActorUserId(), targetAccountHolderId, taskType)
        ));
    }

    private UUID resolveActorUserId() {
        UUID actorAccountHolderId = SecurityUtils.currentAccountHolderId();
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        return principal.userId() != null ? principal.userId() : actorAccountHolderId;
    }

    private UUID resolveTargetAccountHolderId(UUID accountHolderId) {
        UUID actorAccountHolderId = SecurityUtils.currentAccountHolderId();
        return accountHolderId != null ? accountHolderId : actorAccountHolderId;
    }
}
