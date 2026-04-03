package com.ccapp.commerce.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.commerce.domain.entity.CommerceDsbPayout;
import com.ccapp.commerce.domain.entity.CommerceOrder;
import com.ccapp.commerce.domain.entity.CommercePromoCode;
import com.ccapp.commerce.domain.enums.CommerceDistributionMode;
import com.ccapp.commerce.domain.enums.CommerceDsbBeneficiaryRole;
import com.ccapp.commerce.domain.enums.CommerceDsbPayoutStatus;
import com.ccapp.commerce.event.DsbDistributedEvent;
import com.ccapp.commerce.event.DsbReversedEvent;
import com.ccapp.commerce.repository.CommerceDsbPayoutRepository;
import com.ccapp.commerce.repository.CommercePromoCodeRepository;
import com.ccapp.country.config.domain.enums.CountryAccountSystemTag;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.mlm.domain.entity.MlmMemberLink;
import com.ccapp.mlm.repository.MlmMemberLinkRepository;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.wallet.event.WalletCreditedEvent;
import com.ccapp.wallet.service.WalletDebitFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DSBService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final CommercePromoCodeRepository commercePromoCodeRepository;
    private final CommerceDsbPayoutRepository commerceDsbPayoutRepository;
    private final MlmMemberLinkRepository mlmMemberLinkRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final WalletRepository walletRepository;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final OperationService operationService;
    private final AccountingPostingService accountingPostingService;
    private final WalletDebitFacade walletDebitFacade;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public DistributionResult distribute(ShopOrder order,
                                         CommerceOrder commerceOrder,
                                         BigDecimal totalDsbAmount,
                                         String countryCode) {
        BigDecimal dsbAmount = scale(totalDsbAmount);
        if (dsbAmount.signum() <= 0) {
            return new DistributionResult(CommerceDistributionMode.NONE, List.of());
        }

        List<CommerceDsbPayout> payouts = new ArrayList<>();
        CommerceDistributionMode mode;

        if (commerceOrder.getPromoCodeId() != null) {
            mode = CommerceDistributionMode.PROMO_CODE;
            CommercePromoCode promoCode = commercePromoCodeRepository.findById(commerceOrder.getPromoCodeId())
                    .orElseThrow(() -> new BusinessException("PROMO_CODE_NOT_FOUND"));
            BigDecimal reward = dsbAmount.subtract(scale(commerceOrder.getDiscountAmount())).setScale(4, RoundingMode.HALF_UP);
            if (reward.signum() > 0) {
                payouts.add(createOrReusePayout(
                        order,
                        countryCode,
                        promoCode.getOwnerAccountHolderId(),
                        CommerceDsbBeneficiaryRole.PROMO_OWNER,
                        reward
                ));
            }
        } else {
            mode = CommerceDistributionMode.SPONSOR_CHAIN;
            Map<CommerceDsbBeneficiaryRole, BigDecimal> shares = splitSponsorShares(dsbAmount);
            UUID cursor = order.getBuyerId();
            for (CommerceDsbBeneficiaryRole role : List.of(
                    CommerceDsbBeneficiaryRole.SPONSOR_G1,
                    CommerceDsbBeneficiaryRole.SPONSOR_G2,
                    CommerceDsbBeneficiaryRole.SPONSOR_G3
            )) {
                cursor = resolveSponsor(cursor);
                if (cursor == null) {
                    continue;
                }
                BigDecimal amount = scale(shares.get(role));
                if (amount.signum() <= 0) {
                    continue;
                }
                payouts.add(createOrReusePayout(order, countryCode, cursor, role, amount));
            }
        }

        if (!payouts.isEmpty()) {
            domainEventPublisher.publish(DsbDistributedEvent.create(
                    order.getId(),
                    order.getBuyerId(),
                    mode,
                    dsbAmount,
                    order.getCurrency(),
                    payouts.stream()
                            .map(payout -> new DsbDistributedEvent.Beneficiary(
                                    payout.getBeneficiaryAccountHolderId(),
                                    payout.getRole().name(),
                                    scale(payout.getAmount())
                            ))
                            .toList()
            ));
        }
        return new DistributionResult(mode, payouts);
    }

    @Transactional
    public void reverse(CommerceOrder commerceOrder) {
        if (commerceOrder == null) {
            return;
        }
        List<CommerceDsbPayout> payouts = commerceDsbPayoutRepository.findByOrderIdOrderByCreatedAtAsc(commerceOrder.getOrderId());
        if (payouts.isEmpty()) {
            return;
        }

        String countryCode = normalizeCountry(commerceOrder.getCountryCode());
        String marketplacePoolCode = countryChartOfAccountsService
                .resolveSystemAccount(countryCode, CountryAccountSystemTag.MARKETPLACE_POOL)
                .getAccountCode();

        List<DsbReversedEvent.Beneficiary> reversed = new ArrayList<>();
        BigDecimal totalReversed = ZERO;
        for (CommerceDsbPayout payout : payouts) {
            if (payout.getStatus() == CommerceDsbPayoutStatus.REVERSED) {
                continue;
            }
            Wallet beneficiaryWallet = requireActiveWallet(payout.getBeneficiaryAccountHolderId());
            WalletDebitFacade.WalletDebitExecutionResult reversal = walletDebitFacade.executeSecureDebit(
                    new WalletDebitFacade.SecureDebitRequest(
                            payout.getBeneficiaryAccountHolderId(),
                            payout.getBeneficiaryAccountHolderId(),
                            payout.getBeneficiaryAccountHolderId(),
                            beneficiaryWallet,
                            OperationType.COMMERCE_DSB_REVERSAL,
                            scale(payout.getAmount()),
                            payout.getCurrency(),
                            "commerce-dsb-reversal:" + payout.getOrderId() + ":" + payout.getRole().name(),
                            "Commerce DSB reversal for order " + payout.getOrderId() + " role " + payout.getRole().name(),
                            buildReversalFingerprint(payout, marketplacePoolCode),
                            marketplacePoolCode,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            true,
                            false,
                            "Commerce DSB reversal debit",
                            "Marketplace pool recovery",
                            null,
                            null,
                            WalletDebitFacade.SecureDebitPostingMode.STANDARD,
                            null
                    )
            );
            payout.setReversalOperationId(reversal.operationId());
            payout.setStatus(CommerceDsbPayoutStatus.REVERSED);
            commerceDsbPayoutRepository.save(payout);
            totalReversed = totalReversed.add(scale(payout.getAmount()));
            reversed.add(new DsbReversedEvent.Beneficiary(
                    payout.getBeneficiaryAccountHolderId(),
                    payout.getRole().name(),
                    scale(payout.getAmount())
            ));
        }

        if (!reversed.isEmpty()) {
            domainEventPublisher.publish(DsbReversedEvent.create(
                    commerceOrder.getOrderId(),
                    totalReversed,
                    reversed
            ));
        }
    }

    private CommerceDsbPayout createOrReusePayout(ShopOrder order,
                                                  String countryCode,
                                                  UUID beneficiaryAccountHolderId,
                                                  CommerceDsbBeneficiaryRole role,
                                                  BigDecimal amount) {
        CommerceDsbPayout existing = commerceDsbPayoutRepository.findByOrderIdAndRole(order.getId(), role).orElse(null);
        if (existing != null && existing.getStatus() == CommerceDsbPayoutStatus.PAID) {
            return existing;
        }

        AccountHolder beneficiary = accountHolderRepository.findById(beneficiaryAccountHolderId)
                .orElseThrow(() -> new BusinessException("BENEFICIARY_WALLET_NOT_FOUND"));
        Wallet wallet = requireActiveWallet(beneficiaryAccountHolderId);
        Operation operation = operationService.createInitiatedOperation(
                beneficiaryAccountHolderId,
                order.getBuyerId(),
                OperationType.COMMERCE_DSB_PAYOUT,
                scale(amount),
                normalizeCurrency(order.getCurrency()),
                "commerce-dsb-payout:" + order.getId() + ":" + role.name(),
                "Commerce DSB payout for order " + order.getId() + " role " + role.name()
        );
        Operation confirmed = operation.getStatus() == OperationStatus.CONFIRMED
                ? operation
                : operationService.confirmOperation(operation.getId());
        accountingPostingService.postCommerceDsbPayout(confirmed, beneficiaryAccountHolderId, normalizeCountry(countryCode));
        domainEventPublisher.publish(WalletCreditedEvent.create(
                wallet.getId(),
                beneficiaryAccountHolderId,
                scale(amount),
                normalizeCurrency(order.getCurrency()),
                confirmed.getId()
        ));

        CommerceDsbPayout payout = existing == null
                ? CommerceDsbPayout.builder().id(UUID.randomUUID()).orderId(order.getId()).role(role).build()
                : existing;
        payout.setBeneficiaryAccountHolderId(beneficiary.getId());
        payout.setAmount(scale(amount));
        payout.setCurrency(normalizeCurrency(order.getCurrency()));
        payout.setOperationId(confirmed.getId());
        payout.setStatus(CommerceDsbPayoutStatus.PAID);
        return commerceDsbPayoutRepository.save(payout);
    }

    private UUID resolveSponsor(UUID accountHolderId) {
        if (accountHolderId == null) {
            return null;
        }
        MlmMemberLink link = mlmMemberLinkRepository.findById(accountHolderId).orElse(null);
        return link == null ? null : link.getSponsorAccountHolderId();
    }

    private Map<CommerceDsbBeneficiaryRole, BigDecimal> splitSponsorShares(BigDecimal totalDsbAmount) {
        BigDecimal g1 = totalDsbAmount.multiply(new BigDecimal("50"))
                .divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
        BigDecimal g2 = totalDsbAmount.multiply(new BigDecimal("30"))
                .divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
        BigDecimal g3 = totalDsbAmount.subtract(g1).subtract(g2).setScale(4, RoundingMode.HALF_UP);
        return Map.of(
                CommerceDsbBeneficiaryRole.SPONSOR_G1, g1,
                CommerceDsbBeneficiaryRole.SPONSOR_G2, g2,
                CommerceDsbBeneficiaryRole.SPONSOR_G3, g3
        );
    }

    private Wallet requireActiveWallet(UUID accountHolderId) {
        return walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(accountHolderId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("BENEFICIARY_WALLET_NOT_FOUND"));
    }

    private String buildReversalFingerprint(CommerceDsbPayout payout, String marketplacePoolCode) {
        return "commerce-dsb-reversal|"
                + payout.getOrderId()
                + "|" + payout.getRole().name()
                + "|" + scale(payout.getAmount()).stripTrailingZeros().toPlainString()
                + "|" + normalizeCurrency(payout.getCurrency())
                + "|" + marketplacePoolCode;
    }

    private String normalizeCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new BusinessException("PROMO_CODE_COUNTRY_MISMATCH");
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BusinessException("Currency is required");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal scale(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }
        return amount.setScale(4, RoundingMode.HALF_UP);
    }

    public record DistributionResult(
            CommerceDistributionMode distributionMode,
            List<CommerceDsbPayout> payouts
    ) {
    }
}
