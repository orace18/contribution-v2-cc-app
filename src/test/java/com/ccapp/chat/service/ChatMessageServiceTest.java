package com.ccapp.chat.service;

import com.ccapp.chat.domain.entity.ChatConversation;
import com.ccapp.chat.domain.entity.ChatMessage;
import com.ccapp.chat.dto.SendChatMessageRequest;
import com.ccapp.chat.repository.ChatConversationRepository;
import com.ccapp.chat.repository.ChatMessageRepository;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.notifications.event.MessageSentEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatConversationRepository chatConversationRepository;
    @Mock
    private AccountHolderService accountHolderService;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Test
    void sendMessage_shouldPersistAndPublishMessageSentEvent() {
        UUID actorUserId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        SendChatMessageRequest request = new SendChatMessageRequest(
                recipientId,
                "Hello",
                "IN_APP_CHAT",
                "client-1"
        );

        when(chatConversationRepository.findByParticipantAAccountHolderIdAndParticipantBAccountHolderId(any(), any()))
                .thenReturn(Optional.of(ChatConversation.builder()
                        .id(conversationId)
                        .participantAAccountHolderId(senderId)
                        .participantBAccountHolderId(recipientId)
                        .build()));
        doNothing().when(accessControlService).assertCanActOnAccountHolder(any(), any(), any());
        when(accountHolderService.getById(any())).thenReturn(new AccountHolder());
        when(chatMessageRepository.findBySenderAccountHolderIdAndClientMessageId(senderId, "client-1"))
                .thenReturn(Optional.empty());
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            saved.setId(messageId);
            return saved;
        });

        chatMessageService.sendMessage(actorUserId, senderId, request);

        ArgumentCaptor<MessageSentEvent> eventCaptor = ArgumentCaptor.forClass(MessageSentEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());
        MessageSentEvent event = eventCaptor.getValue();
        assertEquals(messageId, event.messageId());
        assertEquals(senderId, event.senderAccountHolderId());
        assertEquals(recipientId, event.recipientAccountHolderId());
        assertEquals("IN_APP_CHAT", event.channel());
    }

    @Test
    void sendMessage_shouldBeIdempotentByClientMessageId() {
        UUID actorUserId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        ChatMessage existing = ChatMessage.builder()
                .id(UUID.randomUUID())
                .senderAccountHolderId(senderId)
                .recipientAccountHolderId(recipientId)
                .conversationId(conversationId)
                .content("Already sent")
                .channel("IN_APP_CHAT")
                .clientMessageId("client-1")
                .build();

        when(chatConversationRepository.findByParticipantAAccountHolderIdAndParticipantBAccountHolderId(any(), any()))
                .thenReturn(Optional.of(ChatConversation.builder()
                        .id(conversationId)
                        .participantAAccountHolderId(senderId)
                        .participantBAccountHolderId(recipientId)
                        .build()));
        doNothing().when(accessControlService).assertCanActOnAccountHolder(any(), any(), any());
        when(accountHolderService.getById(any())).thenReturn(new AccountHolder());
        when(chatMessageRepository.findBySenderAccountHolderIdAndClientMessageId(senderId, "client-1"))
                .thenReturn(Optional.of(existing));

        chatMessageService.sendMessage(actorUserId, senderId, new SendChatMessageRequest(
                recipientId,
                "Hello",
                "IN_APP_CHAT",
                "client-1"
        ));

        verify(chatMessageRepository, never()).save(any());
        verify(domainEventPublisher, never()).publish(any());
    }
}
