package com.ccapp.wallet.repository;

import com.ccapp.wallet.domain.entity.WalletAmlAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WalletAmlAlertRepository extends JpaRepository<WalletAmlAlert, UUID> {

    List<WalletAmlAlert> findByWalletIdAndResolvedFalse(UUID walletId);

    List<WalletAmlAlert> findByResolvedFalseOrderByCreatedAtDesc();
}
