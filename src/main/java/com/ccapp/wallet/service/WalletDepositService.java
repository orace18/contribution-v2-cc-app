package com.ccapp.wallet.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.ledger.service.AccountingPostingService;
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
 * - PSP deposit
 * - Agent cash-in
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
    private final WalletDebitFacade walletDebitFacade;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final AccountHolderRepository accountHolderRepository;
    private final DomainEventPublisher eventPublisher;

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

        return new WalletDepositResponse(
                operation.getId(),
                operation.getStatus().name(),
                amount,
                currency,
                false,
                null,
                null
        );
    }

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

    public WalletDepositResponse agentDeposit(UUID agentAccountHolderId, UUID actorUserId,
                                              WalletAgentDepositRequest request) {
        Wallet agentWallet = walletRepository
                .findFirstByAccountHolderIdAndWalletTypeAndStatus(
                        agentAccountHolderId, WalletType.AGENT, "ACTIVE")
                .orElseThrow(() -> new BusinessException("Agent has no active AGENT wallet"));

        walletGuardService.checkDebitAllowed(agentWallet, request.amount(), request.currency());

        Wallet userWallet = walletGuardService.requireActiveWallet(request.targetAccountHolderId());
        walletLimitService.checkDepositAllowed(userWallet, request.amount(), request.currency());

        AccountHolder userHolder = resolveHolder(request.targetAccountHolderId());
        String userCode = countryChartOfAccountsService.findOrCreateWallet(userHolder).getAccountCode();

        WalletDebitFacade.WalletDebitExecutionResult result = walletDebitFacade.executeSecureDebit(
                new WalletDebitFacade.SecureDebitRequest(
                        request.targetAccountHolderId(),
                        agentAccountHolderId,
                        actorUserId,
                        agentWallet,
                        OperationType.WALLET_DEPOSIT_AGENT,
                        request.amount(),
                        request.currency(),
                        request.idempotencyKey(),
                        "Agent cash-in from " + agentAccountHolderId,
                        buildOtpFingerprint(request),
                        userCode,
                        request.targetAccountHolderId(),
                        userWallet.getId(),
                        null,
                        request.pin(),
                        request.otpChallengeId(),
                        request.otpCode(),
                        false,
                        false,
                        "Agent float debit - cash-in",
                        "User wallet credit - agent cash-in",
                        userWallet.getId(),
                        request.targetAccountHolderId()
                )
        );

        if (result.otpRequired()) {
            return new WalletDepositResponse(
                    result.operationId(),
                    "OTP_REQUIRED",
                    request.amount(),
                    request.currency(),
                    true,
                    result.otpChallengeId(),
                    result.otpExpiresAt()
            );
        }

        log.info("wallet_agent_deposit_done operationId={} agent={} user={} amount={} currency={}",
                result.operationId(), agentAccountHolderId,
                request.targetAccountHolderId(), request.amount(), request.currency());

        return confirmedResponse(result.operationId(), request.amount(), request.currency());
    }

    private AccountHolder resolveHolder(UUID id) {
        return accountHolderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Account holder not found: " + id));
    }

    private String buildOtpFingerprint(WalletAgentDepositRequest request) {
        return "wallet-agent-deposit|"
                + request.targetAccountHolderId()
                + "|" + request.amount().stripTrailingZeros().toPlainString()
                + "|" + request.currency().trim().toUpperCase();
    }

    private WalletDepositResponse confirmedResponse(UUID operationId, BigDecimal amount, String currency) {
        return new WalletDepositResponse(
                operationId,
                "CONFIRMED",
                amount,
                currency,
                false,
                null,
                null
        );
    }
}
