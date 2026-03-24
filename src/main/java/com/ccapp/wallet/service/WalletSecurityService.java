package com.ccapp.wallet.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.notifications.dispatcher.PhoneMessageGateway;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.wallet.domain.entity.WalletOtpChallenge;
import com.ccapp.wallet.domain.enums.WalletOtpChallengeStatus;
import com.ccapp.wallet.dto.WalletPinSetupRequest;
import com.ccapp.wallet.event.WalletLockedEvent;
import com.ccapp.wallet.event.WalletOtpFailedEvent;
import com.ccapp.wallet.event.WalletOtpSentEvent;
import com.ccapp.wallet.event.WalletPinFailedEvent;
import com.ccapp.wallet.repository.WalletOtpChallengeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WalletSecurityService {

    private final WalletRepository walletRepository;
    private final WalletOtpChallengeRepository walletOtpChallengeRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final CountryConfigResolver countryConfigResolver;
    private final PhoneMessageGateway phoneMessageGateway;
    private final DomainEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;

    @Value("${wallet.security.pin-required-default:true}")
    private boolean defaultPinRequired;

    @Value("${wallet.security.otp-threshold-default:100000}")
    private java.math.BigDecimal defaultOtpThreshold;

    @Value("${wallet.security.pin-max-attempts-default:3}")
    private int defaultPinMaxAttempts;

    @Value("${wallet.security.otp-max-attempts-default:3}")
    private int defaultOtpMaxAttempts;

    @Value("${wallet.security.otp-ttl-seconds-default:120}")
    private long defaultOtpTtlSeconds;

    @Value("${wallet.security.otp-code-length:6}")
    private int otpCodeLength;

    public void configurePin(UUID accountHolderId, WalletPinSetupRequest request) {
        Wallet wallet = walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(accountHolderId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("No active wallet found for account holder: " + accountHolderId));
        WalletSecurityPolicy policy = resolvePolicy(resolveAccountHolder(accountHolderId));

        if (!request.newPin().equals(request.confirmPin())) {
            throw new BusinessException("WALLET_PIN_CONFIRMATION_MISMATCH");
        }

        if (wallet.getPinHash() != null && !wallet.getPinHash().isBlank()) {
            if (request.currentPin() == null || request.currentPin().isBlank()) {
                throw new BusinessException("WALLET_CURRENT_PIN_REQUIRED");
            }
            assertPinValid(wallet, accountHolderId, OperationType.WALLET_PAYMENT, request.currentPin(), policy);
        }

        wallet.setPinHash(passwordEncoder.encode(request.newPin().trim()));
        wallet.setPinFailedAttempts(0);
        wallet.setLastPinChangedAt(OffsetDateTime.now());
        walletRepository.save(wallet);
    }

    public WalletAuthorizationResult authorizeDebit(Wallet wallet,
                                                    UUID accountHolderId,
                                                    Operation operation,
                                                    String pin,
                                                    UUID otpChallengeId,
                                                    String otpCode,
                                                    String fingerprint) {
        return authorizeDebitIntent(
                wallet,
                accountHolderId,
                operation,
                pin,
                otpChallengeId,
                otpCode,
                fingerprint
        );
    }

    public WalletAuthorizationResult authorizeDebitIntent(Wallet wallet,
                                                          UUID accountHolderId,
                                                          Operation operation,
                                                          String pin,
                                                          UUID otpChallengeId,
                                                          String otpCode,
                                                          String fingerprint) {
        if (operation == null || operation.getId() == null) {
            throw new BusinessException("WALLET_OPERATION_REQUIRED");
        }
        AccountHolder holder = resolveAccountHolder(accountHolderId);
        WalletSecurityPolicy policy = resolvePolicy(holder);
        OperationType operationType = operation.getType();
        BigDecimal amount = operation.getAmount();

        if (policy.pinRequired()) {
            if (pin == null || pin.isBlank()) {
                throw new BusinessException("WALLET_PIN_REQUIRED");
            }
            assertPinValid(wallet, accountHolderId, operationType, pin, policy);
        }

        if (!requiresOtp(policy, operationType, amount)) {
            return WalletAuthorizationResult.approvedResult();
        }

        if (otpChallengeId == null || otpCode == null || otpCode.isBlank()) {
            WalletOtpChallenge challenge = issueOtpChallenge(wallet, holder, operation, fingerprint, policy);
            return WalletAuthorizationResult.challengeRequired(challenge.getId(), challenge.getExpiresAt());
        }

        validateOtp(wallet, accountHolderId, operation, otpChallengeId, otpCode, fingerprint);
        return WalletAuthorizationResult.approvedResult();
    }

    public WalletAuthorizationResult authorizeAutoDebit(Wallet wallet,
                                                        UUID accountHolderId,
                                                        OperationType operationType,
                                                        BigDecimal amount,
                                                        String fingerprint,
                                                        boolean autoDebitAuthorized) {
        if (!autoDebitAuthorized) {
            throw new BusinessException("WALLET_AUTO_DEBIT_NOT_AUTHORIZED");
        }

        return authorizeSystemDebit(wallet, accountHolderId, operationType, amount, fingerprint, true);
    }

    public WalletAuthorizationResult authorizeSystemDebit(Wallet wallet,
                                                          UUID accountHolderId,
                                                          OperationType operationType,
                                                          BigDecimal amount,
                                                          String fingerprint,
                                                          boolean systemAuthorized) {
        if (!systemAuthorized) {
            throw new BusinessException("WALLET_SYSTEM_DEBIT_NOT_AUTHORIZED");
        }

        // System-triggered debits still require an identifiable wallet owner, type and amount.
        resolveAccountHolder(accountHolderId);
        if (operationType == null) {
            throw new BusinessException("WALLET_SYSTEM_DEBIT_OPERATION_TYPE_REQUIRED");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Amount must be positive");
        }
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new BusinessException("WALLET_SYSTEM_DEBIT_FINGERPRINT_REQUIRED");
        }
        if (wallet.getId() == null) {
            throw new BusinessException("WALLET_SYSTEM_DEBIT_WALLET_REQUIRED");
        }
        return WalletAuthorizationResult.approvedResult();
    }

    public void authorizeReservation(Wallet wallet,
                                     UUID accountHolderId,
                                     String pin,
                                     boolean systemReservation) {
        WalletSecurityPolicy policy = resolvePolicy(resolveAccountHolder(accountHolderId));
        if (systemReservation) {
            return;
        }
        if (pin == null || pin.isBlank()) {
            throw new BusinessException("WALLET_PIN_REQUIRED");
        }
        assertPinValid(wallet, accountHolderId, OperationType.WALLET_PAYMENT, pin, policy);
    }

    private void assertPinValid(Wallet wallet,
                                UUID accountHolderId,
                                OperationType operationType,
                                String providedPin,
                                WalletSecurityPolicy policy) {
        if (wallet.getPinHash() == null || wallet.getPinHash().isBlank()) {
            throw new BusinessException("WALLET_PIN_NOT_SET");
        }

        if (passwordEncoder.matches(providedPin.trim(), wallet.getPinHash())) {
            if (wallet.getPinFailedAttempts() != null && wallet.getPinFailedAttempts() > 0) {
                wallet.setPinFailedAttempts(0);
                walletRepository.save(wallet);
            }
            return;
        }

        int attempts = (wallet.getPinFailedAttempts() == null ? 0 : wallet.getPinFailedAttempts()) + 1;
        wallet.setPinFailedAttempts(attempts);

        int remainingAttempts = Math.max(policy.pinMaxAttempts() - attempts, 0);
        eventPublisher.publish(WalletPinFailedEvent.create(
                wallet.getId(),
                accountHolderId,
                operationType,
                remainingAttempts
        ));

        if (attempts >= policy.pinMaxAttempts()) {
            wallet.setStatus("LOCKED");
            wallet.setLockedAt(OffsetDateTime.now());
            walletRepository.save(wallet);
            eventPublisher.publish(WalletLockedEvent.create(
                    wallet.getId(),
                    accountHolderId,
                    operationType,
                    "PIN_MAX_ATTEMPTS_REACHED"
            ));
            throw new BusinessException("WALLET_LOCKED");
        }

        walletRepository.save(wallet);
        throw new BusinessException("WALLET_PIN_INVALID");
    }

    private WalletOtpChallenge issueOtpChallenge(Wallet wallet,
                                                 AccountHolder holder,
                                                 Operation operation,
                                                 String fingerprint,
                                                 WalletSecurityPolicy policy) {
        OperationType operationType = operation.getType();
        String fingerprintHash = hash(fingerprint);
        expirePendingChallenges(wallet.getId(), operationType, fingerprintHash);

        String otpCode = generateOtpCode(otpCodeLength);
        OffsetDateTime now = OffsetDateTime.now();
        String phoneNumber = resolveOtpPhone(wallet, holder);

        WalletOtpChallenge challenge = walletOtpChallengeRepository.save(WalletOtpChallenge.builder()
                .walletId(wallet.getId())
                .accountHolderId(holder.getId())
                .operationId(operation.getId())
                .operationType(operationType)
                .fingerprintHash(fingerprintHash)
                .otpHash(hash(otpCode))
                .phoneNumber(phoneNumber)
                .status(WalletOtpChallengeStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(policy.otpMaxAttempts())
                .sentAt(now)
                .expiresAt(now.plusSeconds(policy.otpTtlSeconds()))
                .build());

        phoneMessageGateway.sendSms(phoneNumber, buildOtpMessage(otpCode, policy.otpTtlSeconds()));
        eventPublisher.publish(WalletOtpSentEvent.create(
                wallet.getId(),
                holder.getId(),
                operation.getId(),
                challenge.getId(),
                operationType,
                challenge.getExpiresAt()
        ));

        log.info("wallet_otp_sent challengeId={} walletId={} type={} expiresAt={}",
                challenge.getId(), wallet.getId(), operationType, challenge.getExpiresAt());

        return challenge;
    }

    private void validateOtp(Wallet wallet,
                             UUID accountHolderId,
                             Operation operation,
                             UUID otpChallengeId,
                             String otpCode,
                             String fingerprint) {
        OperationType operationType = operation.getType();
        WalletOtpChallenge challenge = walletOtpChallengeRepository.findByIdAndWalletId(otpChallengeId, wallet.getId())
                .orElseThrow(() -> new BusinessException("WALLET_OTP_INVALID"));

        if (!challenge.getOperationId().equals(operation.getId())
                || !challenge.getAccountHolderId().equals(accountHolderId)
                || challenge.getOperationType() != operationType
                || !challenge.getFingerprintHash().equals(hash(fingerprint))) {
            throw new BusinessException("WALLET_OTP_INVALID");
        }

        if (challenge.getStatus() != WalletOtpChallengeStatus.PENDING) {
            throw new BusinessException("WALLET_OTP_INVALID");
        }

        if (challenge.getExpiresAt().isBefore(OffsetDateTime.now())) {
            challenge.setStatus(WalletOtpChallengeStatus.EXPIRED);
            walletOtpChallengeRepository.save(challenge);
            eventPublisher.publish(WalletOtpFailedEvent.create(
                    wallet.getId(),
                    accountHolderId,
                    null,
                    challenge.getId(),
                    operationType,
                    "OTP_EXPIRED",
                    0
            ));
            throw new BusinessException("WALLET_OTP_EXPIRED");
        }

        int attempts = challenge.getAttemptCount() + 1;
        challenge.setAttemptCount(attempts);
        if (!challenge.getOtpHash().equals(hash(otpCode.trim()))) {
            int remainingAttempts = Math.max(challenge.getMaxAttempts() - attempts, 0);
            if (attempts >= challenge.getMaxAttempts()) {
                challenge.setStatus(WalletOtpChallengeStatus.FAILED);
            }
            walletOtpChallengeRepository.save(challenge);
            eventPublisher.publish(WalletOtpFailedEvent.create(
                    wallet.getId(),
                    accountHolderId,
                    null,
                    challenge.getId(),
                    operationType,
                    "OTP_INVALID",
                    remainingAttempts
            ));
            throw new BusinessException(attempts >= challenge.getMaxAttempts()
                    ? "WALLET_OTP_TOO_MANY_ATTEMPTS"
                    : "WALLET_OTP_INVALID");
        }

        challenge.setStatus(WalletOtpChallengeStatus.VERIFIED);
        challenge.setUsedAt(OffsetDateTime.now());
        walletOtpChallengeRepository.save(challenge);
    }

    private void expirePendingChallenges(UUID walletId,
                                         OperationType operationType,
                                         String fingerprintHash) {
        List<WalletOtpChallenge> pendingChallenges = walletOtpChallengeRepository
                .findByWalletIdAndOperationTypeAndFingerprintHashAndStatus(
                        walletId,
                        operationType,
                        fingerprintHash,
                        WalletOtpChallengeStatus.PENDING
                );
        if (pendingChallenges.isEmpty()) {
            return;
        }
        pendingChallenges.forEach(challenge -> challenge.setStatus(WalletOtpChallengeStatus.EXPIRED));
        walletOtpChallengeRepository.saveAll(pendingChallenges);
    }

    private boolean requiresOtp(WalletSecurityPolicy policy,
                                OperationType operationType,
                                java.math.BigDecimal amount) {
        if (operationType == OperationType.WALLET_WITHDRAWAL) {
            return true;
        }
        return amount.compareTo(policy.otpThreshold()) >= 0;
    }

    private WalletSecurityPolicy resolvePolicy(AccountHolder holder) {
        String countryCode = holder.getServiceCountryCode() != null && !holder.getServiceCountryCode().isBlank()
                ? holder.getServiceCountryCode().trim().toUpperCase(Locale.ROOT)
                : holder.getCountryCode() != null && !holder.getCountryCode().isBlank()
                ? holder.getCountryCode().trim().toUpperCase(Locale.ROOT)
                : "INT";
        CountryConfigResolver.ResolvedConfig resolvedConfig =
                countryConfigResolver.resolveActiveConfig(countryCode, CountryConfigType.GENERAL);
        ObjectNode payload = countryConfigResolver.payloadAsObjectNode(resolvedConfig);
        JsonNode walletSecurity = payload.get("walletSecurity");

        return new WalletSecurityPolicy(
                readBoolean(walletSecurity, payload, "pinRequired", "walletPinRequired", defaultPinRequired),
                readDecimal(walletSecurity, payload, "otpThreshold", "walletOtpThreshold", defaultOtpThreshold),
                readInt(walletSecurity, payload, "pinMaxAttempts", "walletPinMaxAttempts", defaultPinMaxAttempts),
                readInt(walletSecurity, payload, "otpMaxAttempts", "walletOtpMaxAttempts", defaultOtpMaxAttempts),
                readLong(walletSecurity, payload, "otpTtlSeconds", "walletOtpTtlSeconds", defaultOtpTtlSeconds)
        );
    }

    private AccountHolder resolveAccountHolder(UUID accountHolderId) {
        return accountHolderRepository.findById(accountHolderId)
                .orElseThrow(() -> new BusinessException("Account holder not found: " + accountHolderId));
    }

    private String resolveOtpPhone(Wallet wallet, AccountHolder holder) {
        String phone = wallet.getMobileMoneyNumber();
        if (phone == null || phone.isBlank()) {
            phone = holder.getPhoneNumber();
        }
        if (phone == null || phone.isBlank()) {
            throw new BusinessException("WALLET_OTP_PHONE_REQUIRED");
        }
        return phone.trim();
    }

    private String buildOtpMessage(String otpCode, long ttlSeconds) {
        long ttlMinutes = Math.max(1, Math.round(Math.ceil(ttlSeconds / 60.0)));
        return "Votre code OTP CC App est: " + otpCode + ". Expire dans " + ttlMinutes + " minute(s).";
    }

    private String generateOtpCode(int length) {
        if (length < 4 || length > 8) {
            throw new BusinessException("WALLET_OTP_CODE_LENGTH_INVALID");
        }
        int min = (int) Math.pow(10, length - 1);
        int maxExclusive = (int) Math.pow(10, length);
        return Integer.toString(ThreadLocalRandom.current().nextInt(min, maxExclusive));
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private boolean readBoolean(JsonNode nestedNode,
                                ObjectNode root,
                                String nestedField,
                                String flatField,
                                boolean defaultValue) {
        JsonNode value = nestedNode != null && nestedNode.isObject() ? nestedNode.get(nestedField) : null;
        if (value == null || value.isNull()) {
            value = root.get(flatField);
        }
        return value != null && value.isBoolean() ? value.asBoolean() : defaultValue;
    }

    private java.math.BigDecimal readDecimal(JsonNode nestedNode,
                                             ObjectNode root,
                                             String nestedField,
                                             String flatField,
                                             java.math.BigDecimal defaultValue) {
        JsonNode value = nestedNode != null && nestedNode.isObject() ? nestedNode.get(nestedField) : null;
        if (value == null || value.isNull()) {
            value = root.get(flatField);
        }
        return value != null && value.isNumber() ? value.decimalValue() : defaultValue;
    }

    private int readInt(JsonNode nestedNode,
                        ObjectNode root,
                        String nestedField,
                        String flatField,
                        int defaultValue) {
        JsonNode value = nestedNode != null && nestedNode.isObject() ? nestedNode.get(nestedField) : null;
        if (value == null || value.isNull()) {
            value = root.get(flatField);
        }
        return value != null && value.isInt() ? value.asInt() : defaultValue;
    }

    private long readLong(JsonNode nestedNode,
                          ObjectNode root,
                          String nestedField,
                          String flatField,
                          long defaultValue) {
        JsonNode value = nestedNode != null && nestedNode.isObject() ? nestedNode.get(nestedField) : null;
        if (value == null || value.isNull()) {
            value = root.get(flatField);
        }
        return value != null && value.isNumber() ? value.asLong() : defaultValue;
    }

    public record WalletAuthorizationResult(
            boolean approved,
            boolean otpRequired,
            UUID otpChallengeId,
            OffsetDateTime otpExpiresAt
    ) {
        public static WalletAuthorizationResult approvedResult() {
            return new WalletAuthorizationResult(true, false, null, null);
        }

        public static WalletAuthorizationResult challengeRequired(UUID otpChallengeId, OffsetDateTime otpExpiresAt) {
            return new WalletAuthorizationResult(false, true, otpChallengeId, otpExpiresAt);
        }
    }

    private record WalletSecurityPolicy(
            boolean pinRequired,
            java.math.BigDecimal otpThreshold,
            int pinMaxAttempts,
            int otpMaxAttempts,
            long otpTtlSeconds
    ) {
    }
}
