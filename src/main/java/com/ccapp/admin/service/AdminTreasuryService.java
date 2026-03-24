package com.ccapp.admin.service;

import com.ccapp.admin.dto.TreasuryAccountResponse;
import com.ccapp.admin.dto.TreasuryCashoutRequest;
import com.ccapp.admin.dto.TreasuryOperationResponse;
import com.ccapp.admin.dto.TreasuryTransferRequest;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import com.ccapp.country.config.domain.enums.CountryChartAccountStatus;
import com.ccapp.country.config.repository.CountryChartAccountRepository;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountCreatedVia;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.ledger.domain.entity.AccountBalanceSnapshot;
import com.ccapp.ledger.repository.AccountBalanceSnapshotRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminTreasuryService {

    private final AccountBalanceSnapshotRepository snapshotRepository;
    private final CountryChartAccountRepository countryChartAccountRepository;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final AccountHolderRepository accountHolderRepository;
    private final AppUserRepository appUserRepository;
    private final OperationService operationService;
    private final AccountingPostingService accountingPostingService;

    @Transactional(readOnly = true)
    public List<TreasuryAccountResponse> getAccounts(String currency) {
        List<AccountBalanceSnapshot> snapshots = (currency == null || currency.isBlank())
                ? snapshotRepository.findAllByOrderByAccountCodeAsc()
                : snapshotRepository.findByCurrencyOrderByAccountCodeAsc(currency.trim().toUpperCase(Locale.ROOT));

        return snapshots.stream()
                .map(snapshot -> new TreasuryAccountResponse(
                        snapshot.getAccountCode(),
                        snapshot.getCurrency(),
                        snapshot.getBalance(),
                        snapshot.getUpdatedAt()
                ))
                .toList();
    }

    @Transactional
    public TreasuryOperationResponse transfer(TreasuryTransferRequest request) {
        String currency = normalizeCurrency(request.currency());
        String sourceCode = normalizeAccountCode(request.sourceAccountCode());
        String destinationCode = normalizeAccountCode(request.destinationAccountCode());
        if (sourceCode.equalsIgnoreCase(destinationCode)) {
            throw new BusinessException("Source and destination accounts must be different");
        }

        CountryChartAccount source = resolveOperationalAccount(sourceCode, currency);
        CountryChartAccount destination = resolveOperationalAccount(destinationCode, currency);
        if (!source.getCountryCode().equalsIgnoreCase(destination.getCountryCode())) {
            throw new BusinessException("Treasury transfers must stay within the same operational country");
        }

        assertSufficientBalance(sourceCode, currency, request.amount(), "Insufficient source treasury balance");
        TreasuryOperator operator = resolveOrCreateTreasuryOperator(source.getCountryCode());
        Operation operation = operationService.createInitiatedOperation(
                operator.accountHolderId(),
                operator.appUserId(),
                OperationType.ADJUSTMENT,
                request.amount(),
                currency,
                "treasury-transfer:" + UUID.randomUUID(),
                treasuryDescription("Treasury transfer", request.reason())
        );
        Operation confirmed = operationService.confirmOperation(operation.getId());
        accountingPostingService.postTreasuryTransfer(confirmed, sourceCode, destinationCode);

        return new TreasuryOperationResponse(
                confirmed.getId(),
                "TRANSFER",
                "Treasury transfer posted to the ledger",
                OffsetDateTime.now()
        );
    }

    @Transactional
    public TreasuryOperationResponse cashout(TreasuryCashoutRequest request) {
        String currency = normalizeCurrency(request.currency());
        String sourceCode = normalizeAccountCode(request.accountCode());
        CountryChartAccount source = resolveOperationalAccount(sourceCode, currency);
        assertSufficientBalance(sourceCode, currency, request.amount(), "Insufficient treasury balance for cashout");

        String destinationCode = countryChartOfAccountsService.findOrCreate(
                source.getCountryCode(),
                CountryAccountGroup.CASH,
                "Treasury cash out"
        ).getAccountCode();
        if (sourceCode.equalsIgnoreCase(destinationCode)) {
            throw new BusinessException("Treasury cashout source and destination must be different");
        }

        TreasuryOperator operator = resolveOrCreateTreasuryOperator(source.getCountryCode());
        Operation operation = operationService.createInitiatedOperation(
                operator.accountHolderId(),
                operator.appUserId(),
                OperationType.ADJUSTMENT,
                request.amount(),
                currency,
                "treasury-cashout:" + UUID.randomUUID(),
                treasuryDescription("Treasury cashout", request.reason())
        );
        Operation confirmed = operationService.confirmOperation(operation.getId());
        accountingPostingService.postTreasuryCashout(confirmed, sourceCode, destinationCode);

        return new TreasuryOperationResponse(
                confirmed.getId(),
                "CASHOUT",
                "Treasury cashout posted to the ledger",
                OffsetDateTime.now()
        );
    }

    private CountryChartAccount resolveOperationalAccount(String accountCode, String currency) {
        return countryChartAccountRepository.findFirstByAccountCodeAndCurrencyAndStatus(
                        accountCode,
                        currency,
                        CountryChartAccountStatus.ACTIVE
                )
                .or(() -> countryChartAccountRepository.findFirstByAccountCodeAndStatus(
                        accountCode,
                        CountryChartAccountStatus.ACTIVE
                ))
                .orElseThrow(() -> new BusinessException("Treasury account not found: " + accountCode));
    }

    private void assertSufficientBalance(String accountCode,
                                         String currency,
                                         BigDecimal amount,
                                         String message) {
        BigDecimal balance = snapshotRepository.findByAccountCodeAndCurrency(accountCode, currency)
                .map(AccountBalanceSnapshot::getBalance)
                .orElse(BigDecimal.ZERO);
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException(message);
        }
    }

    private TreasuryOperator resolveOrCreateTreasuryOperator(String countryCode) {
        UUID accountHolderId = UUID.nameUUIDFromBytes(("treasury-holder:" + countryCode).getBytes(StandardCharsets.UTF_8));
        UUID appUserId = UUID.nameUUIDFromBytes(("treasury-user:" + countryCode).getBytes(StandardCharsets.UTF_8));

        accountHolderRepository.findById(accountHolderId).orElseGet(() -> accountHolderRepository.save(AccountHolder.builder()
                .id(accountHolderId)
                .firstName("Treasury")
                .lastName(countryCode.toUpperCase(Locale.ROOT))
                .countryCode(countryCode.toUpperCase(Locale.ROOT))
                .serviceCountryCode(countryCode.toUpperCase(Locale.ROOT))
                .accountType(AccountType.STANDARD)
                .createdVia(AccountCreatedVia.SELF)
                .kycStatus(KycStatus.VERIFIED)
                .kycVerifiedAt(OffsetDateTime.now())
                .build()));

        appUserRepository.findById(appUserId).orElseGet(() -> appUserRepository.save(AppUser.builder()
                .id(appUserId)
                .firebaseUid("system:treasury:" + countryCode.toLowerCase(Locale.ROOT))
                .accountHolderId(accountHolderId)
                .role(UserRole.USER)
                .build()));

        return new TreasuryOperator(accountHolderId, appUserId);
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BusinessException("Currency is required");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeAccountCode(String accountCode) {
        if (accountCode == null || accountCode.isBlank()) {
            throw new BusinessException("Account code is required");
        }
        return accountCode.trim().toUpperCase(Locale.ROOT);
    }

    private String treasuryDescription(String prefix, String reason) {
        if (reason == null || reason.isBlank()) {
            return prefix;
        }
        return prefix + " - " + reason.trim();
    }

    private record TreasuryOperator(UUID accountHolderId, UUID appUserId) {
    }
}
