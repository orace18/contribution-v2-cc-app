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
import com.ccapp.wallet.domain.enums.WalletType;
import com.ccapp.wallet.dto.WalletAgentDepositRequest;
import com.ccapp.wallet.dto.WalletDepositResponse;
import com.ccapp.wallet.event.WalletCreditedEvent;
import com.ccapp.wallet.event.WalletDebitedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Handles wallet deposits:
 * <ul>
 *   <li>PSP deposit – user initiates payment via PSP; ledger posted after callback confirms.</li>
 *   <li>Agent cash-in – agent wallet debited, user wallet credited; full traceability.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WalletDepositService {

    private final WalletRepository walletRepository;
    private final WalletGuardService walletGuardService;
    private final WalletLimitService walletLimitService;
    private final OperationService operationService;
    private final AccountingPostingService accountingPostingService;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final AccountHolderRepository accountHolderRepository;
    private final DomainEventPublisher eventPublisher;

    // -------------------------------------------------------------------------
    // PSP deposit
    // -------------------------------------------------------------------------

    /**
     * Initiates a PSP deposit. The operation is left in INITIATED state.
     * Call {@link #confirmPspDeposit} after the PSP webhook confirms payment.
     */
    public WalletDepositResponse initiatePspDeposit(UUID accountHolderId, UUID actorUserId,
                                                     BigDecimal amount, String currency,
                                                     String idempotencyKey) {
        Wallet wallet = walletGuardService.requireActiveWallet(accountHolderId);
        walletLimitService.checkDepositAllowed(wallet, amount, currency);

        Operation operation = operationService.createInitiatedOperation(
                accountHolderId, actorUserId,
                OperationType.WALLET_DEPOSIT_PSP,
                amount, currency, idempotencyKey,
                "PSP wallet deposit");

        log.info("wallet_psp_deposit_initiated operationId={} accountHolderId={} amount={} currency={}",
                operation.getId(), accountHolderId, amount, currency);

        return new WalletDepositResponse(operation.getId(), operation.getStatus().name(), amount, currency);
    }

    /**
     * Called once the PSP confirms payment (e.g. from JawuPay webhook).
     * Posts the double-entry to the ledger and emits WALLET_CREDITED.
     */
    public void confirmPspDeposit(UUID operationId) {
        Operation operation = operationService.confirmOperation(operationId);

        Wallet wallet = walletGuardService.requireActiveWallet(operation.getAccountHolderId());
        AccountHolder holder = resolveHolder(operation.getAccountHolderId());
        String walletCode = countryChartOfAccountsService.findOrCreateWallet(holder).getAccountCode();

        accountingPostingService.postWalletDepositPsp(operation, walletCode);

        eventPublisher.publish(WalletCreditedEvent.create(
                wallet.getId(), operation.getAccountHolderId(),
                operation.getAmount(), operation.getCurrency(), operation.getId()));

        log.info("wallet_psp_deposit_confirmed operationId={} accountHolderId={} amount={} currency={}",
                operation.getId(), operation.getAccountHolderId(),
                operation.getAmount(), operation.getCurrency());
    }

    // -------------------------------------------------------------------------
    // Agent cash-in
    // -------------------------------------------------------------------------

    /**
     * Agent deposits cash into a user's wallet.
     *
     * <p>Full traceability: the agent's AGENT wallet is debited (float consumed),
     * the user's USER wallet is credited. Both sides go through the ledger.
     */
    public WalletDepositResponse agentDeposit(UUID agentAccountHolderId, UUID actorUserId,
                                               WalletAgentDepositRequest request) {
        // 1. Agent must have an active AGENT wallet (float account)
        Wallet agentWallet = walletRepository
                .findFirstByAccountHolderIdAndWalletTypeAndStatus(
                        agentAccountHolderId, WalletType.AGENT, "ACTIVE")
                .orElseThrow(() -> new BusinessException("Agent has no active AGENT wallet"));

        // 2. Guard: agent float must cover the amount
        walletGuardService.checkDebitAllowed(agentWallet, request.amount(), request.currency());

        // 3. Target user wallet
        Wallet userWallet = walletGuardService.requireActiveWallet(request.targetAccountHolderId());

        // 4. AML limit on target
        walletLimitService.checkDepositAllowed(userWallet, request.amount(), request.currency());

        // 5. Ledger account codes
        AccountHolder agentHolder = resolveHolder(agentAccountHolderId);
        AccountHolder userHolder = resolveHolder(request.targetAccountHolderId());
        String agentCode = countryChartOfAccountsService.findOrCreateWallet(agentHolder).getAccountCode();
        String userCode = countryChartOfAccountsService.findOrCreateWallet(userHolder).getAccountCode();

        // 6. Operation (accountHolder = user being credited, actor = agent)
        Operation operation = operationService.createInitiatedOperation(
                request.targetAccountHolderId(), actorUserId,
                OperationType.WALLET_DEPOSIT_AGENT,
                request.amount(), request.currency(),
                request.idempotencyKey(),
                "Agent cash-in from " + agentAccountHolderId);

        operation = operationService.confirmOperation(operation.getId());
        accountingPostingService.postWalletDepositAgent(operation, agentCode, userCode);

        // 7. Events – agent debited, user credited
        eventPublisher.publish(WalletDebitedEvent.create(
                agentWallet.getId(), agentAccountHolderId,
                request.amount(), request.currency(), operation.getId()));
        eventPublisher.publish(WalletCreditedEvent.create(
                userWallet.getId(), request.targetAccountHolderId(),
                request.amount(), request.currency(), operation.getId()));

        log.info("wallet_agent_deposit_done operationId={} agent={} user={} amount={} currency={}",
                operation.getId(), agentAccountHolderId,
                request.targetAccountHolderId(), request.amount(), request.currency());

        return new WalletDepositResponse(
                operation.getId(), operation.getStatus().name(),
                request.amount(), request.currency());
    }

    private AccountHolder resolveHolder(UUID id) {
        return accountHolderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Account holder not found: " + id));
    }
}
