package com.ccapp.wallet.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.wallet.event.WalletCreditedEvent;
import com.ccapp.wallet.event.WalletDebitedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class WalletDebitFacade {

    private final WalletGuardService walletGuardService;
    private final WalletSecurityService walletSecurityService;
    private final OperationService operationService;
    private final AccountingPostingService accountingPostingService;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final AccountHolderRepository accountHolderRepository;
    private final DomainEventPublisher eventPublisher;

    public WalletDebitExecutionResult executeSecureDebit(SecureDebitRequest request) {
        validateRequest(request);

        OperationService.OperationRouting routing = new OperationService.OperationRouting(
                request.destinationAccountHolderId(),
                request.destinationWalletId(),
                request.destinationPhone(),
                request.destinationAccountCode()
        );

        Operation existing = operationService.findByIdempotencyKey(request.idempotencyKey())
                .map(found -> operationService.assertCompatibleExistingOperation(
                        found,
                        request.operationAccountHolderId(),
                        request.actorUserId(),
                        request.operationType(),
                        request.amount(),
                        request.currency(),
                        null,
                        routing
                ))
                .orElse(null);
        if (existing != null && existing.getStatus() == OperationStatus.CONFIRMED) {
            return WalletDebitExecutionResult.confirmed(existing);
        }

        Wallet sourceWallet = resolveSourceWallet(request);
        walletGuardService.checkDebitAllowed(sourceWallet, request.amount(), request.currency());

        WalletSecurityService.WalletAuthorizationResult securityResult = request.systemAuthorized()
                ? walletSecurityService.authorizeSystemDebit(
                        sourceWallet,
                        request.sourceAccountHolderId(),
                        request.operationType(),
                        request.amount(),
                        request.fingerprint(),
                        request.autoDebitAuthorized()
                )
                : walletSecurityService.authorizeDebitIntent(
                        sourceWallet,
                        request.sourceAccountHolderId(),
                        request.operationType(),
                        request.amount(),
                        request.pin(),
                        request.otpChallengeId(),
                        request.otpCode(),
                        request.fingerprint()
                );

        if (!securityResult.approved()) {
            return WalletDebitExecutionResult.challengeRequired(
                    securityResult.otpChallengeId(),
                    securityResult.otpExpiresAt()
            );
        }

        Operation operation = operationService.createInitiatedOperation(
                request.operationAccountHolderId(),
                request.actorUserId(),
                request.operationType(),
                request.amount(),
                request.currency(),
                request.idempotencyKey(),
                request.description(),
                null,
                routing
        );

        if (operation.getStatus() == OperationStatus.CONFIRMED) {
            return WalletDebitExecutionResult.confirmed(operation);
        }

        String sourceWalletCode = resolveWalletAccountCode(request.sourceAccountHolderId());
        operation = operationService.confirmOperation(operation.getId());
        accountingPostingService.postSecureWalletDebit(
                operation,
                sourceWalletCode,
                request.destinationAccountCode(),
                request.debitDescription(),
                request.creditDescription()
        );

        eventPublisher.publish(WalletDebitedEvent.create(
                sourceWallet.getId(),
                request.sourceAccountHolderId(),
                request.amount(),
                request.currency(),
                operation.getId()
        ));

        if (request.creditedWalletId() != null && request.creditedAccountHolderId() != null) {
            eventPublisher.publish(WalletCreditedEvent.create(
                    request.creditedWalletId(),
                    request.creditedAccountHolderId(),
                    request.amount(),
                    request.currency(),
                    operation.getId()
            ));
        }

        return WalletDebitExecutionResult.confirmed(operation);
    }

    private Wallet resolveSourceWallet(SecureDebitRequest request) {
        Wallet wallet = request.sourceWallet() != null
                ? request.sourceWallet()
                : walletGuardService.requireActiveWallet(request.sourceAccountHolderId());
        if (!wallet.getAccountHolderId().equals(request.sourceAccountHolderId())) {
            throw new BusinessException("Wallet owner mismatch for secure debit");
        }
        return wallet;
    }

    private String resolveWalletAccountCode(UUID accountHolderId) {
        AccountHolder holder = accountHolderRepository.findById(accountHolderId)
                .orElseThrow(() -> new BusinessException("Account holder not found: " + accountHolderId));
        return countryChartOfAccountsService.findOrCreateWallet(holder).getAccountCode();
    }

    private void validateRequest(SecureDebitRequest request) {
        if (request == null) {
            throw new BusinessException("Secure debit request is required");
        }
        if (request.operationAccountHolderId() == null || request.sourceAccountHolderId() == null || request.actorUserId() == null) {
            throw new BusinessException("Secure debit identities are required");
        }
        if (request.operationType() == null || request.amount() == null || request.amount().signum() <= 0) {
            throw new BusinessException("Secure debit type and positive amount are required");
        }
        if (request.currency() == null || request.currency().isBlank()) {
            throw new BusinessException("Secure debit currency is required");
        }
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new BusinessException("Secure debit idempotency key is required");
        }
        if (request.destinationAccountCode() == null || request.destinationAccountCode().isBlank()) {
            throw new BusinessException("Secure debit destination account is required");
        }
        if (request.fingerprint() == null || request.fingerprint().isBlank()) {
            throw new BusinessException("Secure debit fingerprint is required");
        }
        if (request.systemAuthorized() && request.pin() != null && !request.pin().isBlank()) {
            throw new BusinessException("System secure debit must not provide user PIN");
        }
    }

    public record SecureDebitRequest(
            UUID operationAccountHolderId,
            UUID sourceAccountHolderId,
            UUID actorUserId,
            Wallet sourceWallet,
            OperationType operationType,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            String description,
            String fingerprint,
            String destinationAccountCode,
            UUID destinationAccountHolderId,
            UUID destinationWalletId,
            String destinationPhone,
            String pin,
            UUID otpChallengeId,
            String otpCode,
            boolean systemAuthorized,
            boolean autoDebitAuthorized,
            String debitDescription,
            String creditDescription,
            UUID creditedWalletId,
            UUID creditedAccountHolderId
    ) {
    }

    public record WalletDebitExecutionResult(
            boolean approved,
            boolean otpRequired,
            UUID operationId,
            OffsetDateTime otpExpiresAt,
            UUID otpChallengeId
    ) {
        public static WalletDebitExecutionResult challengeRequired(UUID otpChallengeId, OffsetDateTime otpExpiresAt) {
            return new WalletDebitExecutionResult(false, true, null, otpExpiresAt, otpChallengeId);
        }

        public static WalletDebitExecutionResult confirmed(Operation operation) {
            return new WalletDebitExecutionResult(true, false, operation.getId(), null, null);
        }
    }
}
