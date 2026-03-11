package com.ccapp.chat.controller;

import com.ccapp.chat.dto.ChatMessageResponse;
import com.ccapp.chat.dto.ChatMessagesPageResponse;
import com.ccapp.chat.dto.SendChatMessageRequest;
import com.ccapp.chat.service.ChatMessageService;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/chat/messages")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Chat messaging")
@SecurityRequirement(name = "bearerAuth")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @PostMapping
    @Operation(summary = "Send and persist a chat message")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account holder not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<ChatMessageResponse>> send(@Valid @RequestBody SendChatMessageRequest request) {
        UUID senderAccountHolderId = SecurityUtils.currentAccountHolderId();
        UUID actorUserId = resolveActorUserId();
        ChatMessageResponse response = chatMessageService.sendMessage(actorUserId, senderAccountHolderId, request);
        return ResponseEntity.ok(CommonResponse.success("Message sent", response));
    }

    @GetMapping
    @Operation(summary = "List messages in a conversation with cursor pagination")
    public ResponseEntity<CommonResponse<ChatMessagesPageResponse>> list(@RequestParam UUID conversationId,
                                                                         @RequestParam(required = false) OffsetDateTime before,
                                                                         @RequestParam(required = false) Integer limit) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        UUID actorUserId = resolveActorUserId();
        ChatMessagesPageResponse response = chatMessageService.getMessages(
                actorUserId,
                requesterAccountHolderId,
                conversationId,
                before,
                limit
        );
        return ResponseEntity.ok(CommonResponse.success("Messages", response));
    }

    @PatchMapping("/{messageId}/delivered")
    @Operation(summary = "Mark message as delivered")
    public ResponseEntity<CommonResponse<ChatMessageResponse>> markDelivered(@PathVariable UUID messageId) {
        UUID recipientAccountHolderId = SecurityUtils.currentAccountHolderId();
        UUID actorUserId = resolveActorUserId();
        return ResponseEntity.ok(CommonResponse.success(
                "Message delivered",
                chatMessageService.markDelivered(actorUserId, recipientAccountHolderId, messageId)
        ));
    }

    @PatchMapping("/{messageId}/read")
    @Operation(summary = "Mark message as read")
    public ResponseEntity<CommonResponse<ChatMessageResponse>> markRead(@PathVariable UUID messageId) {
        UUID recipientAccountHolderId = SecurityUtils.currentAccountHolderId();
        UUID actorUserId = resolveActorUserId();
        return ResponseEntity.ok(CommonResponse.success(
                "Message read",
                chatMessageService.markRead(actorUserId, recipientAccountHolderId, messageId)
        ));
    }

    private UUID resolveActorUserId() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        return principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
    }
}
