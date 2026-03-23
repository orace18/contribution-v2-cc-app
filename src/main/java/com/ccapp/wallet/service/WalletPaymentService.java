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
import com.ccapp.wallet.dto.WalletPayRequest;
import com.ccapp.wallet.dto.WalletPayResponse;
import com.ccapp.wallet.event.WalletCreditedEvent;
import com.ccapp.wallet.event.WalletDebitedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles peer-to-peer wallet payments (POST /wallet/pay).
 *
 * <p>Flow:
 * <ol>
 *   <li>Resolve sender wallet via accountHolderId</li>
 *   <li>Guard check: ACTIVE + sufficient available_balance</li>
 *   <li>Resolve destination wallet via phone number</li>
 *   <li>AML limit check on destination</li>
 *   <li>Create idempotent operation</li>
 *   <li>Confirm + post double-entry to ledger</li>
 *   <li>Emit WALLET_DEBITED + WALLET_CREDITED events</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WalletPaymentService {

    private final WalletRepository walletRepository;
    private final WalletGuardService walletGuardService;
    private final WalletLimitService walletLimitService;
    private final OperationService operationService;
    private final AccountingPostingService accountingPostingService;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final AccountHolderRepository accountHolderRepository;
    private final DomainEventPublisher eventPublisher;

    public WalletPayResponse pay(UUID senderAccountHolderId, UUID actorUserId, WalletPayRequest request) {
        // 1. Sender wallet + guard
        Wallet senderWallet = walletGuardService.requireActiveWallet(senderAccountHolderId);
        walletGuardService.checkDebitAllowed(senderWallet, request.amount(), request.currency());

        // 2. Destination wallet by phone (1 phone = 1 wallet rule)
        Wallet destinationWallet = walletRepository.findByMobileMoneyNumber(request.destinationPhone())
                .orElseThrow(() -> new BusinessException(
                        "No wallet found for phone: " + request.destinationPhone()));
        if (!"ACTIVE".equals(destinationWallet.getStatus())) {
            throw new BusinessException("Destination wallet is not active");
        }
        if (senderWallet.getId().equals(destinationWallet.getId())) {
            throw new BusinessException("Cannot pay to own wallet");
        }

        // 3. AML limit on destination
        walletLimitService.checkDepositAllowed(destinationWallet, request.amount(), request.currency());

        // 4. Resolve ledger account codes
        AccountHolder senderHolder = resolveHolder(senderAccountHolderId);
        AccountHolder destinationHolder = resolveHolder(destinationWallet.getAccountHolderId());
        String senderCode = countryChartOfAccountsService.findOrCreateWallet(senderHolder).getAccountCode();
        String destinationCode = countryChartOfAccountsService.findOrCreateWallet(destinationHolder).getAccountCode();

        // 5. Idempotent operation
        Operation operation = operationService.createInitiatedOperation(
                senderAccountHolderId,
                actorUserId,
                OperationType.WALLET_PAYMENT,
                request.amount(),
                request.currency(),
                request.idempotencyKey(),
                request.description()
        );

        // 6. Confirm + double-entry post (atomic, no PSP involved)
        operation = operationService.confirmOperation(operation.getId());
        accountingPostingService.postWalletPayment(operation, senderCode, destinationCode);

        // 7. Events
        eventPublisher.publish(WalletDebitedEvent.create(
                senderWallet.getId(), senderAccountHolderId,
                request.amount(), request.currency(), operation.getId()));
        eventPublisher.publish(WalletCreditedEvent.create(
                destinationWallet.getId(), destinationWallet.getAccountHolderId(),
                request.amount(), request.currency(), operation.getId()));

        log.info("wallet_payment_done operationId={} sender={} destination={} amount={} currency={}",
                operation.getId(), senderAccountHolderId,
                destinationWallet.getAccountHolderId(), request.amount(), request.currency());

        return new WalletPayResponse(
                operation.getId(), operation.getStatus().name(),
                request.amount(), request.currency());
    }

    private AccountHolder resolveHolder(UUID id) {
        return accountHolderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Account holder not found: " + id));
    }
}
