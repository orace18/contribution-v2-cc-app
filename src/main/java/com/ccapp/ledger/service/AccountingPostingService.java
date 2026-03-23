package com.ccapp.ledger.service;

import com.ccapp.ledger.domain.entity.AccountBalanceSnapshot;
import com.ccapp.ledger.domain.entity.AccountingEvent;
import com.ccapp.ledger.domain.entity.LedgerEntry;
import com.ccapp.ledger.domain.enums.AccountingEventType;
import com.ccapp.ledger.domain.enums.LedgerEntryType;
import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import com.ccapp.ledger.repository.AccountBalanceSnapshotRepository;
import com.ccapp.ledger.repository.AccountingEventRepository;
import com.ccapp.ledger.repository.LedgerEntryRepository;
import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.domain.enums.CountryAccountSystemTag;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingPostingService {

    private final OperationService operationService;
    private final AccountingEventRepository accountingEventRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountBalanceSnapshotRepository snapshotRepository;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final CountryConfigResolver countryConfigResolver;
    private final AccountHolderRepository accountHolderRepository;
    private final CountryRepository countryRepository;

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    @Transactional
    public AccountingEvent postConfirmedOperation(UUID operationId) {
        return accountingEventRepository.findByOperationId(operationId)
                .orElseGet(() -> doPost(operationService.getById(operationId)));
    }

    @Transactional
    public AccountingEvent postAdjustment(Operation operation) {
        return accountingEventRepository.findByOperationId(operation.getId())
                .orElseGet(() -> doPost(operation));
    }

    @Transactional
    public AccountingEvent postCommissionPayout(Operation operation,
                                                UUID beneficiaryAccountHolderId,
                                                BigDecimal taxAmount,
                                                BigDecimal netAmount) {
        if (operation.getType() != OperationType.COMMISSION_PAYOUT) {
            throw new BusinessException("Operation type must be COMMISSION_PAYOUT");
        }
        if (taxAmount == null || taxAmount.signum() < 0 || netAmount == null || netAmount.signum() < 0) {
            throw new BusinessException("Invalid payout tax/net amounts");
        }
        if (operation.getAmount().compareTo(taxAmount.add(netAmount)) != 0) {
            throw new BusinessException("Payout amount mismatch against tax/net split");
        }
        if (operation.getStatus() != OperationStatus.CONFIRMED) {
            throw new BusinessException("Only CONFIRMED operations can be posted");
        }

        String countryCode = resolveOperationalCountryCode(operation.getAccountHolderId(), operation.getCurrency());
        AccountHolder beneficiary = resolveAccountHolder(beneficiaryAccountHolderId);
        List<PostingLine> lines = List.of(
                new PostingLine(resolveAccountCode(countryCode, CountryAccountSystemTag.MLM_POOL), LedgerEntryType.DEBIT,
                        operation.getAmount(), operation.getCurrency(),
                        "Commission payout gross expense"),
                new PostingLine(resolveWalletAccountCode(beneficiary), LedgerEntryType.CREDIT,
                        netAmount, operation.getCurrency(),
                        "Member wallet credit"),
                new PostingLine(resolveAccountCode(countryCode, CountryAccountSystemTag.PLATFORM_REVENUE), LedgerEntryType.CREDIT,
                        taxAmount, operation.getCurrency(),
                        "Tax withholding on commission payout")
        );
        return accountingEventRepository.findByOperationId(operation.getId())
                .orElseGet(() -> doPost(operation, lines, AccountingEventType.OPERATION_CONFIRMED));
    }

    private AccountingEvent doPost(Operation operation) {
        if (operation.getStatus() != OperationStatus.CONFIRMED) {
            throw new BusinessException("Only CONFIRMED operations can be posted");
        }
        AccountingEventType eventType = operation.getType() == OperationType.ADJUSTMENT
                ? AccountingEventType.ADJUSTMENT_POSTED
                : AccountingEventType.OPERATION_CONFIRMED;
        String countryCode = resolveOperationalCountryCode(operation.getAccountHolderId(), operation.getCurrency());
        AccountHolder accountHolder = resolveAccountHolder(operation.getAccountHolderId());
        List<PostingLine> lines = buildPostingLines(operation, countryCode, accountHolder);
        return doPost(operation, lines, eventType);
    }

    private AccountingEvent doPost(Operation operation, List<PostingLine> lines, AccountingEventType eventType) {
        if (operation.getStatus() != OperationStatus.CONFIRMED) {
            throw new BusinessException("Only CONFIRMED operations can be posted");
        }

        validateCurrency(lines, operation.getCurrency());
        validateBalanced(lines);

        BigDecimal totalDebit = lines.stream()
                .filter(line -> line.entryType() == LedgerEntryType.DEBIT)
                .map(PostingLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = lines.stream()
                .filter(line -> line.entryType() == LedgerEntryType.CREDIT)
                .map(PostingLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AccountingEvent event = AccountingEvent.builder()
                .operation(operation)
                .eventType(eventType)
                .currency(operation.getCurrency())
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .postedAt(OffsetDateTime.now())
                .build();

        AccountingEvent savedEvent;
        try {
            savedEvent = accountingEventRepository.save(event);
        } catch (DataIntegrityViolationException ex) {
            return accountingEventRepository.findByOperationId(operation.getId())
                    .orElseThrow(() -> ex);
        }

        List<LedgerEntry> entries = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            PostingLine line = lines.get(i);
            entries.add(LedgerEntry.builder()
                    .event(savedEvent)
                    .lineNo(i + 1)
                    .accountCode(line.accountCode())
                    .entryType(line.entryType())
                    .amount(line.amount())
                    .currency(line.currency())
                    .description(line.description())
                    .build());
        }

        ledgerEntryRepository.saveAll(entries);
        updateSnapshots(entries);
        log.info("ledger_posting_completed operationId={} operationType={} eventType={} entries={} totalDebit={} totalCredit={}",
                operation.getId(), operation.getType(), eventType, entries.size(), totalDebit, totalCredit);
        return savedEvent;
    }

    private List<PostingLine> buildPostingLines(Operation operation, String countryCode, AccountHolder accountHolder) {
        String walletCode = resolveWalletAccountCode(accountHolder);
        return switch (operation.getType()) {
            case SUBSCRIPTION -> buildSubscriptionPostingLines(operation, countryCode, walletCode);
            case ADJUSTMENT -> List.of(
                    new PostingLine(resolveAccountCode(countryCode, CountryAccountSystemTag.PLATFORM_REVENUE), LedgerEntryType.DEBIT,
                            operation.getAmount(), operation.getCurrency(), "Reconciliation adjustment"),
                    new PostingLine(walletCode, LedgerEntryType.CREDIT,
                            operation.getAmount(), operation.getCurrency(), "Adjustment against cash")
            );
            case LOAN_DISBURSEMENT -> List.of(
                    new PostingLine(resolveAccountCode(countryCode, CountryAccountSystemTag.LOAN_PORTFOLIO), LedgerEntryType.DEBIT,
                            operation.getAmount(), operation.getCurrency(), "Loan principal recognized"),
                    new PostingLine(walletCode, LedgerEntryType.CREDIT,
                            operation.getAmount(), operation.getCurrency(), "Loan disbursement cash-out")
            );
            case LOAN_REPAYMENT -> List.of(
                    new PostingLine(walletCode, LedgerEntryType.DEBIT,
                            operation.getAmount(), operation.getCurrency(), "Loan repayment cash-in"),
                    new PostingLine(resolveAccountCode(countryCode, CountryAccountSystemTag.LOAN_PORTFOLIO), LedgerEntryType.CREDIT,
                            operation.getAmount(), operation.getCurrency(), "Loan principal repayment")
            );
            case COMMISSION_PAYOUT -> throw new BusinessException(
                    "Use postCommissionPayout for COMMISSION_PAYOUT operations"
            );
        };
    }

    private List<PostingLine> buildSubscriptionPostingLines(Operation operation,
                                                            String countryCode,
                                                            String walletCode) {
        SubscriptionPostingProfile profile = resolveSubscriptionPostingProfile(countryCode);
        String paymentSourceAccountCode = operation.getPspReference() == null || operation.getPspReference().isBlank()
                ? walletCode
                : resolveAccountCode(countryCode, CountryAccountSystemTag.PSP_CLEARING);

        BigDecimal commissionPoolAmount = allocationAmount(operation.getAmount(), profile.commissionPoolPercent());
        BigDecimal guaranteeFundAmount = allocationAmount(operation.getAmount(), profile.guaranteeFundPercent());
        BigDecimal platformFeeAmount = allocationAmount(operation.getAmount(), profile.platformFeePercent());
        BigDecimal administrationAmount = allocationAmount(operation.getAmount(), profile.administrationPercent());
        BigDecimal socialFundAmount = operation.getAmount()
                .subtract(commissionPoolAmount)
                .subtract(guaranteeFundAmount)
                .subtract(platformFeeAmount)
                .subtract(administrationAmount)
                .setScale(4, RoundingMode.HALF_UP);

        return List.of(
                new PostingLine(paymentSourceAccountCode, LedgerEntryType.DEBIT,
                        operation.getAmount(), operation.getCurrency(), "Subscription payment recognition"),
                new PostingLine(profile.membershipRevenueAccountCode(), LedgerEntryType.CREDIT,
                        operation.getAmount(), operation.getCurrency(), "Cotisations des membres"),
                new PostingLine(profile.membershipRevenueAccountCode(), LedgerEntryType.DEBIT,
                        operation.getAmount(), operation.getCurrency(), "Subscription pre-allocation"),
                new PostingLine(profile.commissionPoolAccountCode(), LedgerEntryType.CREDIT,
                        commissionPoolAmount, operation.getCurrency(), "Commission pool allocation"),
                new PostingLine(profile.guaranteeFundAccountCode(), LedgerEntryType.CREDIT,
                        guaranteeFundAmount, operation.getCurrency(), "Guarantee fund allocation"),
                new PostingLine(profile.platformFeeAccountCode(), LedgerEntryType.CREDIT,
                        platformFeeAmount, operation.getCurrency(), "Platform fee allocation"),
                new PostingLine(profile.administrationAccountCode(), LedgerEntryType.CREDIT,
                        administrationAmount, operation.getCurrency(), "Administration allocation"),
                new PostingLine(profile.socialFundAccountCode(), LedgerEntryType.CREDIT,
                        socialFundAmount, operation.getCurrency(), "Social fund allocation")
        );
    }

    private SubscriptionPostingProfile resolveSubscriptionPostingProfile(String countryCode) {
        var generalConfig = countryConfigResolver.resolveActiveConfig(countryCode, CountryConfigType.GENERAL);
        var accountingConfig = countryConfigResolver.resolveActiveConfig(countryCode, CountryConfigType.ACCOUNTING);
        var generalPayload = countryConfigResolver.payloadAsObjectNode(generalConfig);
        var accountingPayload = countryConfigResolver.payloadAsObjectNode(accountingConfig);
        var accountsNode = accountingPayload.has("accounts") && accountingPayload.get("accounts").isObject()
                ? (com.fasterxml.jackson.databind.node.ObjectNode) accountingPayload.get("accounts")
                : accountingPayload.has("preallocationAccounts") && accountingPayload.get("preallocationAccounts").isObject()
                ? (com.fasterxml.jackson.databind.node.ObjectNode) accountingPayload.get("preallocationAccounts")
                : accountingPayload;

        BigDecimal commissionPoolPercent = requiredPercentage(generalPayload, "commissionPool", "commission_pool");
        BigDecimal guaranteeFundPercent = requiredPercentage(generalPayload, "guaranteeFund", "guarantee_fund");
        BigDecimal platformFeePercent = requiredPercentage(generalPayload, "platformFee", "platform_fee");
        BigDecimal administrationPercent = requiredPercentage(generalPayload, "administration", "administrationFee");
        BigDecimal socialFundPercent = requiredPercentage(generalPayload, "socialFund", "social_fund");
        BigDecimal totalPercent = commissionPoolPercent
                .add(guaranteeFundPercent)
                .add(platformFeePercent)
                .add(administrationPercent)
                .add(socialFundPercent)
                .setScale(4, RoundingMode.HALF_UP);
        if (totalPercent.compareTo(ONE_HUNDRED.setScale(4, RoundingMode.HALF_UP)) != 0) {
            throw new BusinessException("Subscription allocation percentages must sum to 100");
        }

        return new SubscriptionPostingProfile(
                resolveExplicitOrDefaultAccountCode(countryCode, accountsNode, "membershipRevenue", "cotisations",
                        () -> countryChartOfAccountsService.findOrCreate(
                                countryCode,
                                CountryAccountGroup.MEMBERSHIP_FEES,
                                "750001 Cotisations des membres"
                        ).getAccountCode()),
                resolveExplicitOrDefaultAccountCode(countryCode, accountsNode, "commissionPool", "mlmPool",
                        () -> resolveAccountCode(countryCode, CountryAccountSystemTag.MLM_POOL)),
                resolveExplicitOrDefaultAccountCode(countryCode, accountsNode, "guaranteeFund", "guarantee",
                        () -> countryChartOfAccountsService.findOrCreate(
                                countryCode,
                                CountryAccountGroup.GUARANTEE_FUND,
                                "Guarantee Fund"
                        ).getAccountCode()),
                resolveExplicitOrDefaultAccountCode(countryCode, accountsNode, "platformFee", "platformRevenue",
                        () -> resolveAccountCode(countryCode, CountryAccountSystemTag.PLATFORM_REVENUE)),
                resolveExplicitOrDefaultAccountCode(countryCode, accountsNode, "administration", "administrationFee",
                        () -> countryChartOfAccountsService.findOrCreate(
                                countryCode,
                                CountryAccountGroup.ADMINISTRATION,
                                "Administration"
                        ).getAccountCode()),
                resolveExplicitOrDefaultAccountCode(countryCode, accountsNode, "socialFund", "social",
                        () -> countryChartOfAccountsService.findOrCreate(
                                countryCode,
                                CountryAccountGroup.SOCIAL_FUND,
                                "Social Fund"
                        ).getAccountCode()),
                commissionPoolPercent,
                guaranteeFundPercent,
                platformFeePercent,
                administrationPercent,
                socialFundPercent
        );
    }

    private String resolveExplicitOrDefaultAccountCode(String countryCode,
                                                       com.fasterxml.jackson.databind.node.ObjectNode accountsNode,
                                                       String primaryAlias,
                                                       String secondaryAlias,
                                                       java.util.function.Supplier<String> fallbackSupplier) {
        if (accountsNode != null) {
            for (String alias : List.of(primaryAlias, secondaryAlias)) {
                var node = accountsNode.get(alias);
                if (node != null && node.isTextual() && !node.asText().isBlank()) {
                    String explicitCode = node.asText().trim().toUpperCase(Locale.ROOT);
                    if (!countryChartOfAccountsService.hasRequiredSystemMappings(countryCode)
                            && explicitCode.isBlank()) {
                        break;
                    }
                    return explicitCode;
                }
            }
        }
        return fallbackSupplier.get();
    }

    private BigDecimal requiredPercentage(com.fasterxml.jackson.databind.node.ObjectNode payload, String... aliases) {
        if (payload == null) {
            throw new BusinessException("Subscription allocation configuration is required");
        }
        com.fasterxml.jackson.databind.JsonNode source = payload.has("percentages") && payload.get("percentages").isObject()
                ? payload.get("percentages")
                : payload;
        for (String alias : aliases) {
            var node = source.get(alias);
            if (node != null && node.isNumber()) {
                BigDecimal value = node.decimalValue();
                if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(ONE_HUNDRED) > 0) {
                    throw new BusinessException("Subscription allocation percentage must be between 0 and 100 for " + alias);
                }
                return value;
            }
        }
        throw new BusinessException("Missing subscription allocation percentage for " + aliases[0]);
    }

    private BigDecimal allocationAmount(BigDecimal baseAmount, BigDecimal percent) {
        return baseAmount.multiply(percent)
                .divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
    }

    private String resolveAccountCode(String countryCode, CountryAccountSystemTag systemTag) {
        CountryChartAccount account = countryChartOfAccountsService.resolveSystemAccount(countryCode, systemTag);
        return account.getAccountCode();
    }

    /**
     * Resolves the wallet account code for a specific account holder.
     * Auto-creates the wallet if it does not exist (safety guarantee).
     */
    private String resolveWalletAccountCode(AccountHolder accountHolder) {
        CountryChartAccount wallet = countryChartOfAccountsService.findOrCreateWallet(accountHolder);
        return wallet.getAccountCode();
    }

    private AccountHolder resolveAccountHolder(UUID accountHolderId) {
        return accountHolderRepository.findById(accountHolderId)
                .orElseThrow(() -> new BusinessException("Account holder not found: " + accountHolderId));
    }

    private String resolveOperationalCountryCode(UUID accountHolderId, String currency) {
        String normalizedCurrency = currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
        String holderCountryCode = accountHolderRepository.findById(accountHolderId)
                .map(this::readAccountHolderCountryCode)
                .orElse(null);
        if (holderCountryCode != null && !holderCountryCode.isBlank()) {
            return ensureCountryExists(holderCountryCode, normalizedCurrency);
        }
        return countryRepository.findByFallbackTrue()
                .map(Country::getCode)
                .or(() -> countryRepository.findByActiveTrueOrderByNameAsc().stream()
                        .filter(country -> normalizedCurrency != null
                                && normalizedCurrency.equalsIgnoreCase(country.getCurrency()))
                        .map(Country::getCode)
                        .findFirst())
                .or(() -> countryRepository.findByActiveTrueOrderByNameAsc().stream()
                        .map(Country::getCode)
                        .findFirst())
                .map(code -> ensureCountryExists(code, normalizedCurrency))
                .orElseGet(() -> ensureCountryExists("INT", normalizedCurrency == null ? "USD" : normalizedCurrency));
    }

    private String readAccountHolderCountryCode(AccountHolder accountHolder) {
        if (accountHolder == null) {
            return null;
        }
        if (accountHolder.getServiceCountryCode() != null && !accountHolder.getServiceCountryCode().isBlank()) {
            return accountHolder.getServiceCountryCode().trim().toUpperCase(Locale.ROOT);
        }
        if (accountHolder.getCountryCode() != null && !accountHolder.getCountryCode().isBlank()) {
            return accountHolder.getCountryCode().trim().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private String ensureCountryExists(String countryCode, String currency) {
        String normalizedCode = countryCode.trim().toUpperCase(Locale.ROOT);
        if (countryRepository.findByCode(normalizedCode).isPresent()) {
            return normalizedCode;
        }
        countryRepository.save(Country.builder()
                .id(UUID.randomUUID())
                .code(normalizedCode)
                .name("Country " + normalizedCode)
                .currency(currency == null || currency.isBlank() ? "USD" : currency)
                .active(true)
                .configured(false)
                .fallback("INT".equals(normalizedCode))
                .build());
        return normalizedCode;
    }

    private void validateCurrency(List<PostingLine> lines, String operationCurrency) {
        boolean invalid = lines.stream().anyMatch(line -> !operationCurrency.equals(line.currency()));
        if (invalid) {
            throw new BusinessException("Strict currency verification failed");
        }
    }

    private void validateBalanced(List<PostingLine> lines) {
        BigDecimal debit = lines.stream()
                .filter(line -> line.entryType() == LedgerEntryType.DEBIT)
                .map(PostingLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = lines.stream()
                .filter(line -> line.entryType() == LedgerEntryType.CREDIT)
                .map(PostingLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (debit.compareTo(credit) != 0) {
            throw new BusinessException("Ledger entries are not balanced");
        }
    }

    private void updateSnapshots(List<LedgerEntry> entries) {
        for (LedgerEntry entry : entries) {
            AccountBalanceSnapshot snapshot = snapshotRepository
                    .findByAccountCodeAndCurrency(entry.getAccountCode(), entry.getCurrency())
                    .orElseGet(() -> snapshotRepository.save(AccountBalanceSnapshot.builder()
                            .accountCode(entry.getAccountCode())
                            .currency(entry.getCurrency())
                            .balance(BigDecimal.ZERO)
                            .version(0L)
                            .build()));

            BigDecimal delta = entry.getEntryType() == LedgerEntryType.DEBIT
                    ? entry.getAmount()
                    : entry.getAmount().negate();
            snapshot.setBalance(snapshot.getBalance().add(delta));
            snapshotRepository.save(snapshot);
        }
    }

    private record PostingLine(
            String accountCode,
            LedgerEntryType entryType,
            BigDecimal amount,
            String currency,
            String description
    ) {
    }

    private record SubscriptionPostingProfile(
            String membershipRevenueAccountCode,
            String commissionPoolAccountCode,
            String guaranteeFundAccountCode,
            String platformFeeAccountCode,
            String administrationAccountCode,
            String socialFundAccountCode,
            BigDecimal commissionPoolPercent,
            BigDecimal guaranteeFundPercent,
            BigDecimal platformFeePercent,
            BigDecimal administrationPercent,
            BigDecimal socialFundPercent
    ) {
    }
}
