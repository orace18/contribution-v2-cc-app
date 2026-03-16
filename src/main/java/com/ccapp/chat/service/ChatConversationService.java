package com.ccapp.chat.service;

import com.ccapp.chat.domain.entity.ChatConversation;
import com.ccapp.chat.domain.entity.ChatMessage;
import com.ccapp.chat.domain.enums.ChatConversationType;
import com.ccapp.chat.domain.enums.ChatMessageStatus;
import com.ccapp.chat.dto.ChatConversationSummaryResponse;
import com.ccapp.chat.dto.CreateOrResolveConversationRequest;
import com.ccapp.chat.repository.ChatConversationRepository;
import com.ccapp.chat.repository.ChatMessageRepository;
import com.ccapp.common.api.PageResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatConversationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final List<ChatMessageStatus> UNREAD_STATUSES = List.of(ChatMessageStatus.SENT, ChatMessageStatus.DELIVERED);

    private final ChatConversationRepository chatConversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AccountHolderService accountHolderService;
    private final AccessControlService accessControlService;
    private final AppUserRepository appUserRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional(readOnly = true)
    public PageResponse<ChatConversationSummaryResponse> listConversations(UUID actorUserId,
                                                                           UUID requesterAccountHolderId,
                                                                           Integer page,
                                                                           Integer size) {
        accountHolderService.getById(requesterAccountHolderId);
        accessControlService.assertCanActOnAccountHolder(actorUserId, requesterAccountHolderId, PermissionLevel.VIEW);
        Pageable pageable = normalizePageable(page, size);

        Page<ChatConversationRepository.ConversationSummaryProjection> rawPage =
                chatConversationRepository.findConversationSummariesByParticipant(requesterAccountHolderId, pageable);

        Page<ChatConversationSummaryResponse> mappedPage = rawPage.map(summary -> {
                    UUID conversationId = UUID.fromString(summary.getConversationId());
                    UUID participantA = UUID.fromString(summary.getParticipantAAccountHolderId());
                    UUID participantB = UUID.fromString(summary.getParticipantBAccountHolderId());
                    return toConversationSummary(
                            conversationId,
                            participantA,
                            participantB,
                            requesterAccountHolderId,
                            summary.getLastActivity()
                    );
                }
        );

        return PageResponse.from(mappedPage);
    }

    @Transactional
    public ChatConversationSummaryResponse createOrResolveConversation(UUID actorUserId,
                                                                       UUID requesterAccountHolderId,
                                                                       CreateOrResolveConversationRequest request) {
        accountHolderService.getById(requesterAccountHolderId);
        accessControlService.assertCanActOnAccountHolder(actorUserId, requesterAccountHolderId, PermissionLevel.VIEW);

        if (request.participants().size() != 1) {
            throw new BusinessException("Exactly one participant is required");
        }

        UUID participantId = request.participants().get(0);
        if (participantId.equals(requesterAccountHolderId)) {
            throw new BusinessException("Self conversation is not allowed");
        }
        accountHolderService.getById(participantId);

        UUID participantA = requesterAccountHolderId.compareTo(participantId) <= 0 ? requesterAccountHolderId : participantId;
        UUID participantB = participantA.equals(requesterAccountHolderId) ? participantId : requesterAccountHolderId;

        ChatConversation conversation = resolveDirectConversation(requesterAccountHolderId, participantId);

        OffsetDateTime fallbackLastActivity = conversation.getCreatedAt();
        return toConversationSummary(
                conversation.getId(),
                conversation.getParticipantAAccountHolderId(),
                conversation.getParticipantBAccountHolderId(),
                requesterAccountHolderId,
                fallbackLastActivity
        );
    }

    @Transactional
    public ChatConversation resolveDirectConversation(UUID participantOneAccountHolderId,
                                                      UUID participantTwoAccountHolderId) {
        UUID participantA = participantOneAccountHolderId.compareTo(participantTwoAccountHolderId) <= 0
                ? participantOneAccountHolderId
                : participantTwoAccountHolderId;
        UUID participantB = participantA.equals(participantOneAccountHolderId)
                ? participantTwoAccountHolderId
                : participantOneAccountHolderId;

        return chatConversationRepository
                .findByParticipantAAccountHolderIdAndParticipantBAccountHolderId(participantA, participantB)
                .orElseGet(() -> chatConversationRepository.save(ChatConversation.builder()
                        .participantAAccountHolderId(participantA)
                        .participantBAccountHolderId(participantB)
                        .build()));
    }

    private ChatConversationSummaryResponse toConversationSummary(UUID conversationId,
                                                                  UUID participantAAccountHolderId,
                                                                  UUID participantBAccountHolderId,
                                                                  UUID requesterAccountHolderId,
                                                                  OffsetDateTime fallbackLastActivity) {
        UUID otherParticipantId = requesterAccountHolderId.equals(participantAAccountHolderId)
                ? participantBAccountHolderId
                : participantAAccountHolderId;
        AccountHolder otherParticipant = accountHolderService.getById(otherParticipantId);

        String title = displayName(otherParticipant);
        String avatar = appUserRepository.findFirstByAccountHolderIdOrderByCreatedAtAsc(otherParticipantId)
                .flatMap(appUser -> userProfileRepository.findByUserId(appUser.getId()))
                .map(profile -> profile.getProfilePhotoMediaId() != null ? profile.getProfilePhotoMediaId().toString() : null)
                .orElse(null);

        ChatMessage lastMessage = chatMessageRepository.findTopByConversationIdOrderByCreatedAtDesc(conversationId).orElse(null);
        long unreadCount = chatMessageRepository.countByConversationIdAndRecipientAccountHolderIdAndMessageStatusIn(
                conversationId,
                requesterAccountHolderId,
                UNREAD_STATUSES
        );
        OffsetDateTime lastActivity = lastMessage != null ? lastMessage.getCreatedAt() : fallbackLastActivity;

        return new ChatConversationSummaryResponse(
                conversationId,
                ChatConversationType.DIRECT,
                title,
                avatar,
                lastMessage != null ? lastMessage.getContent() : null,
                lastActivity,
                unreadCount
        );
    }

    private String displayName(AccountHolder accountHolder) {
        String first = accountHolder.getFirstName() == null ? "" : accountHolder.getFirstName().trim();
        String last = accountHolder.getLastName() == null ? "" : accountHolder.getLastName().trim();
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? "Unknown" : combined;
    }

    private Pageable normalizePageable(Integer page, Integer size) {
        int normalizedPage = page == null || page < 0 ? 0 : page;
        int normalizedSize = size == null || size <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(normalizedPage, normalizedSize);
    }
}
