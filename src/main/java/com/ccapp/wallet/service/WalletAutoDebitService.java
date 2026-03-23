package com.ccapp.wallet.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.wallet.domain.entity.WalletAutoDebitRule;
import com.ccapp.wallet.event.WalletDebitedEvent;
import com.ccapp.wallet.repository.WalletAutoDebitRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Auto-debit rule engine.
 *
 * <p>Triggered on each {@code WALLET_CREDITED} event. Executes all enabled rules
 * for the credited wallet in priority order. Each rule either:
 * <ul>
 *   <li>Debits a fixed {@code amountOrPercent}</li>
 *   <li>Debits {@code creditedAmount * amountOrPercent / 100} when {@code isPercent = true}</li>
 * </ul>
 *
 * <p>If the wallet has insufficient available balance for a rule, that rule is
 * skipped (non-fatal). Execution order is strictly priority ASC.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletAutoDebitService {

    private final WalletAutoDebitRuleRepository ruleRepository;
    private final WalletRepository walletRepository;
    private final WalletBalanceService walletBalanceService;
    private final OperationService operationService;
    private final AccountingPostingService accountingPostingService;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final AccountHolderRepository accountHolderRepository;
    private final DomainEventPublisher eventPublisher;

    /**
     * Executes all auto-debit rules for {@code walletId} triggered by an incoming credit.
     *
     * @param walletId        the wallet that was just credited
     * @param creditedAmount  amount that was credited (used to compute percentage debits)
     * @param currency        currency of the credit
     * @param creditOperationId the originating operation (used for idempotency)
     */
    @Transactional
    public void executeAutoDebits(UUID walletId, BigDecimal creditedAmount,
                                   String currency, UUID creditOperationId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new BusinessException("Wallet not found: " + walletId));

        List<WalletAutoDebitRule> rules =
                ruleRepository.findByWalletIdAndEnabledTrueOrderByPriorityAsc(walletId);

        if (rules.isEmpty()) {
            return;
        }

        log.info("wallet_auto_debit_engine_start walletId={} rules={} creditAmount={} currency={}",
                walletId, rules.size(), creditedAmount, currency);

        for (WalletAutoDebitRule rule : rules) {
            try {
                executeRule(wallet, rule, creditedAmount, currency, creditOperationId);
            } catch (BusinessException e) {
                // Non-fatal: log and continue to next rule
                log.warn("wallet_auto_debit_rule_skipped walletId={} ruleId={} ruleType={} reason={}",
                        walletId, rule.getId(), rule.getRuleType(), e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------

    private void executeRule(Wallet wallet, WalletAutoDebitRule rule,
                              BigDecimal creditedAmount, String currency, UUID creditOperationId) {
        BigDecimal debitAmount = rule.isPercent()
                ? creditedAmount.multiply(rule.getAmountOrPercent())
                        .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                : rule.getAmountOrPercent();

        // Re-read available balance after prior rules may have already debited
        BigDecimal available = walletBalanceService.getAvailableBalance(wallet, currency);
        if (available.compareTo(debitAmount) < 0) {
            log.info("wallet_auto_debit_skip_balance walletId={} ruleId={} ruleType={} required={} available={}",
                    wallet.getId(), rule.getId(), rule.getRuleType(), debitAmount, available);
            return;
        }

        // Idempotency: one auto-debit per rule per credit operation
        String idempotencyKey = "auto-debit:rule:" + rule.getId() + ":op:" + creditOperationId;

        AccountHolder holder = accountHolderRepository.findById(wallet.getAccountHolderId())
                .orElseThrow(() -> new BusinessException("Account holder not found"));
        String walletCode = countryChartOfAccountsService.findOrCreateWallet(holder).getAccountCode();

        Operation operation = operationService.createInitiatedOperation(
                wallet.getAccountHolderId(),
                wallet.getAccountHolderId(),
                OperationType.WALLET_PAYMENT,
                debitAmount,
                currency,
                idempotencyKey,
                "Auto-debit [" + rule.getRuleType().name() + "] priority=" + rule.getPriority()
        );

        operation = operationService.confirmOperation(operation.getId());
        accountingPostingService.postWalletPayment(operation, walletCode, rule.getDestinationAccountCode());

        eventPublisher.publish(WalletDebitedEvent.create(
                wallet.getId(), wallet.getAccountHolderId(), debitAmount, currency, operation.getId()));

        log.info("wallet_auto_debit_executed walletId={} ruleId={} ruleType={} amount={} dest={} operationId={}",
                wallet.getId(), rule.getId(), rule.getRuleType(),
                debitAmount, rule.getDestinationAccountCode(), operation.getId());
    }
}
