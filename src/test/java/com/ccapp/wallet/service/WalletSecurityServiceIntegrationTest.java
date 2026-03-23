package com.ccapp.wallet.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.notifications.dispatcher.PhoneMessageDispatchResult;
import com.ccapp.notifications.dispatcher.PhoneMessageGateway;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.wallet.domain.entity.WalletOtpChallenge;
import com.ccapp.wallet.domain.enums.WalletOtpChallengeStatus;
import com.ccapp.wallet.domain.enums.WalletType;
import com.ccapp.wallet.dto.WalletPinSetupRequest;
import com.ccapp.wallet.repository.WalletOtpChallengeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({WalletSecurityService.class, WalletSecurityServiceIntegrationTest.TestConfig.class})
class WalletSecurityServiceIntegrationTest {

    private static final Pattern OTP_PATTERN = Pattern.compile("(\\d{4,8})");

    @Autowired
    private WalletSecurityService walletSecurityService;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private WalletOtpChallengeRepository walletOtpChallengeRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private CountryConfigResolver countryConfigResolver;
    @MockBean
    private PhoneMessageGateway phoneMessageGateway;
    @MockBean
    private DomainEventPublisher domainEventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(phoneMessageGateway.sendSms(anyString(), anyString()))
                .thenReturn(new PhoneMessageDispatchResult(true, "sms:accepted"));
        stubPolicy(true, "5000", 3, 3, 120);
    }

    @Test
    void wrongPinShouldReject() {
        UUID holderId = createAccountHolder();
        Wallet wallet = createWallet(holderId, "1234", 0);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                walletSecurityService.authorizeDebit(
                        wallet,
                        holderId,
                        walletOperation(holderId, OperationType.WALLET_PAYMENT, "1000"),
                        "9999",
                        null,
                        null,
                        "wallet-payment|1000|XOF|22961000000"
                ));

        assertEquals("WALLET_PIN_INVALID", exception.getMessage());
        Wallet reloaded = walletRepository.findById(wallet.getId()).orElseThrow();
        assertEquals(1, reloaded.getPinFailedAttempts());
        assertEquals("ACTIVE", reloaded.getStatus());
    }

    @Test
    void multipleFailuresShouldLockWallet() {
        UUID holderId = createAccountHolder();
        Wallet wallet = createWallet(holderId, "1234", 2);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                walletSecurityService.authorizeDebit(
                        wallet,
                        holderId,
                        walletOperation(holderId, OperationType.WALLET_PAYMENT, "1000"),
                        "9999",
                        null,
                        null,
                        "wallet-payment|1000|XOF|22961000000"
                ));

        assertEquals("WALLET_LOCKED", exception.getMessage());
        Wallet reloaded = walletRepository.findById(wallet.getId()).orElseThrow();
        assertEquals(3, reloaded.getPinFailedAttempts());
        assertEquals("LOCKED", reloaded.getStatus());
        assertNotNull(reloaded.getLockedAt());
    }

    @Test
    void expiredOtpShouldReject() {
        UUID holderId = createAccountHolder();
        Wallet wallet = createWallet(holderId, "1234", 0);
        Operation operation = walletOperation(holderId, OperationType.WALLET_WITHDRAWAL, "1000");

        WalletSecurityService.WalletAuthorizationResult challengeResult =
                walletSecurityService.authorizeDebit(
                        wallet,
                        holderId,
                        operation,
                        "1234",
                        null,
                        null,
                        "wallet-withdrawal|1000|XOF"
                );

        assertTrue(challengeResult.otpRequired());
        WalletOtpChallenge challenge = walletOtpChallengeRepository.findById(challengeResult.otpChallengeId()).orElseThrow();
        challenge.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        walletOtpChallengeRepository.save(challenge);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                walletSecurityService.authorizeDebit(
                        walletRepository.findById(wallet.getId()).orElseThrow(),
                        holderId,
                        operation,
                        "1234",
                        challenge.getId(),
                        "123456",
                        "wallet-withdrawal|1000|XOF"
                ));

        assertEquals("WALLET_OTP_EXPIRED", exception.getMessage());
        WalletOtpChallenge reloaded = walletOtpChallengeRepository.findById(challenge.getId()).orElseThrow();
        assertEquals(WalletOtpChallengeStatus.EXPIRED, reloaded.getStatus());
    }

    @Test
    void validFlowShouldSucceed() {
        UUID holderId = createAccountHolder();
        Wallet wallet = createWallet(holderId, "1234", 0);
        Operation operation = walletOperation(holderId, OperationType.WALLET_WITHDRAWAL, "1000");

        WalletSecurityService.WalletAuthorizationResult challengeResult =
                walletSecurityService.authorizeDebit(
                        wallet,
                        holderId,
                        operation,
                        "1234",
                        null,
                        null,
                        "wallet-withdrawal|1000|XOF"
                );

        assertTrue(challengeResult.otpRequired());

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(phoneMessageGateway).sendSms(anyString(), messageCaptor.capture());
        String otpCode = extractOtp(messageCaptor.getValue());

        WalletSecurityService.WalletAuthorizationResult result =
                walletSecurityService.authorizeDebit(
                        walletRepository.findById(wallet.getId()).orElseThrow(),
                        holderId,
                        operation,
                        "1234",
                        challengeResult.otpChallengeId(),
                        otpCode,
                        "wallet-withdrawal|1000|XOF"
                );

        assertTrue(result.approved());
        WalletOtpChallenge challenge = walletOtpChallengeRepository.findById(challengeResult.otpChallengeId()).orElseThrow();
        assertEquals(WalletOtpChallengeStatus.VERIFIED, challenge.getStatus());
        assertNotNull(challenge.getUsedAt());
    }

    @Test
    void configurePinShouldHashPin() {
        UUID holderId = createAccountHolder();
        Wallet wallet = createWalletWithoutPin(holderId);

        walletSecurityService.configurePin(holderId, new WalletPinSetupRequest(null, "1234", "1234"));

        Wallet reloaded = walletRepository.findById(wallet.getId()).orElseThrow();
        assertNotNull(reloaded.getPinHash());
        assertNotEquals("1234", reloaded.getPinHash());
        assertTrue(passwordEncoder.matches("1234", reloaded.getPinHash()));
    }

    private void stubPolicy(boolean pinRequired,
                            String otpThreshold,
                            int pinMaxAttempts,
                            int otpMaxAttempts,
                            int otpTtlSeconds) {
        CountryConfigResolver.ResolvedConfig resolvedConfig = new CountryConfigResolver.ResolvedConfig(
                "BJ",
                CountryConfigType.GENERAL,
                1,
                "{}",
                CountryConfigResolver.ConfigSource.COUNTRY
        );
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode walletSecurity = payload.putObject("walletSecurity");
        walletSecurity.put("pinRequired", pinRequired);
        walletSecurity.put("otpThreshold", new BigDecimal(otpThreshold));
        walletSecurity.put("pinMaxAttempts", pinMaxAttempts);
        walletSecurity.put("otpMaxAttempts", otpMaxAttempts);
        walletSecurity.put("otpTtlSeconds", otpTtlSeconds);

        when(countryConfigResolver.resolveActiveConfig("BJ", CountryConfigType.GENERAL)).thenReturn(resolvedConfig);
        when(countryConfigResolver.payloadAsObjectNode(resolvedConfig)).thenReturn(payload);
    }

    private UUID createAccountHolder() {
        return accountHolderRepository.save(AccountHolder.builder()
                .id(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("22961000000")
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .kycStatus(KycStatus.VERIFIED)
                .accountType(AccountType.STANDARD)
                .build()).getId();
    }

    private Wallet createWallet(UUID accountHolderId, String pin, int failedAttempts) {
        return walletRepository.save(Wallet.builder()
                .accountHolderId(accountHolderId)
                .provider("MTN")
                .mobileMoneyNumber("22961000000")
                .status("ACTIVE")
                .walletType(WalletType.USER)
                .pinHash(passwordEncoder.encode(pin))
                .pinFailedAttempts(failedAttempts)
                .build());
    }

    private Wallet createWalletWithoutPin(UUID accountHolderId) {
        return walletRepository.save(Wallet.builder()
                .accountHolderId(accountHolderId)
                .provider("MTN")
                .mobileMoneyNumber("22961000000")
                .status("ACTIVE")
                .walletType(WalletType.USER)
                .pinFailedAttempts(0)
                .build());
    }

    private Operation walletOperation(UUID accountHolderId, OperationType type, String amount) {
        return Operation.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .actorUserId(accountHolderId)
                .type(type)
                .status(OperationStatus.INITIATED)
                .amount(new BigDecimal(amount))
                .currency("XOF")
                .idempotencyKey(UUID.randomUUID().toString())
                .build();
    }

    private String extractOtp(String message) {
        Matcher matcher = OTP_PATTERN.matcher(message);
        if (!matcher.find()) {
            throw new IllegalStateException("OTP not found in message: " + message);
        }
        return matcher.group(1);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
