package com.ccapp.community.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.community.domain.entity.CommunityPost;
import com.ccapp.community.dto.CreateCommunityPostRequest;
import com.ccapp.community.event.PostCreatedEvent;
import com.ccapp.community.repository.CommunityPostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityAdminServiceTest {

    @Mock
    private CommunityPostRepository communityPostRepository;
    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private CommunityAdminService communityAdminService;

    @Test
    void create_shouldPersistPostAndPublishPostCreatedEvent() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID communityId = UUID.randomUUID();

        CreateCommunityPostRequest request = new CreateCommunityPostRequest(
                "My post",
                "Post content",
                "https://cdn.example/img.jpg",
                authorId,
                communityId,
                "PUBLIC"
        );

        when(communityPostRepository.save(any(CommunityPost.class))).thenReturn(CommunityPost.builder()
                .id(postId)
                .title(request.title())
                .content(request.content())
                .mediaUrl(request.mediaUrl())
                .authorAccountHolderId(authorId)
                .communityId(communityId)
                .visibility("PUBLIC")
                .build());

        communityAdminService.create(request);

        ArgumentCaptor<PostCreatedEvent> eventCaptor = ArgumentCaptor.forClass(PostCreatedEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());
        PostCreatedEvent event = eventCaptor.getValue();
        assertEquals(postId, event.postId());
        assertEquals(authorId, event.authorAccountHolderId());
        assertEquals(communityId, event.communityId());
        assertEquals("PUBLIC", event.visibility());
    }
}
