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
    private static final BigDecimal MARKETPLACE_GUARANTEE_PERCENT = new BigDecimal("15");
    private static final BigDecimal MARKETPLACE_PLATFORM_PERCENT = new BigDecimal("10");
    private static final BigDecimal MARKETPLACE_ADMINISTRATION_PERCENT = new BigDecimal("10");
    private static final BigDecimal MARKETPLACE_SOCIAL_PERCENT = new BigDecimal("5");
    private static final BigDecimal MARKETPLACE_MLM_PERCENT = new BigDecimal("60");

    @Transactional
    public AccountingEvent postConfirmedOperation(UUID operationId) {
        return accountingEventRepository.findByOperationId(operationId)
                .orElseGet(() -> doPost(operationService.getById(operationId)));
    }

    @Transactional
    public AccountingEvent postAdjustment(Operation operation) {
        throw new BusinessException("Generic ADJUSTMENT posting is forbidden; use an explicit adjustment posting flow");
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

    @Transactional
    public AccountingEvent postMarketplacePayment(Operation operation,
                                                  UUID buyerAccountHolderId,
                                                  String countryCode,
                                                  boolean walletPayment) {
        if (operation.getType() != OperationType.MARKETPLACE_PAYMENT) {
            throw new BusinessException("Operation type must be MARKETPLACE_PAYMENT");
        }
        if (operation.getStatus() != OperationStatus.CONFIRMED) {
            throw new BusinessException("Only CONFIRMED marketplace payment operations can be posted");
        }
        if (walletPayment) {
            throw new BusinessException("Wallet marketplace debits must go through WalletDebitFacade");
        }
        resolveAccountHolder(buyerAccountHolderId);
        List<PostingLine> lines = List.of(
                new PostingLine(
                        resolveAccountCode(countryCode, CountryAccountSystemTag.PSP_CLEARING),
                        LedgerEntryType.DEBIT,
                        operation.getAmount(),
                        operation.getCurrency(),
                        "Marketplace payment via PSP shadow"
                ),
                new PostingLine(
                        resolveAccountCode(countryCode, CountryAccountSystemTag.MARKETPLACE_POOL),
                        LedgerEntryType.CREDIT,
                        operation.getAmount(),
                        operation.getCurrency(),
                        "Marketplace pool funding"
                )
        );
        return accountingEventRepository.findByOperationId(operation.getId())
                .orElseGet(() -> doPost(operation, lines, AccountingEventType.OPERATION_CONFIRMED));
    }

    @Transactional
    public AccountingEvent postMarketplaceWalletPayment(Operation operation,
                                                        UUID buyerAccountHolderId,
                                                        String countryCode) {
        if (operation.getType() != OperationType.MARKETPLACE_PAYMENT) {
            throw new BusinessException("Operation type must be MARKETPLACE_PAYMENT");
        }
        if (operation.getStatus() != OperationStatus.CONFIRMED) {
            throw new BusinessException("Only CONFIRMED marketplace payment operations can be posted");
        }
        AccountHolder buyer = resolveAccountHolder(buyerAccountHolderId);
        List<PostingLine> lines = List.of(
                new PostingLine(
                        resolveWalletAccountCode(buyer),
                        LedgerEntryType.DEBIT,
                        operation.getAmount(),
                        operation.getCurrency(),
                        "Marketplace wallet payment"
                ),
                new PostingLine(
                        resolveAccountCode(countryCode, CountryAccountSystemTag.MARKETPLACE_POOL),
                        LedgerEntryType.CREDIT,
                        operation.getAmount(),
                        operation.getCurrency(),
                        "Marketplace pool funding"
                )
        );
        return accountingEventRepository.findByOperationId(operation.getId())
                .orElseGet(() -> doPost(operation, lines, AccountingEventType.OPERATION_CONFIRMED));
    }

    @Transactional
    public AccountingEvent postMarketplaceMerchantPayout(Operation operation,
                                                         UUID merchantAccountHolderId,
                                                         String countryCode) {
        if (operation.getType() != OperationType.MARKETPLACE_MERCHANT_PAYOUT) {
            throw new BusinessException("Operation type must be MARKETPLACE_MERCHANT_PAYOUT");
        }
        if (operation.getStatus() != OperationStatus.CONFIRMED) {
            throw new BusinessException("Only CONFIRMED merchant payout operations can be posted");
        }
        AccountHolder merchant = resolveAccountHolder(merchantAccountHolderId);
        List<PostingLine> lines = List.of(
                new PostingLine(resolveAccountCode(countryCode, CountryAccountSystemTag.MARKETPLACE_POOL),
                        LedgerEntryType.DEBIT,
                        operation.getAmount(),
                        operation.getCurrency(),
                        "Marketplace merchant net payout"),
                new PostingLine(resolveWalletAccountCode(merchant),
                        LedgerEntryType.CREDIT,
                        operation.getAmount(),
                        operation.getCurrency(),
                        "Merchant wallet credit")
        );
        return accountingEventRepository.findByOperationId(operation.getId())
                .orElseGet(() -> doPost(operation, lines, AccountingEventType.OPERATION_CONFIRMED));
    }

    @Transactional
    public AccountingEvent postCommerceDsbPayout(Operation operation,
                                                 UUID beneficiaryAccountHolderId,
                                                 String countryCode) {
        if (operation.getType() != OperationType.COMMERCE_DSB_PAYOUT) {
            throw new BusinessException("Operation type must be COMMERCE_DSB_PAYOUT");
        }
        if (operation.getStatus() != OperationStatus.CONFIRMED) {
            throw new BusinessException("Only CONFIRMED DSB payout operations can be posted");
        }
        AccountHolder beneficiary = resolveAccountHolder(beneficiaryAccountHolderId);
        List<PostingLine> lines = List.of(
                new PostingLine(resolveAccountCode(countryCode, CountryAccountSystemTag.MARKETPLACE_POOL),
                        LedgerEntryType.DEBIT,
                        operation.getAmount(),
                        operation.getCurrency(),
                        "Commerce DSB payout"),
                new PostingLine(resolveWalletAccountCode(beneficiary),
                        LedgerEntryType.CREDIT,
                        operation.getAmount(),
                        operation.getCurrency(),
                        "Commerce DSB wallet credit")
        );
        return accountingEventRepository.findByOperationId(operation.getId())
                .orElseGet(() -> doPost(operation, lines, AccountingEventType.OPERATION_CONFIRMED));
    }

    @Transactional
    public AccountingEvent postMarketplaceMerchantPayoutReversal(Operation operation,
                                                                 UUID merchantAccountHolderId,
                                                                 String countryCode) {
        throw new BusinessException("Merchant payout reversal wallet debits must go through WalletDebitFacade");
    }

    @Transactional
    public AccountingEvent postMarketplaceCommissionAllocation(Operation operation,
                                                               String countryCode) {
        if (operation.getType() != OperationType.MARKETPLACE_COMMISSION_ALLOCATION) {
            throw new BusinessException("Operation type must be MARKETPLACE_COMMISSION_ALLOCATION");
        }
        if (operation.getStatus() != OperationStatus.CONFIRMED) {
            throw new BusinessException("Only CONFIRMED marketplace commission allocation operations can be posted");
        }
        MarketplaceAllocationProfile profile = resolveMarketplaceAllocationProfile(countryCode);
        List<PostingLine> lines = buildMarketplaceAllocationLines(
                operation,
                profile,
                true
        );
        return accountingEventRepository.findByOperationId(operation.getId())
                .orElseGet(() -> doPost(operation, lines, AccountingEventType.OPERATION_CONFIRMED));
    }

    @Transactional
    public AccountingEvent postMarketplaceCommissionAllocationReversal(Operation operation,
                                                                       String countryCode) {
        if (operation.getType() != OperationType.MARKETPLACE_COMMISSION_ALLOCATION_REVERSAL) {
            throw new BusinessException("Operation type must be MARKETPLACE_COMMISSION_ALLOCATION_REVERSAL");
        }
        if (operation.getStatus() != OperationStatus.CONFIRMED) {
            throw new BusinessException("Only CONFIRMED marketplace commission allocation reversal operations can be posted");
        }
        MarketplaceAllocationProfile profile = resolveMarketplaceAllocationProfile(countryCode);
        List<PostingLine> lines = buildMarketplaceAllocationLines(
                operation,
                profile,
                false
        );
        return accountingEventRepository.findByOperationId(operation.getId())
                .orElseGet(() -> doPost(operation, lines, AccountingEventType.OPERATION_CONFIRMED));
    }

    // =========================================================================
    // Wallet operation posting methods (pre-built lines – no switch dispatch)
    // =========================================================================

    /**
     * WALLET_PAYMENT: DEBIT senderWallet → CREDIT destinationAccount.
     * Used for P2P payments and auto-debit rules.
     */
    @Transactional
    public AccountingEvent postWalletPayment(Operation operation,
                                              String senderWalletCode,
                                              String destinationAccountCode) {
        validateWalletOperation(operation, OperationType.WALLET_PAYMENT);
        return postSecureWalletDebit(
                operation,
                senderWalletCode,
                destinationAccountCode,
                "Wallet payment - sender debit",
                "Wallet payment - destination credit"
        );
    }

    /**
     * WALLET_DEPOSIT_PSP: DEBIT PSP_CLEARING → CREDIT userWallet.
     */
    @Transactional
    public AccountingEvent postWalletDepositPsp(Operation operation, String userWalletCode) {
        validateWalletOperation(operation, OperationType.WALLET_DEPOSIT_PSP);
        String countryCode = resolveOperationalCountryCode(operation.getAccountHolderId(), operation.getCurrency());
        String pspCode = resolveAccountCode(countryCode, CountryAccountSystemTag.PSP_CLEARING);
        List<PostingLine> lines = List.of(
                new PostingLine(pspCode, LedgerEntryType.DEBIT,
                        operation.getAmount(), operation.getCurrency(), "PSP deposit clearing debit"),
                new PostingLine(userWalletCode, LedgerEntryType.CREDIT,
                        operation.getAmount(), operation.getCurrency(), "Wallet top-up from PSP")
        );
        return accountingEventRepository.findByOperationId(operation.getId())
                .orElseGet(() -> doPost(operation, lines, AccountingEventType.OPERATION_CONFIRMED));
    }

    /**
     * WALLET_DEPOSIT_AGENT: DEBIT agentWallet → CREDIT userWallet.
     * Full traceability for cash-in via agent float.
     */
    @Transactional
    public AccountingEvent postWalletDepositAgent(Operation operation,
                                                   String agentWalletCode,
                                                   String userWalletCode) {
        validateWalletOperation(operation, OperationType.WALLET_DEPOSIT_AGENT);
        return postSecureWalletDebit(
                operation,
                agentWalletCode,
                userWalletCode,
                "Agent float debit - cash-in",
                "User wallet credit - agent cash-in"
        );
    }

    /**
     * WALLET_WITHDRAWAL: DEBIT userWallet → CREDIT PSP_CLEARING.
     */
    @Transactional
    public AccountingEvent postWalletWithdrawal(Operation operation, String userWalletCode) {
        validateWalletOperation(operation, OperationType.WALLET_WITHDRAWAL);
        String countryCode = resolveOperationalCountryCode(operation.getAccountHolderId(), operation.getCurrency());
        String pspCode = resolveAccountCode(countryCode, CountryAccountSystemTag.PSP_CLEARING);
        return postSecureWalletDebit(
                operation,
                userWalletCode,
                pspCode,
                "Wallet withdrawal debit",
                "PSP payout clearing credit"
        );
    }

    @Transactional
    public AccountingEvent postSecureWalletDebit(Operation operation,
                                                 String sourceWalletCode,
                                                 String destinationAccountCode,
                                                 String debitDescription,
                                                 String creditDescription) {
        if (operation.getStatus() != OperationStatus.CONFIRMED) {
            throw new BusinessException("Only CONFIRMED operations can be posted");
        }
        if (sourceWalletCode == null || sourceWalletCode.isBlank()
                || destinationAccountCode == null || destinationAccountCode.isBlank()) {
            throw new BusinessException("Secure wallet debit account codes are required");
        }
        List<PostingLine> lines = List.of(
                new PostingLine(sourceWalletCode.trim(), LedgerEntryType.DEBIT,
                        operation.getAmount(), operation.getCurrency(), safeDescription(debitDescription)),
                new PostingLine(destinationAccountCode.trim(), LedgerEntryType.CREDIT,
                        operation.getAmount(), operation.getCurrency(), safeDescription(creditDescription))
        );
        return accountingEventRepository.findByOperationId(operation.getId())
                .orElseGet(() -> doPost(operation, lines, AccountingEventType.OPERATION_CONFIRMED));
    }

    @Transactional
    public AccountingEvent postTreasuryTransfer(Operation operation,
                                                String sourceAccountCode,
                                                String destinationAccountCode) {
        return postExplicitAccountTransfer(
                operation,
                sourceAccountCode,
                destinationAccountCode,
                "Treasury transfer source debit",
                "Treasury transfer destination credit"
        );
    }

    @Transactional
    public AccountingEvent postTreasuryCashout(Operation operation,
                                               String sourceAccountCode,
                                               String destinationAccountCode) {
        return postExplicitAccountTransfer(
                operation,
                sourceAccountCode,
                destinationAccountCode,
                "Treasury cashout source debit",
                "Treasury cashout destination credit"
        );
    }

    @Transactional
    public AccountingEvent postPspReconciliationAdjustment(Operation operation,
                                                           String countryCode,
                                                           BigDecimal signedDelta) {
        if (operation.getType() != OperationType.ADJUSTMENT) {
            throw new BusinessException("Operation type must be ADJUSTMENT");
        }
        if (operation.getStatus() != OperationStatus.CONFIRMED) {
            throw new BusinessException("Only CONFIRMED adjustments can be posted");
        }
        if (signedDelta == null || signedDelta.signum() == 0) {
            throw new BusinessException("Signed reconciliation delta is required");
        }

        BigDecimal amount = signedDelta.abs();
        String pspClearing = resolveAccountCode(countryCode, CountryAccountSystemTag.PSP_CLEARING);
        String platformAccount = resolveAccountCode(countryCode, CountryAccountSystemTag.PLATFORM_REVENUE);
        List<PostingLine> lines = signedDelta.signum() > 0
                ? List.of(
                new PostingLine(pspClearing, LedgerEntryType.DEBIT, amount, operation.getCurrency(),
                        "PSP reconciliation increase"),
                new PostingLine(platformAccount, LedgerEntryType.CREDIT, amount, operation.getCurrency(),
                        "PSP reconciliation counter-account")
        )
                : List.of(
                new PostingLine(platformAccount, LedgerEntryType.DEBIT, amount, operation.getCurrency(),
                        "PSP reconciliation counter-account"),
                new PostingLine(pspClearing, LedgerEntryType.CREDIT, amount, operation.getCurrency(),
                        "PSP reconciliation decrease")
        );
        return accountingEventRepository.findByOperationId(operation.getId())
                .orElseGet(() -> doPost(operation, lines, AccountingEventType.ADJUSTMENT_POSTED));
    }

    private AccountingEvent postExplicitAccountTransfer(Operation operation,
                                                        String sourceAccountCode,
                                                        String destinationAccountCode,
                                                        String debitDescription,
                                                        String creditDescription) {
        if (operation.getStatus() != OperationStatus.CONFIRMED) {
            throw new BusinessException("Only CONFIRMED operations can be posted");
        }
        if (sourceAccountCode == null || sourceAccountCode.isBlank()
                || destinationAccountCode == null || destinationAccountCode.isBlank()) {
            throw new BusinessException("Explicit transfer account codes are required");
        }
        List<PostingLine> lines = List.of(
                new PostingLine(sourceAccountCode.trim(), LedgerEntryType.DEBIT,
                        operation.getAmount(), operation.getCurrency(), debitDescription),
                new PostingLine(destinationAccountCode.trim(), LedgerEntryType.CREDIT,
                        operation.getAmount(), operation.getCurrency(), creditDescription)
        );
        return accountingEventRepository.findByOperationId(operation.getId())
                .orElseGet(() -> doPost(operation, lines, AccountingEventType.ADJUSTMENT_POSTED));
    }

    private void validateWalletOperation(Operation operation, OperationType expected) {
        if (operation.getType() != expected) {
            throw new BusinessException("Operation type must be " + expected.name()
                    + ", got: " + operation.getType());
        }
        if (operation.getStatus() != OperationStatus.CONFIRMED) {
            throw new BusinessException("Only CONFIRMED operations can be posted");
        }
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
            case ADJUSTMENT -> throw new BusinessException(
                    "Generic ADJUSTMENT posting is forbidden; use an explicit adjustment posting flow"
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
            case MARKETPLACE_PAYMENT -> throw new BusinessException(
                    "Use postMarketplacePayment for MARKETPLACE_PAYMENT operations"
            );
            case MARKETPLACE_MERCHANT_PAYOUT -> throw new BusinessException(
                    "Use postMarketplaceMerchantPayout for MARKETPLACE_MERCHANT_PAYOUT operations"
            );
            case MARKETPLACE_COMMISSION_ALLOCATION -> throw new BusinessException(
                    "Use postMarketplaceCommissionAllocation for MARKETPLACE_COMMISSION_ALLOCATION operations"
            );
            case MARKETPLACE_MERCHANT_PAYOUT_REVERSAL -> throw new BusinessException(
                    "Use postMarketplaceMerchantPayoutReversal for MARKETPLACE_MERCHANT_PAYOUT_REVERSAL operations"
            );
            case MARKETPLACE_COMMISSION_ALLOCATION_REVERSAL -> throw new BusinessException(
                    "Use postMarketplaceCommissionAllocationReversal for MARKETPLACE_COMMISSION_ALLOCATION_REVERSAL operations"
            );
            case COMMERCE_DSB_PAYOUT -> throw new BusinessException(
                    "Use postCommerceDsbPayout for COMMERCE_DSB_PAYOUT operations"
            );
            case COMMERCE_DSB_REVERSAL -> throw new BusinessException(
                    "Use WalletDebitFacade for COMMERCE_DSB_REVERSAL operations"
            );
            case WALLET_PAYMENT, WALLET_DEPOSIT_PSP, WALLET_DEPOSIT_AGENT, WALLET_WITHDRAWAL ->
                    throw new BusinessException(
                            "Use the dedicated wallet posting method for type: " + operation.getType()
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

    private List<PostingLine> buildMarketplaceAllocationLines(Operation operation,
                                                              MarketplaceAllocationProfile profile,
                                                              boolean allocateOutOfPool) {
        BigDecimal mlmAmount = allocationAmount(operation.getAmount(), profile.mlmPercent());
        BigDecimal guaranteeAmount = allocationAmount(operation.getAmount(), profile.guaranteePercent());
        BigDecimal platformAmount = allocationAmount(operation.getAmount(), profile.platformPercent());
        BigDecimal administrationAmount = allocationAmount(operation.getAmount(), profile.administrationPercent());
        BigDecimal socialAmount = operation.getAmount()
                .subtract(mlmAmount)
                .subtract(guaranteeAmount)
                .subtract(platformAmount)
                .subtract(administrationAmount)
                .setScale(4, RoundingMode.HALF_UP);

        if (allocateOutOfPool) {
            return List.of(
                    new PostingLine(profile.marketplacePoolAccountCode(), LedgerEntryType.DEBIT,
                            operation.getAmount(), operation.getCurrency(), "Marketplace commission allocation"),
                    new PostingLine(profile.guaranteeFundAccountCode(), LedgerEntryType.CREDIT,
                            guaranteeAmount, operation.getCurrency(), "Marketplace guarantee allocation"),
                    new PostingLine(profile.platformFeeAccountCode(), LedgerEntryType.CREDIT,
                            platformAmount, operation.getCurrency(), "Marketplace platform fee allocation"),
                    new PostingLine(profile.administrationAccountCode(), LedgerEntryType.CREDIT,
                            administrationAmount, operation.getCurrency(), "Marketplace administration allocation"),
                    new PostingLine(profile.socialFundAccountCode(), LedgerEntryType.CREDIT,
                            socialAmount, operation.getCurrency(), "Marketplace social fund allocation"),
                    new PostingLine(profile.mlmPoolAccountCode(), LedgerEntryType.CREDIT,
                            mlmAmount, operation.getCurrency(), "Marketplace MLM pool allocation")
            );
        }

        return List.of(
                new PostingLine(profile.guaranteeFundAccountCode(), LedgerEntryType.DEBIT,
                        guaranteeAmount, operation.getCurrency(), "Marketplace guarantee allocation reversal"),
                new PostingLine(profile.platformFeeAccountCode(), LedgerEntryType.DEBIT,
                        platformAmount, operation.getCurrency(), "Marketplace platform fee allocation reversal"),
                new PostingLine(profile.administrationAccountCode(), LedgerEntryType.DEBIT,
                        administrationAmount, operation.getCurrency(), "Marketplace administration allocation reversal"),
                new PostingLine(profile.socialFundAccountCode(), LedgerEntryType.DEBIT,
                        socialAmount, operation.getCurrency(), "Marketplace social fund allocation reversal"),
                new PostingLine(profile.mlmPoolAccountCode(), LedgerEntryType.DEBIT,
                        mlmAmount, operation.getCurrency(), "Marketplace MLM pool allocation reversal"),
                new PostingLine(profile.marketplacePoolAccountCode(), LedgerEntryType.CREDIT,
                        operation.getAmount(), operation.getCurrency(), "Marketplace pool restoration")
        );
    }

    private MarketplaceAllocationProfile resolveMarketplaceAllocationProfile(String countryCode) {
        BigDecimal totalPercent = MARKETPLACE_MLM_PERCENT
                .add(MARKETPLACE_GUARANTEE_PERCENT)
                .add(MARKETPLACE_PLATFORM_PERCENT)
                .add(MARKETPLACE_ADMINISTRATION_PERCENT)
                .add(MARKETPLACE_SOCIAL_PERCENT)
                .setScale(4, RoundingMode.HALF_UP);
        if (totalPercent.compareTo(ONE_HUNDRED.setScale(4, RoundingMode.HALF_UP)) != 0) {
            throw new BusinessException("Marketplace allocation percentages must sum to 100");
        }
        return new MarketplaceAllocationProfile(
                resolveAccountCode(countryCode, CountryAccountSystemTag.MARKETPLACE_POOL),
                resolveAccountCode(countryCode, CountryAccountSystemTag.MLM_POOL),
                countryChartOfAccountsService.findOrCreate(countryCode, CountryAccountGroup.GUARANTEE_FUND, "Guarantee Fund").getAccountCode(),
                resolveAccountCode(countryCode, CountryAccountSystemTag.PLATFORM_REVENUE),
                countryChartOfAccountsService.findOrCreate(countryCode, CountryAccountGroup.ADMINISTRATION, "Administration").getAccountCode(),
                countryChartOfAccountsService.findOrCreate(countryCode, CountryAccountGroup.SOCIAL_FUND, "Social Fund").getAccountCode(),
                MARKETPLACE_GUARANTEE_PERCENT,
                MARKETPLACE_PLATFORM_PERCENT,
                MARKETPLACE_ADMINISTRATION_PERCENT,
                MARKETPLACE_SOCIAL_PERCENT,
                MARKETPLACE_MLM_PERCENT
        );
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

    private String safeDescription(String description) {
        if (description == null || description.isBlank()) {
            return "Wallet secure debit";
        }
        return description;
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

    private record MarketplaceAllocationProfile(
            String marketplacePoolAccountCode,
            String mlmPoolAccountCode,
            String guaranteeFundAccountCode,
            String platformFeeAccountCode,
            String administrationAccountCode,
            String socialFundAccountCode,
            BigDecimal guaranteePercent,
            BigDecimal platformPercent,
            BigDecimal administrationPercent,
            BigDecimal socialPercent,
            BigDecimal mlmPercent
    ) {
    }
}
