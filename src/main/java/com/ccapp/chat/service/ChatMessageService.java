package com.ccapp.chat.service;

import com.ccapp.chat.domain.entity.ChatConversation;
import com.ccapp.chat.domain.entity.ChatMessage;
import com.ccapp.chat.domain.enums.ChatMessageStatus;
import com.ccapp.chat.dto.ChatRealtimeEvent;
import com.ccapp.chat.dto.ChatMessageResponse;
import com.ccapp.chat.dto.ChatMessagesPageResponse;
import com.ccapp.chat.dto.SendChatMessageRequest;
import com.ccapp.chat.event.ChatMessageStatusUpdatedEvent;
import com.ccapp.chat.repository.ChatConversationRepository;
import com.ccapp.chat.repository.ChatMessageRepository;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.notifications.event.MessageSentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String EVENT_MESSAGE_SENT = "chat.message.sent";
    private static final String EVENT_MESSAGE_DELIVERED = "chat.message.delivered";
    private static final String EVENT_MESSAGE_READ = "chat.message.read";

    private final ChatMessageRepository chatMessageRepository;
    private final ChatConversationRepository chatConversationRepository;
    private final AccountHolderService accountHolderService;
    private final AccessControlService accessControlService;
    private final DomainEventPublisher domainEventPublisher;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public ChatMessageResponse sendMessage(UUID actorUserId, UUID senderAccountHolderId, SendChatMessageRequest request) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, senderAccountHolderId, PermissionLevel.TRANSACT);
        accountHolderService.getById(senderAccountHolderId);
        accountHolderService.getById(request.recipientAccountHolderId());

        ChatConversation conversation = resolveConversation(senderAccountHolderId, request.recipientAccountHolderId());
        String normalizedClientMessageId = normalizeClientMessageId(request.clientMessageId());
        if (normalizedClientMessageId != null) {
            return chatMessageRepository.findBySenderAccountHolderIdAndClientMessageId(senderAccountHolderId, normalizedClientMessageId)
                    .map(existing -> ensureExistingMessageConversation(existing, conversation.getId()))
                    .map(this::toResponse)
                    .orElseGet(() -> persistAndPublish(senderAccountHolderId, conversation, request, normalizedClientMessageId));
        }

        return persistAndPublish(senderAccountHolderId, conversation, request, null);
    }

    @Transactional(readOnly = true)
    public ChatMessagesPageResponse getMessages(UUID actorUserId,
                                                UUID requesterAccountHolderId,
                                                UUID conversationId,
                                                OffsetDateTime before,
                                                Integer limit) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, requesterAccountHolderId, PermissionLevel.VIEW);
        ChatConversation conversation = chatConversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
        assertParticipant(conversation, requesterAccountHolderId);

        int pageSize = normalizeLimit(limit);
        OffsetDateTime beforeCursor = sanitizeBeforeCursor(before);
        var pageable = PageRequest.of(0, pageSize);
        var page = beforeCursor == null
                ? chatMessageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                : chatMessageRepository.findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(conversationId, beforeCursor, pageable);

        OffsetDateTime nextBefore = page.hasNext() && !page.getContent().isEmpty()
                ? page.getContent().get(page.getContent().size() - 1).getCreatedAt()
                : null;
        return new ChatMessagesPageResponse(
                conversationId,
                page.getContent().stream().map(this::toResponse).toList(),
                nextBefore
        );
    }

    @Transactional
    public ChatMessageResponse markDelivered(UUID actorUserId, UUID recipientAccountHolderId, UUID messageId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, recipientAccountHolderId, PermissionLevel.VIEW);
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Chat message not found"));
        if (!recipientAccountHolderId.equals(message.getRecipientAccountHolderId())) {
            throw new BusinessException("Only recipient can mark message as delivered");
        }
        if (message.getMessageStatus() == ChatMessageStatus.READ
                || message.getMessageStatus() == ChatMessageStatus.DELIVERED) {
            return toResponse(message);
        }

        OffsetDateTime now = OffsetDateTime.now();
        message.setMessageStatus(ChatMessageStatus.DELIVERED);
        message.setDeliveredAt(now);
        ChatMessage saved = chatMessageRepository.save(message);
        domainEventPublisher.publish(ChatMessageStatusUpdatedEvent.create(
                saved.getId(),
                saved.getConversationId(),
                saved.getMessageStatus(),
                saved.getRecipientAccountHolderId()
        ));
        publishRealtime(saved, EVENT_MESSAGE_DELIVERED);
        return toResponse(saved);
    }

    @Transactional
    public ChatMessageResponse markRead(UUID actorUserId, UUID recipientAccountHolderId, UUID messageId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, recipientAccountHolderId, PermissionLevel.VIEW);
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Chat message not found"));
        if (!recipientAccountHolderId.equals(message.getRecipientAccountHolderId())) {
            throw new BusinessException("Only recipient can mark message as read");
        }
        if (message.getMessageStatus() == ChatMessageStatus.READ) {
            return toResponse(message);
        }

        OffsetDateTime now = OffsetDateTime.now();
        message.setMessageStatus(ChatMessageStatus.READ);
        if (message.getDeliveredAt() == null) {
            message.setDeliveredAt(now);
        }
        message.setReadAt(now);
        ChatMessage saved = chatMessageRepository.save(message);
        domainEventPublisher.publish(ChatMessageStatusUpdatedEvent.create(
                saved.getId(),
                saved.getConversationId(),
                saved.getMessageStatus(),
                saved.getRecipientAccountHolderId()
        ));
        publishRealtime(saved, EVENT_MESSAGE_READ);
        return toResponse(saved);
    }

    private ChatMessageResponse persistAndPublish(UUID senderAccountHolderId,
                                                  ChatConversation conversation,
                                                  SendChatMessageRequest request,
                                                  String normalizedClientMessageId) {
        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .senderAccountHolderId(senderAccountHolderId)
                .recipientAccountHolderId(request.recipientAccountHolderId())
                .conversationId(conversation.getId())
                .content(request.content().trim())
                .channel(normalizeChannel(request.channel()))
                .clientMessageId(normalizedClientMessageId)
                .messageStatus(ChatMessageStatus.SENT)
                .build());

        domainEventPublisher.publish(MessageSentEvent.create(
                saved.getId(),
                saved.getSenderAccountHolderId(),
                saved.getRecipientAccountHolderId(),
                saved.getChannel()
        ));
        publishRealtime(saved, EVENT_MESSAGE_SENT);

        return toResponse(saved);
    }

    private ChatConversation resolveConversation(UUID senderAccountHolderId, UUID recipientAccountHolderId) {
        UUID participantA = senderAccountHolderId.compareTo(recipientAccountHolderId) <= 0
                ? senderAccountHolderId
                : recipientAccountHolderId;
        UUID participantB = participantA.equals(senderAccountHolderId)
                ? recipientAccountHolderId
                : senderAccountHolderId;

        return chatConversationRepository
                .findByParticipantAAccountHolderIdAndParticipantBAccountHolderId(participantA, participantB)
                .orElseGet(() -> chatConversationRepository.save(ChatConversation.builder()
                        .participantAAccountHolderId(participantA)
                        .participantBAccountHolderId(participantB)
                        .build()));
    }

    private ChatMessage ensureExistingMessageConversation(ChatMessage message, UUID conversationId) {
        if (message.getConversationId() != null) {
            return message;
        }
        message.setConversationId(conversationId);
        return chatMessageRepository.save(message);
    }

    private void assertParticipant(ChatConversation conversation, UUID accountHolderId) {
        boolean participant = accountHolderId.equals(conversation.getParticipantAAccountHolderId())
                || accountHolderId.equals(conversation.getParticipantBAccountHolderId());
        if (!participant) {
            throw new BusinessException("Account holder is not a participant of this conversation");
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    private OffsetDateTime sanitizeBeforeCursor(OffsetDateTime before) {
        if (before == null) {
            return null;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (before.isAfter(now.plusMinutes(1))) {
            throw new BusinessException("Invalid pagination cursor");
        }
        return before;
    }

    private void publishRealtime(ChatMessage message, String eventType) {
        ChatRealtimeEvent event = new ChatRealtimeEvent(
                eventType,
                message.getConversationId(),
                OffsetDateTime.now(),
                toResponse(message)
        );
        messagingTemplate.convertAndSend(
                "/topic/chat/conversations/" + message.getConversationId(),
                event
        );
        messagingTemplate.convertAndSend(
                "/topic/chat/users/" + message.getRecipientAccountHolderId(),
                event
        );
    }

    private String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return "IN_APP_CHAT";
        }
        return channel.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeClientMessageId(String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            return null;
        }
        return clientMessageId.trim();
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSenderAccountHolderId(),
                message.getRecipientAccountHolderId(),
                message.getConversationId(),
                message.getContent(),
                message.getChannel(),
                message.getClientMessageId(),
                message.getMessageStatus(),
                message.getDeliveredAt(),
                message.getReadAt(),
                message.getCreatedAt()
        );
    }
}
