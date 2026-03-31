package com.ccapp.identity.verification.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.security.ClientRequestMetadata;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.service.IdentitySecurityService;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationChannel;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationStatus;
import com.ccapp.identity.verification.dto.ConfirmPhoneVerificationRequest;
import com.ccapp.identity.verification.dto.PhoneVerificationResponse;
import com.ccapp.identity.verification.dto.StartPhoneVerificationRequest;
import com.ccapp.notifications.dispatcher.PhoneMessageDispatchResult;
import com.ccapp.notifications.dispatcher.PhoneMessageGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({PhoneVerificationService.class, UnifiedOtpService.class})
class PhoneVerificationServiceIntegrationTest {

    private static final Pattern OTP_PATTERN = Pattern.compile("(\\d{4,8})");

    @Autowired
    private PhoneVerificationService phoneVerificationService;
    @Autowired
    private AccountHolderRepository accountHolderRepository;

    @MockBean
    private PhoneMessageGateway phoneMessageGateway;
    @MockBean
    private DomainEventPublisher domainEventPublisher;
    @MockBean
    private PhoneVerificationTrustService phoneVerificationTrustService;
    @MockBean
    private IdentitySecurityService identitySecurityService;

    @BeforeEach
    void setUp() {
        when(phoneMessageGateway.sendSms(anyString(), anyString()))
                .thenReturn(new PhoneMessageDispatchResult(true, "sms:accepted"));
        when(phoneMessageGateway.sendWhatsApp(anyString(), anyString()))
                .thenReturn(new PhoneMessageDispatchResult(true, "whatsapp:accepted"));
        when(identitySecurityService.currentRequestMetadata()).thenReturn(new ClientRequestMetadata("203.0.113.30", "BJ"));
    }

    @Test
    void startAndConfirm_shouldVerifyPhoneSuccessfully() {
        UUID requesterId = createAccountHolder();

        PhoneVerificationResponse started = phoneVerificationService.startVerification(
                requesterId,
                new StartPhoneVerificationRequest("+22996007373", PhoneVerificationChannel.SMS)
        );

        assertNotNull(started.id());
        assertEquals(PhoneVerificationStatus.PENDING, started.status());

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(phoneMessageGateway).sendSms(anyString(), messageCaptor.capture());
        String otpCode = extractOtp(messageCaptor.getValue());

        PhoneVerificationResponse confirmed = phoneVerificationService.confirmVerification(
                requesterId,
                started.id(),
                new ConfirmPhoneVerificationRequest(otpCode)
        );

        assertEquals(PhoneVerificationStatus.VERIFIED, confirmed.status());
        assertNotNull(confirmed.verifiedAt());
    }

    @Test
    void resend_shouldFailWhenCooldownNotReached() {
        UUID requesterId = createAccountHolder();
        PhoneVerificationResponse started = phoneVerificationService.startVerification(
                requesterId,
                new StartPhoneVerificationRequest("+22996007373", PhoneVerificationChannel.WHATSAPP)
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> phoneVerificationService.resendVerification(requesterId, started.id())
        );
        assertEquals("PHONE_VERIFICATION_RESEND_TOO_EARLY", exception.getMessage());
    }

    @Test
    void confirm_shouldFailWhenCodeInvalid() {
        UUID requesterId = createAccountHolder();
        PhoneVerificationResponse started = phoneVerificationService.startVerification(
                requesterId,
                new StartPhoneVerificationRequest("+22996007373", PhoneVerificationChannel.SMS)
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> phoneVerificationService.confirmVerification(
                        requesterId,
                        started.id(),
                        new ConfirmPhoneVerificationRequest("000000")
                )
        );
        assertEquals("PHONE_VERIFICATION_CODE_INVALID", exception.getMessage());
    }

    private UUID createAccountHolder() {
        return accountHolderRepository.save(AccountHolder.builder()
                .id(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("229" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.STANDARD)
                .build()).getId();
    }

    private String extractOtp(String message) {
        Matcher matcher = OTP_PATTERN.matcher(message);
        if (!matcher.find()) {
            throw new IllegalStateException("OTP not found in message: " + message);
        }
        return matcher.group(1);
    }
}
