package com.ccapp.chat.service;

import com.ccapp.chat.domain.entity.ChatConversation;
import com.ccapp.chat.domain.entity.ChatMessage;
import com.ccapp.chat.domain.enums.ChatMessageStatus;
import com.ccapp.chat.dto.ChatConversationSummaryResponse;
import com.ccapp.chat.dto.CreateOrResolveConversationRequest;
import com.ccapp.chat.repository.ChatConversationRepository;
import com.ccapp.chat.repository.ChatMessageRepository;
import com.ccapp.common.api.PageResponse;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(ChatConversationService.class)
class ChatConversationServiceIntegrationTest {

    @Autowired
    private ChatConversationService chatConversationService;
    @Autowired
    private ChatConversationRepository chatConversationRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;

    @MockBean
    private AccountHolderService accountHolderService;
    @MockBean
    private AccessControlService accessControlService;

    @BeforeEach
    void setUp() {
        doNothing().when(accessControlService).assertCanActOnAccountHolder(any(), any(), any());
        when(accountHolderService.getById(any())).thenAnswer(invocation -> {
            UUID accountHolderId = invocation.getArgument(0);
            return accountHolderRepository.findById(accountHolderId).orElseThrow();
        });
    }

    @Test
    void createOrResolveConversation_shouldReturnExistingConversationWhenAlreadyCreated() {
        UUID actorUserId = UUID.randomUUID();
        UUID requesterId = createAccountHolder("Alice", "One");
        UUID participantId = createAccountHolder("Bob", "Two");

        var first = chatConversationService.createOrResolveConversation(
                actorUserId,
                requesterId,
                new CreateOrResolveConversationRequest(List.of(participantId))
        );
        var second = chatConversationService.createOrResolveConversation(
                actorUserId,
                requesterId,
                new CreateOrResolveConversationRequest(List.of(participantId))
        );

        assertEquals(first.conversationId(), second.conversationId());
        assertEquals(1L, chatConversationRepository.count());
    }

    @Test
    void listConversations_shouldReturnLastMessageUnreadAndLastActivity() {
        UUID actorUserId = UUID.randomUUID();
        UUID requesterId = createAccountHolder("Requester", "User");
        UUID participantId = createAccountHolder("Peer", "User");
        ChatConversation conversation = chatConversationRepository.save(ChatConversation.builder()
                .participantAAccountHolderId(requesterId)
                .participantBAccountHolderId(participantId)
                .build());

        chatMessageRepository.save(ChatMessage.builder()
                .senderAccountHolderId(participantId)
                .recipientAccountHolderId(requesterId)
                .conversationId(conversation.getId())
                .content("Hello requester")
                .channel("IN_APP_CHAT")
                .messageStatus(ChatMessageStatus.SENT)
                .build());

        chatMessageRepository.save(ChatMessage.builder()
                .senderAccountHolderId(requesterId)
                .recipientAccountHolderId(participantId)
                .conversationId(conversation.getId())
                .content("Reply")
                .channel("IN_APP_CHAT")
                .messageStatus(ChatMessageStatus.READ)
                .createdAt(OffsetDateTime.now().plusSeconds(1))
                .build());

        PageResponse<ChatConversationSummaryResponse> page = chatConversationService.listConversations(actorUserId, requesterId, 0, 20);
        assertEquals(1, page.items().size());
        ChatConversationSummaryResponse item = page.items().get(0);
        assertEquals(conversation.getId(), item.conversationId());
        assertEquals("Reply", item.lastMessage());
        assertEquals(1L, item.unreadCount());
        assertNotNull(item.lastActivity());
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
