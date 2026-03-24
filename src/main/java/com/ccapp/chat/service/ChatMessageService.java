package com.ccapp.chat.service;

import com.ccapp.chat.domain.entity.ChatConversation;
import com.ccapp.chat.domain.entity.ChatMessage;
import com.ccapp.chat.domain.entity.ChatMessageDeliveryStatus;
import com.ccapp.chat.domain.entity.ChatReadReceipt;
import com.ccapp.chat.domain.enums.ChatConversationType;
import com.ccapp.chat.domain.enums.ChatMessageStatus;
import com.ccapp.chat.dto.ChatRealtimeEvent;
import com.ccapp.chat.dto.ChatMessageResponse;
import com.ccapp.chat.dto.ChatMessagesPageResponse;
import com.ccapp.chat.dto.SendChatMessageRequest;
import com.ccapp.chat.event.ChatMessageStatusUpdatedEvent;
import com.ccapp.chat.repository.ChatConversationRepository;
import com.ccapp.chat.repository.ChatMessageDeliveryStatusRepository;
import com.ccapp.chat.repository.ChatMessageRepository;
import com.ccapp.chat.repository.ChatReadReceiptRepository;
import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circles.repository.CircleMemberRepository;
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
    private final ChatMessageDeliveryStatusRepository chatMessageDeliveryStatusRepository;
    private final ChatReadReceiptRepository chatReadReceiptRepository;
    private final ChatConversationService chatConversationService;
    private final AccountHolderService accountHolderService;
    private final AccessControlService accessControlService;
    private final CircleMemberRepository circleMemberRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public ChatMessageResponse sendMessage(UUID actorUserId, UUID senderAccountHolderId, SendChatMessageRequest request) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, senderAccountHolderId, PermissionLevel.TRANSACT);
        accountHolderService.getById(senderAccountHolderId);

        if (request.conversationId() != null) {
            ChatConversation conversation = chatConversationRepository.findById(request.conversationId())
                    .orElseThrow(() -> new NotFoundException("Conversation not found"));
            if (conversation.getType() == ChatConversationType.CIRCLE) {
                assertCircleMember(conversation, senderAccountHolderId);
                return sendCircleMessage(senderAccountHolderId, conversation, request);
            }
            // DIRECT with explicit conversationId
            assertParticipant(conversation, senderAccountHolderId);
            return persistAndPublish(senderAccountHolderId, conversation, request, normalizeClientMessageId(request.clientMessageId()));
        }

        // Legacy DIRECT: resolve by recipientAccountHolderId
        if (request.recipientAccountHolderId() == null) {
            throw new BusinessException("Either conversationId or recipientAccountHolderId is required");
        }
        accountHolderService.getById(request.recipientAccountHolderId());
        ChatConversation conversation = chatConversationService.resolveDirectConversation(
                senderAccountHolderId,
                request.recipientAccountHolderId()
        );
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
        var pageable = PageRequest.of(0, pageSize);
        var page = before == null
                ? chatMessageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                : chatMessageRepository.findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(conversationId, before, pageable);

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
        if (message.getRecipientAccountHolderId() != null
                && !recipientAccountHolderId.equals(message.getRecipientAccountHolderId())) {
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
        if (saved.getRecipientAccountHolderId() != null) {
            upsertDeliveryStatus(saved.getId(), saved.getRecipientAccountHolderId(), ChatMessageStatus.DELIVERED);
        }
        domainEventPublisher.publish(ChatMessageStatusUpdatedEvent.create(
                saved.getId(),
                saved.getConversationId(),
                saved.getMessageStatus(),
                saved.getRecipientAccountHolderId()
        ));
        publishRealtime(EVENT_MESSAGE_DELIVERED, saved);
        return toResponse(saved);
    }

    @Transactional
    public ChatMessageResponse markRead(UUID actorUserId, UUID recipientAccountHolderId, UUID messageId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, recipientAccountHolderId, PermissionLevel.VIEW);
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Chat message not found"));
        if (message.getRecipientAccountHolderId() != null
                && !recipientAccountHolderId.equals(message.getRecipientAccountHolderId())) {
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
        if (saved.getRecipientAccountHolderId() != null) {
            upsertDeliveryStatus(saved.getId(), saved.getRecipientAccountHolderId(), ChatMessageStatus.READ);
            upsertReadReceipt(saved.getId(), saved.getRecipientAccountHolderId(), now);
        }
        domainEventPublisher.publish(ChatMessageStatusUpdatedEvent.create(
                saved.getId(),
                saved.getConversationId(),
                saved.getMessageStatus(),
                saved.getRecipientAccountHolderId()
        ));
        publishRealtime(EVENT_MESSAGE_READ, saved);
        return toResponse(saved);
    }

    // -------------------------------------------------------------------------

    private ChatMessageResponse sendCircleMessage(UUID senderAccountHolderId,
                                                  ChatConversation conversation,
                                                  SendChatMessageRequest request) {
        String normalizedClientMessageId = normalizeClientMessageId(request.clientMessageId());
        if (normalizedClientMessageId != null) {
            return chatMessageRepository.findBySenderAccountHolderIdAndClientMessageId(senderAccountHolderId, normalizedClientMessageId)
                    .map(this::toResponse)
                    .orElseGet(() -> persistCircleMessage(senderAccountHolderId, conversation, request, normalizedClientMessageId));
        }
        return persistCircleMessage(senderAccountHolderId, conversation, request, null);
    }

    private ChatMessageResponse persistCircleMessage(UUID senderAccountHolderId,
                                                     ChatConversation conversation,
                                                     SendChatMessageRequest request,
                                                     String normalizedClientMessageId) {
        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .senderAccountHolderId(senderAccountHolderId)
                .recipientAccountHolderId(null)
                .conversationId(conversation.getId())
                .content(request.content().trim())
                .channel(normalizeChannel(request.channel()))
                .clientMessageId(normalizedClientMessageId)
                .messageStatus(ChatMessageStatus.SENT)
                .build());

        publishCircleRealtime(EVENT_MESSAGE_SENT, saved);
        notifyCircleMembers(saved, senderAccountHolderId, conversation.getCircleId());
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
        if (saved.getRecipientAccountHolderId() != null) {
            upsertDeliveryStatus(saved.getId(), saved.getRecipientAccountHolderId(), ChatMessageStatus.SENT);
        }
        publishRealtime(EVENT_MESSAGE_SENT, saved);

        return toResponse(saved);
    }

    private ChatMessage ensureExistingMessageConversation(ChatMessage message, UUID conversationId) {
        if (message.getConversationId() != null) {
            return message;
        }
        message.setConversationId(conversationId);
        return chatMessageRepository.save(message);
    }

    private void notifyCircleMembers(ChatMessage message, UUID senderAccountHolderId, UUID circleId) {
        circleMemberRepository.findByCircle_IdOrderByJoinedAtAsc(circleId).stream()
                .map(CircleMember::getAccountHolderId)
                .filter(memberId -> !memberId.equals(senderAccountHolderId))
                .forEach(memberId -> domainEventPublisher.publish(MessageSentEvent.create(
                        message.getId(),
                        senderAccountHolderId,
                        memberId,
                        message.getChannel()
                )));
    }

    private void assertParticipant(ChatConversation conversation, UUID accountHolderId) {
        if (conversation.getType() == ChatConversationType.CIRCLE) {
            assertCircleMember(conversation, accountHolderId);
            return;
        }
        boolean participant = accountHolderId.equals(conversation.getParticipantAAccountHolderId())
                || accountHolderId.equals(conversation.getParticipantBAccountHolderId());
        if (!participant) {
            throw new BusinessException("Account holder is not a participant of this conversation");
        }
    }

    private void assertCircleMember(ChatConversation conversation, UUID accountHolderId) {
        if (!circleMemberRepository.existsByCircle_IdAndAccountHolderId(conversation.getCircleId(), accountHolderId)) {
            throw new BusinessException("Account holder is not a member of this circle");
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    private void publishRealtime(String eventType, ChatMessage message) {
        ChatMessageResponse payload = toResponse(message);
        ChatRealtimeEvent realtimeEvent = new ChatRealtimeEvent(eventType, payload);
        messagingTemplate.convertAndSend(
                "/topic/chat/events/conversations/" + message.getConversationId(),
                realtimeEvent
        );
        if (message.getRecipientAccountHolderId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/chat/events/users/" + message.getRecipientAccountHolderId(),
                    realtimeEvent
            );
            messagingTemplate.convertAndSend(
                    "/topic/chat/users/" + message.getRecipientAccountHolderId(),
                    payload
            );
        }
        messagingTemplate.convertAndSend(
                "/topic/chat/conversations/" + message.getConversationId(),
                payload
        );
    }

    private void publishCircleRealtime(String eventType, ChatMessage message) {
        ChatMessageResponse payload = toResponse(message);
        ChatRealtimeEvent realtimeEvent = new ChatRealtimeEvent(eventType, payload);
        messagingTemplate.convertAndSend(
                "/topic/chat/events/conversations/" + message.getConversationId(),
                realtimeEvent
        );
        messagingTemplate.convertAndSend(
                "/topic/chat/conversations/" + message.getConversationId(),
                payload
        );
    }

    private void upsertDeliveryStatus(UUID messageId, UUID recipientAccountHolderId, ChatMessageStatus status) {
        ChatMessageDeliveryStatus deliveryStatus = chatMessageDeliveryStatusRepository
                .findByMessageIdAndRecipientAccountHolderId(messageId, recipientAccountHolderId)
                .orElseGet(() -> ChatMessageDeliveryStatus.builder()
                        .messageId(messageId)
                        .recipientAccountHolderId(recipientAccountHolderId)
                        .build());
        deliveryStatus.setStatus(status);
        chatMessageDeliveryStatusRepository.save(deliveryStatus);
    }

    private void upsertReadReceipt(UUID messageId, UUID readerAccountHolderId, OffsetDateTime readAt) {
        ChatReadReceipt readReceipt = chatReadReceiptRepository
                .findByMessageIdAndReaderAccountHolderId(messageId, readerAccountHolderId)
                .orElseGet(() -> ChatReadReceipt.builder()
                        .messageId(messageId)
                        .readerAccountHolderId(readerAccountHolderId)
                        .build());
        readReceipt.setReadAt(readAt);
        chatReadReceiptRepository.save(readReceipt);
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
