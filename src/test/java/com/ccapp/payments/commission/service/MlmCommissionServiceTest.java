package com.ccapp.payments.commission.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.financial.audit.service.FinancialAuditService;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.payments.commission.domain.entity.MlmCommission;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import com.ccapp.payments.event.CommissionGeneratedEvent;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import com.ccapp.payments.subscription.repository.MembershipRepository;
import com.ccapp.payments.subscription.repository.SubscriptionPlanRepository;
import com.ccapp.platform.config.service.GlobalMlmConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MlmCommissionServiceTest {

    @Mock
    private MlmCommissionRepository mlmCommissionRepository;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private FinancialAuditService financialAuditService;
    @Mock
    private CommissionEventRateLimiter commissionEventRateLimiter;
    @Mock
    private MlmRelationshipRepository mlmRelationshipRepository;
    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private CountryConfigResolver countryConfigResolver;
    @Mock
    private GlobalMlmConfigService globalMlmConfigService;

    @InjectMocks
    private MlmCommissionService mlmCommissionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mlmCommissionService, "commissionSpikeThreshold", new BigDecimal("10000"));
        ReflectionTestUtils.setField(mlmCommissionService, "commissionGenerationMaxPerMinute", 2000);
        when(commissionEventRateLimiter.allow(any(), anyInt())).thenReturn(true);
    }

    @Test
    void calculateForOperation_shouldCreatePendingUnilevelCommissions_forEligibleAncestors() {
        UUID operationId = UUID.randomUUID();
        UUID purchaserId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID generation1Id = UUID.randomUUID();
        UUID generation2Id = UUID.randomUUID();
        Operation operation = Operation.builder()
                .id(operationId)
                .accountHolderId(purchaserId)
                .planId(planId)
                .type(OperationType.SUBSCRIPTION)
                .amount(new BigDecimal("10000.00"))
                .currency("XOF")
                .build();

        when(subscriptionPlanRepository.findById(planId))
                .thenReturn(Optional.of(SubscriptionPlan.builder()
                        .id(planId)
                        .country("BJ")
                        .currency("XOF")
                        .grade(SubscriptionGrade.CLASSIC)
                        .durationMonths(1)
                        .price(new BigDecimal("10000.00"))
                        .active(true)
                        .code("CLASSIC_1M")
                        .name("Classic 1 month")
                        .build()));

        CountryConfigResolver.ResolvedConfig generalConfig = new CountryConfigResolver.ResolvedConfig(
                "BJ", CountryConfigType.GENERAL, 1, "{\"percentages\":{\"commissionPool\":60}}",
                CountryConfigResolver.ConfigSource.COUNTRY
        );
        CountryConfigResolver.ResolvedConfig mlmLimitsConfig = new CountryConfigResolver.ResolvedConfig(
                "BJ", CountryConfigType.MLM_LIMITS, 1, "{\"weeklyCapsByTier\":[{\"tier\":\"CLASSIC\",\"cap\":5000},{\"tier\":\"GOLD\",\"cap\":5000}]}",
                CountryConfigResolver.ConfigSource.COUNTRY
        );
        when(countryConfigResolver.resolveActiveConfig("BJ", CountryConfigType.GENERAL)).thenReturn(generalConfig);
        when(countryConfigResolver.resolveActiveConfig("BJ", CountryConfigType.MLM_LIMITS)).thenReturn(mlmLimitsConfig);
        when(countryConfigResolver.payloadAsObjectNode(generalConfig)).thenReturn(generalPayload());
        when(countryConfigResolver.payloadAsObjectNode(mlmLimitsConfig)).thenReturn(mlmLimitsPayload("CLASSIC", "GOLD"));
        when(countryConfigResolver.resolveActivePlatformConfig(any())).thenReturn(new CountryConfigResolver.ResolvedPlatformConfig(
                null,
                1,
                "{}"
        ));
        when(globalMlmConfigService.resolveActiveConfig()).thenReturn(globalMlmConfig());

        when(mlmRelationshipRepository.findAncestors(purchaserId)).thenReturn(List.of(
                MlmRelationship.builder().ancestorId(generation1Id).descendantId(purchaserId).level(1).build(),
                MlmRelationship.builder().ancestorId(generation2Id).descendantId(purchaserId).level(2).build()
        ));
        when(mlmCommissionRepository.existsByOperationIdAndBeneficiaryAccountHolderIdAndGeneration(operationId, generation1Id, 1)).thenReturn(false);
        when(mlmCommissionRepository.existsByOperationIdAndBeneficiaryAccountHolderIdAndGeneration(operationId, generation2Id, 2)).thenReturn(false);
        when(membershipRepository.findFirstByAccountHolderIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                eq(generation1Id), eq(MembershipStatus.ACTIVE), any(OffsetDateTime.class)
        )).thenReturn(Optional.of(activeMembership(generation1Id, SubscriptionGrade.CLASSIC)));
        when(membershipRepository.findFirstByAccountHolderIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                eq(generation2Id), eq(MembershipStatus.ACTIVE), any(OffsetDateTime.class)
        )).thenReturn(Optional.of(activeMembership(generation2Id, SubscriptionGrade.GOLD)));
        when(mlmCommissionRepository.sumAmountByBeneficiaryAccountHolderIdAndWeekIdAndCommissionType(eq(generation1Id), anyInt(), eq(CommissionType.SUBSCRIPTION)))
                .thenReturn(BigDecimal.ZERO);
        when(mlmCommissionRepository.sumAmountByBeneficiaryAccountHolderIdAndWeekIdAndCommissionType(eq(generation2Id), anyInt(), eq(CommissionType.SUBSCRIPTION)))
                .thenReturn(BigDecimal.ZERO);
        when(mlmCommissionRepository.save(any(MlmCommission.class))).thenAnswer(invocation -> {
            MlmCommission commission = invocation.getArgument(0);
            commission.setId(UUID.randomUUID());
            return commission;
        });

        mlmCommissionService.calculateForOperation(operation);

        ArgumentCaptor<MlmCommission> commissionCaptor = ArgumentCaptor.forClass(MlmCommission.class);
        verify(mlmCommissionRepository, times(2)).save(commissionCaptor.capture());
        List<MlmCommission> saved = commissionCaptor.getAllValues();

        assertEquals(generation1Id, saved.get(0).getBeneficiaryAccountHolderId());
        assertEquals(1, saved.get(0).getGeneration());
        assertEquals(new BigDecimal("1800.0000"), saved.get(0).getAmount());
        assertEquals("SUBSCRIPTION_UNILEVEL", saved.get(0).getSource());

        assertEquals(generation2Id, saved.get(1).getBeneficiaryAccountHolderId());
        assertEquals(2, saved.get(1).getGeneration());
        assertEquals(new BigDecimal("600.0000"), saved.get(1).getAmount());

        ArgumentCaptor<CommissionGeneratedEvent> eventCaptor = ArgumentCaptor.forClass(CommissionGeneratedEvent.class);
        verify(domainEventPublisher, times(2)).publish(eventCaptor.capture());
        assertEquals("SUBSCRIPTION_UNILEVEL", eventCaptor.getAllValues().get(0).source());
        assertEquals("SUBSCRIPTION_UNILEVEL", eventCaptor.getAllValues().get(1).source());
    }

    @Test
    void calculateForOperation_shouldSkipAncestor_whenWeeklyCapIsReached() {
        UUID operationId = UUID.randomUUID();
        UUID purchaserId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID generation1Id = UUID.randomUUID();
        Operation operation = Operation.builder()
                .id(operationId)
                .accountHolderId(purchaserId)
                .planId(planId)
                .type(OperationType.SUBSCRIPTION)
                .amount(new BigDecimal("10000.00"))
                .currency("XOF")
                .build();

        when(subscriptionPlanRepository.findById(planId))
                .thenReturn(Optional.of(SubscriptionPlan.builder()
                        .id(planId)
                        .country("BJ")
                        .currency("XOF")
                        .grade(SubscriptionGrade.CLASSIC)
                        .durationMonths(1)
                        .price(new BigDecimal("10000.00"))
                        .active(true)
                        .code("CLASSIC_1M")
                        .name("Classic 1 month")
                        .build()));
        CountryConfigResolver.ResolvedConfig generalConfig = new CountryConfigResolver.ResolvedConfig(
                "BJ", CountryConfigType.GENERAL, 1, "{\"percentages\":{\"commissionPool\":60}}",
                CountryConfigResolver.ConfigSource.COUNTRY
        );
        CountryConfigResolver.ResolvedConfig mlmLimitsConfig = new CountryConfigResolver.ResolvedConfig(
                "BJ", CountryConfigType.MLM_LIMITS, 1, "{\"weeklyCapsByTier\":[{\"tier\":\"CLASSIC\",\"cap\":1000}]}",
                CountryConfigResolver.ConfigSource.COUNTRY
        );
        when(countryConfigResolver.resolveActiveConfig("BJ", CountryConfigType.GENERAL)).thenReturn(generalConfig);
        when(countryConfigResolver.resolveActiveConfig("BJ", CountryConfigType.MLM_LIMITS)).thenReturn(mlmLimitsConfig);
        when(countryConfigResolver.payloadAsObjectNode(generalConfig)).thenReturn(generalPayload());
        when(countryConfigResolver.payloadAsObjectNode(mlmLimitsConfig)).thenReturn(mlmLimitsPayload("CLASSIC"));
        when(countryConfigResolver.resolveActivePlatformConfig(any())).thenReturn(new CountryConfigResolver.ResolvedPlatformConfig(
                null,
                1,
                "{}"
        ));
        when(globalMlmConfigService.resolveActiveConfig()).thenReturn(globalMlmConfig());
        when(mlmRelationshipRepository.findAncestors(purchaserId)).thenReturn(List.of(
                MlmRelationship.builder().ancestorId(generation1Id).descendantId(purchaserId).level(1).build()
        ));
        when(mlmCommissionRepository.existsByOperationIdAndBeneficiaryAccountHolderIdAndGeneration(operationId, generation1Id, 1)).thenReturn(false);
        when(membershipRepository.findFirstByAccountHolderIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                eq(generation1Id), eq(MembershipStatus.ACTIVE), any(OffsetDateTime.class)
        )).thenReturn(Optional.of(activeMembership(generation1Id, SubscriptionGrade.CLASSIC)));
        when(mlmCommissionRepository.sumAmountByBeneficiaryAccountHolderIdAndWeekIdAndCommissionType(eq(generation1Id), anyInt(), eq(CommissionType.SUBSCRIPTION)))
                .thenReturn(new BigDecimal("1000.0000"));

        mlmCommissionService.calculateForOperation(operation);

        verify(mlmCommissionRepository, never()).save(any());
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    void calculateForOperation_shouldExposeCountryContext_whenCommissionPoolMissing() {
        UUID operationId = UUID.randomUUID();
        UUID purchaserId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Operation operation = Operation.builder()
                .id(operationId)
                .accountHolderId(purchaserId)
                .planId(planId)
                .type(OperationType.SUBSCRIPTION)
                .amount(new BigDecimal("10000.00"))
                .currency("XOF")
                .build();

        when(subscriptionPlanRepository.findById(planId))
                .thenReturn(Optional.of(SubscriptionPlan.builder()
                        .id(planId)
                        .country("BJ")
                        .currency("XOF")
                        .grade(SubscriptionGrade.CLASSIC)
                        .durationMonths(1)
                        .price(new BigDecimal("10000.00"))
                        .active(true)
                        .code("CLASSIC_1M")
                        .name("Classic 1 month")
                        .build()));

        CountryConfigResolver.ResolvedConfig generalConfig = new CountryConfigResolver.ResolvedConfig(
                "BJ", CountryConfigType.GENERAL, null, "{}",
                CountryConfigResolver.ConfigSource.EMPTY
        );
        when(countryConfigResolver.resolveActiveConfig("BJ", CountryConfigType.GENERAL)).thenReturn(generalConfig);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> mlmCommissionService.calculateForOperation(operation));

        assertTrue(exception.getMessage().contains("commissionPool allocation percentage is required in GENERAL config for country BJ"));
        assertTrue(exception.getMessage().contains("source=EMPTY"));
    }

    private Membership activeMembership(UUID accountHolderId, SubscriptionGrade grade) {
        return Membership.builder()
                .accountHolderId(accountHolderId)
                .countryCode("BJ")
                .planGrade(grade)
                .status(MembershipStatus.ACTIVE)
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .build();
    }

    private ObjectNode generalPayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putObject("percentages").put("commissionPool", 60);
        return payload;
    }

    private GlobalMlmConfigService.GlobalMlmConfig globalMlmConfig() {
        return new GlobalMlmConfigService.GlobalMlmConfig(
                java.util.Map.of(
                        1, new BigDecimal("30"),
                        2, new BigDecimal("10"),
                        3, new BigDecimal("39"),
                        4, new BigDecimal("5"),
                        5, new BigDecimal("5"),
                        6, new BigDecimal("4"),
                        7, new BigDecimal("3"),
                        8, new BigDecimal("2.5"),
                        9, new BigDecimal("1"),
                        10, new BigDecimal("0.5")
                ),
                new GlobalMlmConfigService.GlobalBinaryConfig("WEAKER_LEG")
        );
    }

    private ObjectNode mlmLimitsPayload(String... tiers) {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode weeklyCaps = payload.putArray("weeklyCapsByTier");
        for (String tier : tiers) {
            ObjectNode item = weeklyCaps.addObject();
            item.put("tier", tier);
            item.put("cap", "GOLD".equals(tier) ? 5000 : 1000);
            if ("CLASSIC".equals(tier) && tiers.length > 1) {
                item.put("cap", 5000);
            }
        }
        return payload;
    }
}
