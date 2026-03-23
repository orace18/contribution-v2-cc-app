package com.ccapp.identity.wallet.repository;

import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.wallet.domain.enums.WalletType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    List<Wallet> findByAccountHolderIdOrderByCreatedAtDesc(UUID accountHolderId);

    Optional<Wallet> findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(UUID accountHolderId, String status);

    /** Used for phone-based wallet identity lookup (1 phone = 1 wallet). */
    Optional<Wallet> findByMobileMoneyNumber(String mobileMoneyNumber);

    /** Used by agent deposit to locate the AGENT wallet of a given account holder. */
    Optional<Wallet> findFirstByAccountHolderIdAndWalletTypeAndStatus(
            UUID accountHolderId, WalletType walletType, String status);
}
