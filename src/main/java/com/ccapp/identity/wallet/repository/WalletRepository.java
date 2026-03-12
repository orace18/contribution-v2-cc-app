package com.ccapp.identity.wallet.repository;

import com.ccapp.identity.wallet.domain.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    List<Wallet> findByAccountHolderIdOrderByCreatedAtDesc(UUID accountHolderId);

    Optional<Wallet> findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(UUID accountHolderId, String status);
}
