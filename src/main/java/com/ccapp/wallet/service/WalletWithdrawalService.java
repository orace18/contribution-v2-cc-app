package com.ccapp.wallet.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.wallet.dto.WalletWithdrawRequest;
import com.ccapp.wallet.dto.WalletWithdrawResponse;
import com.ccapp.wallet.event.WalletDebitedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles wallet withdrawals (wallet → PSP payout).
 *
 * <p>Rules:
 * <ul>
 *   <li>KYC must be VERIFIED</li>
 *   <li>Wallet must be ACTIVE with sufficient available balance</li>
 *   <li>All operations go through the ledger (DEBIT wallet → CREDIT PSP_CLEARING)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WalletWithdrawalService {

    private final WalletGuardService walletGuardService;
    private final OperationService operationService;
    private final AccountingPostingService accountingPostingService;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final AccountHolderRepository accountHolderRepository;
    private final DomainEventPublisher eventPublisher;

    public WalletWithdrawResponse withdraw(UUID accountHolderId, UUID actorUserId,
                                            WalletWithdrawRequest request) {
        // 1. KYC gate
        AccountHolder holder = resolveHolder(accountHolderId);
        if (holder.getKycStatus() != KycStatus.VERIFIED) {
            throw new BusinessException(
                    "KYC verification is required for withdrawal. Current status: "
                            + holder.getKycStatus().name());
        }

        // 2. Wallet guard
        Wallet wallet = walletGuardService.requireActiveWallet(accountHolderId);
        walletGuardService.checkDebitAllowed(wallet, request.amount(), request.currency());

        // 3. Ledger account code
        String walletCode = countryChartOfAccountsService.findOrCreateWallet(holder).getAccountCode();

        // 4. Create + confirm operation
        Operation operation = operationService.createInitiatedOperation(
                accountHolderId, actorUserId,
                OperationType.WALLET_WITHDRAWAL,
                request.amount(), request.currency(),
                request.idempotencyKey(),
                "Wallet withdrawal – payout via JawuPay");

        operation = operationService.confirmOperation(operation.getId());

        // 5. Double-entry: DEBIT wallet → CREDIT PSP_CLEARING
        accountingPostingService.postWalletWithdrawal(operation, walletCode);

        // 6. Event
        eventPublisher.publish(WalletDebitedEvent.create(
                wallet.getId(), accountHolderId,
                request.amount(), request.currency(), operation.getId()));

        log.info("wallet_withdrawal_done operationId={} accountHolderId={} amount={} currency={}",
                operation.getId(), accountHolderId, request.amount(), request.currency());

        return new WalletWithdrawResponse(
                operation.getId(), operation.getStatus().name(),
                request.amount(), request.currency());
    }

    private AccountHolder resolveHolder(UUID id) {
        return accountHolderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Account holder not found: " + id));
    }
}
