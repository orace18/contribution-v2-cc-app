package com.ccapp.circles.service;

import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.entity.CircleInvite;
import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circles.domain.enums.CircleMemberRole;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.dto.*;
import com.ccapp.circles.repository.CircleInviteRepository;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.notifications.dispatcher.PhoneMessageDispatchResult;
import com.ccapp.notifications.dispatcher.PhoneMessageGateway;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({CircleInviteService.class, TestJacksonAndCachingConfig.class})
class CircleInviteServiceIntegrationTest {

    @Autowired
    private CircleInviteService circleInviteService;
    @Autowired
    private CircleRepository circleRepository;
    @Autowired
    private CircleMemberRepository circleMemberRepository;
    @Autowired
    private CircleInviteRepository circleInviteRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;

    @MockBean
    private CircleService circleService;
    @MockBean
    private PhoneMessageGateway phoneMessageGateway;
    @MockBean
    private CountryConfigResolver countryConfigResolver;

    @BeforeEach
    void setUp() {
        when(phoneMessageGateway.sendWhatsApp(any(), any()))
                .thenReturn(new PhoneMessageDispatchResult(true, "whatsapp:accepted"));
        when(countryConfigResolver.resolveActiveConfig(any(), any())).thenAnswer(invocation -> new CountryConfigResolver.ResolvedConfig(
                invocation.getArgument(0),
                invocation.getArgument(1),
                null,
                "{}",
                CountryConfigResolver.ConfigSource.EMPTY
        ));
        when(countryConfigResolver.payloadAsObjectNode(any(CountryConfigResolver.ResolvedConfig.class)))
                .thenReturn(JsonNodeFactory.instance.objectNode());
    }

    @Test
    void createInviteAndQr_shouldReturnLinkAndPayload() {
        UUID actorId = createAccountHolder("Actor");
        Circle circle = createCircle(actorId);

        CircleInviteResponse invite = circleInviteService.createInvite(
                actorId,
                circle.getId(),
                new CreateCircleInviteRequest(5, 24)
        );
        assertNotNull(invite.id());
        assertEquals(4, invite.maxUses());
        assertTrue(invite.inviteLink().contains(invite.invitationToken()));

        CircleInviteQrResponse qr = circleInviteService.getInviteQr(actorId, circle.getId(), invite.id());
        assertEquals(invite.id(), qr.inviteId());
        assertTrue(qr.qrPayload().contains(invite.invitationToken()));
    }

    @Test
    void sendAndJoinViaToken_shouldUseGatewayAndIncrementUsage() {
        UUID actorId = createAccountHolder("Actor");
        Circle circle = createCircle(actorId);
        CircleInviteResponse invite = circleInviteService.createInvite(
                actorId,
                circle.getId(),
                new CreateCircleInviteRequest(2, 24)
        );

        SendCircleInviteWhatsAppResponse sent = circleInviteService.sendInviteViaWhatsApp(
                actorId,
                circle.getId(),
                invite.id(),
                new SendCircleInviteWhatsAppRequest("+22996007373", null)
        );
        assertTrue(sent.delivered());
        verify(phoneMessageGateway).sendWhatsApp(eq("+22996007373"), any());

        CircleMemberResponse joinResponse = new CircleMemberResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                CircleMemberRole.MEMBER,
                "Invited Member",
                null,
                "",
                "STANDARD",
                null,
                null,
                null,
                null,
                OffsetDateTime.now()
        );
        when(circleService.joinCircle(any(), eq(circle.getId()), any()))
                .thenReturn(joinResponse);

        CircleMemberResponse joined = circleInviteService.joinViaInviteToken(
                UUID.randomUUID(),
                invite.invitationToken(),
                new JoinCircleRequest(null, null, null)
        );
        assertEquals(joinResponse.id(), joined.id());

        CircleInvite persisted = circleInviteRepository.findById(invite.id()).orElseThrow();
        assertEquals(1, persisted.getUsedCount());
    }

    private UUID createAccountHolder(String suffix) {
        return accountHolderRepository.save(AccountHolder.builder()
                .id(UUID.randomUUID())
                .firstName("First" + suffix)
                .lastName("Last" + suffix)
                .phoneNumber("229" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.STANDARD)
                .build()).getId();
    }

    private Circle createCircle(UUID actorId) {
        Circle circle = circleRepository.save(Circle.builder()
                .name("Les Batisseurs")
                .coachId(actorId)
                .countryCode("BJ")
                .status(CircleStatus.ACTIVE)
                .build());

        circleMemberRepository.save(CircleMember.builder()
                .circle(circle)
                .accountHolderId(actorId)
                .role(CircleMemberRole.MEMBER)
                .financialMember(true)
                .joinedAt(OffsetDateTime.now())
                .build());
        return circle;
    }
}
