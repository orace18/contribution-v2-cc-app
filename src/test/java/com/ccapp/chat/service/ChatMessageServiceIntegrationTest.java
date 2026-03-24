package com.ccapp.chat.service;

import com.ccapp.chat.domain.entity.ChatMessage;
import com.ccapp.chat.domain.enums.ChatMessageStatus;
import com.ccapp.chat.dto.ChatMessageResponse;
import com.ccapp.chat.dto.ChatRealtimeEvent;
import com.ccapp.chat.dto.ChatMessagesPageResponse;
import com.ccapp.chat.dto.SendChatMessageRequest;
import com.ccapp.chat.repository.ChatConversationRepository;
import com.ccapp.chat.repository.ChatMessageRepository;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.service.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({ChatMessageService.class, ChatConversationService.class})
class ChatMessageServiceIntegrationTest {

    @Autowired
    private ChatMessageService chatMessageService;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private ChatConversationRepository chatConversationRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;

    @MockBean
    private AccountHolderService accountHolderService;
    @MockBean
    private AccessControlService accessControlService;
    @MockBean
    private DomainEventPublisher domainEventPublisher;
    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void setUp() {
        doNothing().when(accessControlService).assertCanActOnAccountHolder(any(), any(), any());
        when(accountHolderService.getById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return accountHolderRepository.findById(id).orElseThrow();
        });
    }

    @Test
    void paginationAndStatusUpdates_shouldWorkWithConversationModel() {
        UUID actorUserId = UUID.randomUUID();
        UUID senderId = createAccountHolder("Sender", "One");
        UUID recipientId = createAccountHolder("Recipient", "One");

        var first = chatMessageService.sendMessage(actorUserId, senderId, new SendChatMessageRequest(
                null,
                recipientId,
                "Hello 1",
                "IN_APP_CHAT",
                "client-1"
        ));
        var second = chatMessageService.sendMessage(actorUserId, senderId, new SendChatMessageRequest(
                null,
                recipientId,
                "Hello 2",
                "IN_APP_CHAT",
                "client-2"
        ));

        assertNotNull(first.conversationId());
        assertEquals(first.conversationId(), second.conversationId());
        assertEquals(2L, chatMessageRepository.count());
        assertEquals(1L, chatConversationRepository.count());

        ChatMessagesPageResponse page = chatMessageService.getMessages(
                actorUserId,
                senderId,
                first.conversationId(),
                null,
                1
        );
        assertEquals(1, page.messages().size());
        assertNotNull(page.nextBefore());

        var delivered = chatMessageService.markDelivered(actorUserId, recipientId, first.id());
        assertEquals(ChatMessageStatus.DELIVERED, delivered.messageStatus());
        assertNotNull(delivered.deliveredAt());

        var read = chatMessageService.markRead(actorUserId, recipientId, first.id());
        assertEquals(ChatMessageStatus.READ, read.messageStatus());
        assertNotNull(read.readAt());

        ChatMessage persisted = chatMessageRepository.findById(first.id()).orElseThrow();
        assertEquals(ChatMessageStatus.READ, persisted.getMessageStatus());
        verify(messagingTemplate, atLeastOnce()).convertAndSend(contains("/topic/chat/events/conversations/"), any(ChatRealtimeEvent.class));
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/chat/conversations/" + first.conversationId()), any(ChatMessageResponse.class));
    }

    private UUID createAccountHolder(String firstName, String lastName) {
        return accountHolderRepository.save(AccountHolder.builder()
                .id(UUID.randomUUID())
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber("229" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.STANDARD)
                .build()).getId();
    }
}
