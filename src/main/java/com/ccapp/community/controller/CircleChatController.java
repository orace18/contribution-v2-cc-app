package com.ccapp.community.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.ccapp.community.dto.CircleChatMessageRequest;
import com.ccapp.community.dto.CircleChatMessageResponse;
import com.ccapp.community.service.CircleChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/circles")
@RequiredArgsConstructor
@Tag(name = "Circle Chat", description = "Chat de groupe en temps réel dans les cercles")
@SecurityRequirement(name = "bearerAuth")
public class CircleChatController {

    private final CircleChatService circleChatService;

    /**
     * Historique paginé (cursor-based) — du plus récent au plus ancien.
     * Utiliser before=sentAt du dernier message reçu pour charger la suite.
     */
    @GetMapping("/{circleId}/chat")
    @Operation(summary = "Historique du chat d'un cercle")
    public ResponseEntity<CommonResponse<List<CircleChatMessageResponse>>> getMessages(
            @PathVariable UUID circleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime before,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(CommonResponse.success(
                "Circle chat messages",
                circleChatService.getMessages(circleId, before, limit)
        ));
    }

    /**
     * Envoi d'un message REST (texte, image, vidéo, audio).
     * Le message est aussi broadcasté via WebSocket à tous les membres connectés.
     */
    @PostMapping("/{circleId}/chat")
    @Operation(summary = "Envoyer un message dans le cercle (texte, image, vidéo, audio)")
    public ResponseEntity<CommonResponse<CircleChatMessageResponse>> sendMessage(
            @PathVariable UUID circleId,
            @Valid @RequestBody CircleChatMessageRequest request) {
        UUID senderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Message sent",
                circleChatService.sendMessage(circleId, senderId, request)
        ));
    }

    /**
     * Marquer un message spécifique comme lu.
     * Déclenche un READ_RECEIPT WebSocket vers tous les membres connectés.
     */
    @PostMapping("/{circleId}/chat/{messageId}/read")
    @Operation(summary = "Marquer un message comme lu")
    public ResponseEntity<CommonResponse<Void>> markRead(
            @PathVariable UUID circleId,
            @PathVariable UUID messageId) {
        UUID memberId = SecurityUtils.currentAccountHolderId();
        circleChatService.markRead(circleId, messageId, memberId);
        return ResponseEntity.ok(CommonResponse.success("Message marked as read", null));
    }

    /**
     * Marquer tous les messages non lus comme lus (à appeler à l'ouverture du chat).
     */
    @PostMapping("/{circleId}/chat/read-all")
    @Operation(summary = "Marquer tous les messages du cercle comme lus")
    public ResponseEntity<CommonResponse<Void>> markAllRead(@PathVariable UUID circleId) {
        UUID memberId = SecurityUtils.currentAccountHolderId();
        circleChatService.markAllRead(circleId, memberId);
        return ResponseEntity.ok(CommonResponse.success("All messages marked as read", null));
    }

    // ─── WebSocket ───────────────────────────────────────────────────────────

    /**
     * Endpoint WebSocket STOMP pour envoyer un message en temps réel.
     * Client publie sur : /app/circles/{circleId}/chat
     * Tous les membres reçoivent sur : /topic/circles/{circleId}/chat
     */
    @MessageMapping("/circles/{circleId}/chat")
    public void sendMessageWs(
            @DestinationVariable UUID circleId,
            @Payload CircleChatMessageRequest request,
            Principal principal) {
        UUID senderId = resolveAccountHolderId(principal);
        circleChatService.sendMessage(circleId, senderId, request);
    }

    /**
     * Endpoint WebSocket STOMP pour marquer un message comme lu.
     * Client publie sur : /app/circles/{circleId}/chat/{messageId}/read
     */
    @MessageMapping("/circles/{circleId}/chat/{messageId}/read")
    public void markReadWs(
            @DestinationVariable UUID circleId,
            @DestinationVariable UUID messageId,
            Principal principal) {
        UUID memberId = resolveAccountHolderId(principal);
        circleChatService.markRead(circleId, messageId, memberId);
    }

    private UUID resolveAccountHolderId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthenticatedPrincipal ap) {
            if (ap.accountHolderId() != null) return ap.accountHolderId();
            if (ap.userId() != null) return ap.userId();
        }
        throw new IllegalStateException("Cannot resolve accountHolderId from WebSocket principal");
    }
}
