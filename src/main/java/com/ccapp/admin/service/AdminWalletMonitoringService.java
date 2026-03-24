package com.ccapp.admin.service;

import com.ccapp.admin.dto.AdminWalletMonitoringResponse;
import com.ccapp.admin.dto.AdminWalletTransactionResponse;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.wallet.service.WalletBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminWalletMonitoringService {

    private final AccountHolderRepository accountHolderRepository;
    private final WalletRepository walletRepository;
    private final CountryContextService countryContextService;
    private final WalletBalanceService walletBalanceService;
    private final OperationRepository operationRepository;

    @Transactional(readOnly = true)
    public AdminWalletMonitoringResponse searchByPhone(String phone) {
        String normalizedPhone = normalizePhone(phone);
        AccountHolder holder = resolveAccountHolder(normalizedPhone);
        Wallet wallet = walletRepository.findByAccountHolderIdOrderByCreatedAtDesc(holder.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Wallet not found for phone"));

        CountryContext countryContext = countryContextService.resolve(holder.getId());
        BigDecimal balance = walletBalanceService.getMainBalance(wallet, countryContext.currency());
        BigDecimal reservedBalance = walletBalanceService.getReservedBalance(wallet);
        BigDecimal availableBalance = balance.subtract(reservedBalance);
        List<AdminWalletTransactionResponse> recentTransactions = operationRepository.findRecentForAccountHolder(
                        holder.getId(),
                        PageRequest.of(0, 20)
                ).stream()
                .map(operation -> toTransaction(operation, holder.getId()))
                .toList();

        return new AdminWalletMonitoringResponse(
                wallet.getId(),
                holder.getId(),
                buildFullName(holder),
                holder.getPhoneNumber(),
                wallet.getMobileMoneyNumber(),
                wallet.getStatus(),
                balance,
                reservedBalance,
                availableBalance,
                countryContext.currency(),
                recentTransactions
        );
    }

    private AccountHolder resolveAccountHolder(String normalizedPhone) {
        return accountHolderRepository.findByPhoneNumber(normalizedPhone)
                .or(() -> walletRepository.findByMobileMoneyNumber(normalizedPhone)
                        .flatMap(wallet -> accountHolderRepository.findById(wallet.getAccountHolderId())))
                .orElseThrow(() -> new NotFoundException("Wallet not found for phone"));
    }

    private AdminWalletTransactionResponse toTransaction(Operation operation, UUID accountHolderId) {
        return new AdminWalletTransactionResponse(
                operation.getId(),
                operation.getType(),
                operation.getStatus(),
                resolveDirection(operation, accountHolderId),
                operation.getAmount(),
                operation.getCurrency(),
                operation.getDescription(),
                operation.getIdempotencyKey(),
                operation.getDestinationPhone(),
                operation.getCreatedAt()
        );
    }

    private String resolveDirection(Operation operation, UUID accountHolderId) {
        boolean source = accountHolderId.equals(operation.getAccountHolderId());
        boolean destination = accountHolderId.equals(operation.getDestinationAccountHolderId());
        if (source && destination) {
            return "SELF";
        }
        if (source) {
            return "DEBIT";
        }
        if (destination) {
            return "CREDIT";
        }
        return "INFO";
    }

    private String buildFullName(AccountHolder holder) {
        return (holder.getFirstName() + " " + holder.getLastName()).trim();
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new NotFoundException("Phone is required");
        }
        return phone.trim();
    }
}
