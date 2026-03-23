package com.ccapp.wallet.repository;

import com.ccapp.wallet.domain.entity.WalletAutoDebitRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WalletAutoDebitRuleRepository extends JpaRepository<WalletAutoDebitRule, UUID> {

    List<WalletAutoDebitRule> findByWalletIdAndEnabledTrueOrderByPriorityAsc(UUID walletId);

    List<WalletAutoDebitRule> findByWalletIdAndEnabledTrueAndAutoDebitAuthorizedTrueOrderByPriorityAsc(UUID walletId);
}
