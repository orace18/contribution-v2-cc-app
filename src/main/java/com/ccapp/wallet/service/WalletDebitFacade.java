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
            ensurePosting(existing, request);
            return WalletDebitExecutionResult.confirmed(existing);
        }
        if (existing != null && existing.getStatus() == OperationStatus.PENDING_PSP) {
            return WalletDebitExecutionResult.pending(existing);
        }
        if (existing != null && existing.getStatus() != OperationStatus.INITIATED) {
            throw new BusinessException("Secure debit idempotency key is bound to a closed operation");
        }

        Wallet sourceWallet = resolveSourceWallet(request);
        walletGuardService.checkDebitAllowed(sourceWallet, request.amount(), request.currency());

        Operation operation = existing != null
                ? existing
                : operationService.createInitiatedOperation(
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
                        operation,
                        request.pin(),
                        request.otpChallengeId(),
                        request.otpCode(),
                        request.fingerprint()
                );

        if (!securityResult.approved()) {
            return WalletDebitExecutionResult.challengeRequired(
                    operation.getId(),
                    securityResult.otpChallengeId(),
                    securityResult.otpExpiresAt()
            );
        }

        if (operation.getStatus() == OperationStatus.CONFIRMED) {
            ensurePosting(operation, request);
            return WalletDebitExecutionResult.confirmed(operation);
        }
        if (request.postingMode() == SecureDebitPostingMode.NONE) {
            return WalletDebitExecutionResult.authorized(operation);
        }

        operation = operationService.confirmOperation(operation.getId());
        ensurePosting(operation, request);

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

    private void ensurePosting(Operation operation, SecureDebitRequest request) {
        switch (request.postingMode()) {
            case STANDARD -> accountingPostingService.postSecureWalletDebit(
                    operation,
                    resolveWalletAccountCode(request.sourceAccountHolderId()),
                    request.destinationAccountCode(),
                    request.debitDescription(),
                    request.creditDescription()
            );
            case OPERATION_POSTING -> accountingPostingService.postConfirmedOperation(operation.getId());
            case CONFIRM_ONLY -> {
            }
            case MARKETPLACE_PAYMENT -> accountingPostingService.postMarketplaceWalletPayment(
                    operation,
                    request.sourceAccountHolderId(),
                    request.requiredCountryCode()
            );
            case NONE -> {
            }
        }
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
        if (request.postingMode() == SecureDebitPostingMode.MARKETPLACE_PAYMENT
                && (request.countryCode() == null || request.countryCode().isBlank())) {
            throw new BusinessException("Marketplace wallet debit country is required");
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
            UUID creditedAccountHolderId,
            SecureDebitPostingMode postingMode,
            String countryCode
    ) {
        public String requiredCountryCode() {
            if (countryCode == null || countryCode.isBlank()) {
                throw new BusinessException("Secure debit country is required");
            }
            return countryCode.trim();
        }
    }

    public record WalletDebitExecutionResult(
            boolean approved,
            boolean otpRequired,
            UUID operationId,
            String status,
            OffsetDateTime otpExpiresAt,
            UUID otpChallengeId
    ) {
        public static WalletDebitExecutionResult challengeRequired(UUID operationId,
                                                                   UUID otpChallengeId,
                                                                   OffsetDateTime otpExpiresAt) {
            return new WalletDebitExecutionResult(false, true, operationId, OperationStatus.INITIATED.name(), otpExpiresAt, otpChallengeId);
        }

        public static WalletDebitExecutionResult confirmed(Operation operation) {
            return new WalletDebitExecutionResult(true, false, operation.getId(), operation.getStatus().name(), null, null);
        }

        public static WalletDebitExecutionResult authorized(Operation operation) {
            return new WalletDebitExecutionResult(true, false, operation.getId(), operation.getStatus().name(), null, null);
        }

        public static WalletDebitExecutionResult pending(Operation operation) {
            return new WalletDebitExecutionResult(true, false, operation.getId(), operation.getStatus().name(), null, null);
        }
    }

    public enum SecureDebitPostingMode {
        STANDARD,
        OPERATION_POSTING,
        CONFIRM_ONLY,
        MARKETPLACE_PAYMENT,
        NONE
    }
}
