package com.ccapp.wallet.repository;

import com.ccapp.wallet.domain.entity.WalletLimitConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalletLimitConfigRepository extends JpaRepository<WalletLimitConfig, UUID> {

    Optional<WalletLimitConfig> findByCountryCodeAndKycLevelAndCurrency(
            String countryCode, String kycLevel, String currency);
}
