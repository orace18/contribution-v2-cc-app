package com.ccapp.progress.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.progress.domain.enums.TaskType;
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

    @GetMapping("/me")
    @Operation(summary = "Lister les taches de l'utilisateur courant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<UserTaskResponse>>> myTasks() {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(CommonResponse.success("User tasks", taskService.getUserTasks(userId)));
    }

    @PostMapping("/{taskType}/complete")
    @Operation(summary = "Completer une tache et recalculer la progression")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "TaskType invalide ou erreur metier", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<TaskCompletionResponse>> complete(@PathVariable TaskType taskType) {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(CommonResponse.success("Task completed", taskService.completeTask(userId, taskType)));
    }
}
