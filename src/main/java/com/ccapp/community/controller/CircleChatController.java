package com.ccapp.community.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.community.dto.CircleChatMessageRequest;
import com.ccapp.community.dto.CircleChatMessageResponse;
import com.ccapp.community.service.CircleChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/circles")
@RequiredArgsConstructor
@Tag(name = "Circle Chat", description = "Legacy circle chat endpoints kept temporarily for compatibility")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
@Deprecated
public class CircleChatController {

    private final CircleChatService circleChatService;

    /**
     * @deprecated Legacy endpoint kept for compatibility while circle chat migrates to the canonical chat stack.
     */
    @GetMapping("/{circleId}/chat")
    @Operation(
            summary = "Historique du chat d'un cercle",
            description = "Legacy endpoint kept temporarily for compatibility. Future circle messaging will converge on the canonical chat stack.",
            deprecated = true
    )
    @Deprecated
    public ResponseEntity<CommonResponse<List<CircleChatMessageResponse>>> getMessages(
            @PathVariable UUID circleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime before,
            @RequestParam(defaultValue = "50") int limit) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        log.warn("legacy_endpoint_invoked endpointId={} accountHolderId={} circleId={} transport={} beforeProvided={} limit={}",
                "circle-chat.get-messages",
                accountHolderId,
                circleId,
                "REST",
                before != null,
                limit);
        return ResponseEntity.ok(CommonResponse.success(
                "Circle chat messages",
                circleChatService.getMessages(circleId, before, limit)
        ));
    }

    /**
     * @deprecated Legacy endpoint kept for compatibility while circle chat migrates to the canonical chat stack.
     */
    @PostMapping("/{circleId}/chat")
    @Operation(
            summary = "Envoyer un message dans le cercle",
            description = "Legacy endpoint kept temporarily for compatibility. Future circle messaging will converge on the canonical chat stack.",
            deprecated = true
    )
    @Deprecated
    public ResponseEntity<CommonResponse<CircleChatMessageResponse>> sendMessage(
            @PathVariable UUID circleId,
            @Valid @RequestBody CircleChatMessageRequest request) {
        UUID senderId = SecurityUtils.currentAccountHolderId();
        log.warn("legacy_endpoint_invoked endpointId={} accountHolderId={} circleId={} transport={} messageType={} hasMedia={} hasClientMessageId={}",
                "circle-chat.send-message",
                senderId,
                circleId,
                "REST",
                request.messageType(),
                request.mediaId() != null,
                request.clientMessageId() != null && !request.clientMessageId().isBlank());
        return ResponseEntity.ok(CommonResponse.success(
                "Message sent",
                circleChatService.sendMessage(circleId, senderId, request)
        ));
    }

    /**
     * @deprecated Legacy endpoint kept for compatibility while circle chat migrates to the canonical chat stack.
     */
    @PostMapping("/{circleId}/chat/{messageId}/read")
    @Operation(
            summary = "Marquer un message comme lu",
            description = "Legacy endpoint kept temporarily for compatibility. Future circle messaging will converge on the canonical chat stack.",
            deprecated = true
    )
    @Deprecated
    public ResponseEntity<CommonResponse<Void>> markRead(
            @PathVariable UUID circleId,
            @PathVariable UUID messageId) {
        UUID memberId = SecurityUtils.currentAccountHolderId();
        log.warn("legacy_endpoint_invoked endpointId={} accountHolderId={} circleId={} messageId={} transport={}",
                "circle-chat.mark-read",
                memberId,
                circleId,
                messageId,
                "REST");
        circleChatService.markRead(circleId, messageId, memberId);
        return ResponseEntity.ok(CommonResponse.success("Message marked as read", null));
    }

    /**
     * @deprecated Legacy endpoint kept for compatibility while circle chat migrates to the canonical chat stack.
     */
    @PostMapping("/{circleId}/chat/read-all")
    @Operation(
            summary = "Marquer tous les messages du cercle comme lus",
            description = "Legacy endpoint kept temporarily for compatibility. Future circle messaging will converge on the canonical chat stack.",
            deprecated = true
    )
    @Deprecated
    public ResponseEntity<CommonResponse<Void>> markAllRead(@PathVariable UUID circleId) {
        UUID memberId = SecurityUtils.currentAccountHolderId();
        log.warn("legacy_endpoint_invoked endpointId={} accountHolderId={} circleId={} transport={}",
                "circle-chat.mark-all-read",
                memberId,
                circleId,
                "REST");
        circleChatService.markAllRead(circleId, memberId);
        return ResponseEntity.ok(CommonResponse.success("All messages marked as read", null));
    }

    /**
     * @deprecated Legacy endpoint kept for compatibility while circle chat migrates to the canonical chat stack.
     */
    @MessageMapping("/circles/{circleId}/chat")
    @Deprecated
    public void sendMessageWs(
            @DestinationVariable UUID circleId,
            @Payload CircleChatMessageRequest request,
            Principal principal) {
        UUID senderId = resolveAccountHolderId(principal);
        log.warn("legacy_endpoint_invoked endpointId={} accountHolderId={} circleId={} transport={} messageType={} hasMedia={} hasClientMessageId={}",
                "circle-chat.send-message",
                senderId,
                circleId,
                "WS",
                request.messageType(),
                request.mediaId() != null,
                request.clientMessageId() != null && !request.clientMessageId().isBlank());
        circleChatService.sendMessage(circleId, senderId, request);
    }

    /**
     * @deprecated Legacy endpoint kept for compatibility while circle chat migrates to the canonical chat stack.
     */
    @MessageMapping("/circles/{circleId}/chat/{messageId}/read")
    @Deprecated
    public void markReadWs(
            @DestinationVariable UUID circleId,
            @DestinationVariable UUID messageId,
            Principal principal) {
        UUID memberId = resolveAccountHolderId(principal);
        log.warn("legacy_endpoint_invoked endpointId={} accountHolderId={} circleId={} messageId={} transport={}",
                "circle-chat.mark-read",
                memberId,
                circleId,
                messageId,
                "WS");
        circleChatService.markRead(circleId, messageId, memberId);
    }

    private UUID resolveAccountHolderId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthenticatedPrincipal ap) {
            if (ap.accountHolderId() != null) {
                return ap.accountHolderId();
            }
            if (ap.userId() != null) {
                return ap.userId();
            }
        }
        throw new IllegalStateException("Cannot resolve accountHolderId from WebSocket principal");
    }
}
