package com.ccapp.notifications.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.notifications.dto.CreateNotificationRequest;
import com.ccapp.notifications.dto.NotificationResponse;
import com.ccapp.notifications.dto.SendMultiChannelNotificationRequest;
import com.ccapp.notifications.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Multi-channel user notifications")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    @Operation(summary = "Create a notification")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Recipient, actor or template not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<NotificationResponse>> create(@Valid @RequestBody CreateNotificationRequest request) {
        UUID actorUserId = request.actorUserId() != null ? request.actorUserId() : resolveActorUserId();
        CreateNotificationRequest normalized = new CreateNotificationRequest(
                request.accountHolderId(),
                actorUserId,
                request.type(),
                request.channel(),
                request.templateCode(),
                request.title(),
                request.message(),
                request.variables(),
                request.sendNow()
        );
        return ResponseEntity.ok(CommonResponse.success("Notification created", notificationService.createNotification(normalized)));
    }

    @PostMapping("/{notificationId}/send")
    @Operation(summary = "Dispatch a pending notification")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Notification not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<NotificationResponse>> send(@PathVariable UUID notificationId) {
        return ResponseEntity.ok(CommonResponse.success(
                "Notification sent",
                notificationService.sendNotification(notificationId, resolveActorUserId())
        ));
    }

    @PostMapping("/multi-channel")
    @Operation(summary = "Create and send multi-channel notifications")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Recipient, actor or template not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<NotificationResponse>>> sendMultiChannel(
            @Valid @RequestBody SendMultiChannelNotificationRequest request) {
        UUID actorUserId = request.actorUserId() != null ? request.actorUserId() : resolveActorUserId();
        SendMultiChannelNotificationRequest normalized = new SendMultiChannelNotificationRequest(
                request.accountHolderId(),
                actorUserId,
                request.type(),
                request.channels(),
                request.templateCode(),
                request.title(),
                request.message(),
                request.variables(),
                request.sendNow()
        );
        return ResponseEntity.ok(CommonResponse.success("Multi-channel notifications processed", notificationService.sendMultiChannel(normalized)));
    }

    @GetMapping("/me")
    @Operation(summary = "Get notifications for current account holder")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account holder not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<NotificationResponse>>> myNotifications() {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        UUID actorUserId = resolveActorUserId();
        return ResponseEntity.ok(CommonResponse.success(
                "Notifications",
                notificationService.getNotificationsForUser(actorUserId, accountHolderId)
        ));
    }

    @GetMapping("/me/unread-count")
    @Operation(summary = "Get unread notification count for current account holder")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<Long>> unreadCount() {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success("Unread count", notificationService.getUnreadCount(accountHolderId)));
    }

    @PatchMapping("/me/{notificationId}/read")
    @Operation(summary = "Mark a notification as read")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<Void>> markRead(@PathVariable UUID notificationId) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        notificationService.markRead(notificationId, accountHolderId);
        return ResponseEntity.ok(CommonResponse.success("Marked as read", null));
    }

    @PatchMapping("/me/read-all")
    @Operation(summary = "Mark all notifications as read")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<Void>> markAllRead() {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        notificationService.markAllRead(accountHolderId);
        return ResponseEntity.ok(CommonResponse.success("All marked as read", null));
    }

    @GetMapping("/account-holders/{accountHolderId}")
    @Operation(summary = "Get notifications for an account holder")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account holder not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<NotificationResponse>>> forAccountHolder(@PathVariable UUID accountHolderId) {
        UUID actorUserId = resolveActorUserId();
        return ResponseEntity.ok(CommonResponse.success(
                "Notifications",
                notificationService.getNotificationsForUser(actorUserId, accountHolderId)
        ));
    }

    private UUID resolveActorUserId() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        return principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
    }
}
