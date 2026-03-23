package com.ccapp.wallet.repository;

import com.ccapp.wallet.domain.entity.WalletOtpChallenge;
import com.ccapp.wallet.domain.enums.WalletOtpChallengeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletOtpChallengeRepository extends JpaRepository<WalletOtpChallenge, UUID> {

    Optional<WalletOtpChallenge> findByIdAndWalletId(UUID id, UUID walletId);

    List<WalletOtpChallenge> findByOperationIdAndStatus(UUID operationId, WalletOtpChallengeStatus status);

    List<WalletOtpChallenge> findByWalletIdAndOperationTypeAndFingerprintHashAndStatus(UUID walletId,
                                                                                        com.ccapp.payments.operation.domain.enums.OperationType operationType,
                                                                                        String fingerprintHash,
                                                                                        WalletOtpChallengeStatus status);
}
