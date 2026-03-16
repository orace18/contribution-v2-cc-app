package com.ccapp.chat.controller;

import com.ccapp.chat.dto.ChatConversationSummaryResponse;
import com.ccapp.chat.dto.CreateOrResolveConversationRequest;
import com.ccapp.chat.service.ChatConversationService;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.api.PageResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/chat/conversations")
@RequiredArgsConstructor
@Tag(name = "Chat Conversations", description = "Conversation endpoints for mobile UX")
@SecurityRequirement(name = "bearerAuth")
public class ChatConversationController {

    private final ChatConversationService chatConversationService;

    @GetMapping
    @Operation(summary = "List conversations for authenticated account holder")
    public ResponseEntity<CommonResponse<PageResponse<ChatConversationSummaryResponse>>> list(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Conversations",
                chatConversationService.listConversations(resolveActorUserId(), requesterAccountHolderId, page, size)
        ));
    }

    @PostMapping
    @Operation(summary = "Create or resolve a direct conversation")
    public ResponseEntity<CommonResponse<ChatConversationSummaryResponse>> createOrResolve(
            @Valid @RequestBody CreateOrResolveConversationRequest request) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Conversation resolved",
                chatConversationService.createOrResolveConversation(resolveActorUserId(), requesterAccountHolderId, request)
        ));
    }

    private UUID resolveActorUserId() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        return principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
    }
}
