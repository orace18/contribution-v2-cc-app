package com.ccapp.common.security;

import com.ccapp.identity.dto.RefreshTokenRequest;
import com.ccapp.mobile.auth.dto.MobileSocialLoginRequest;
import com.ccapp.mobile.payments.dto.MobilePaymentInitiateRequest;
import com.ccapp.identity.domain.enums.Platform;
import com.ccapp.wallet.dto.WalletPayRequest;
import com.ccapp.wallet.dto.WalletPinSetupRequest;
import com.ccapp.wallet.dto.WalletReserveRequest;
import com.ccapp.wallet.dto.WalletWithdrawRequest;
import com.ccapp.wallet.service.WalletDebitFacade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataRedactorTest {

    @Test
    void redactMessage_masksBearerTokensPinsAndRefreshTokens() {
        String redacted = SensitiveDataRedactor.redactMessage(
                "Authorization=Bearer secret-token pin=1234 refreshToken=refresh-secret"
        );

        assertThat(redacted)
                .isEqualTo("Authorization=Bearer [REDACTED] pin=[REDACTED] refreshToken=[REDACTED]");
    }

    @Test
    void sensitiveRequestToStrings_doNotExposeSecrets() {
        String mobilePayment = new MobilePaymentInitiateRequest(
                "idem-1",
                "BJ",
                "CC_WALLET",
                "22901020304",
                "wallet",
                "MEMBERSHIP",
                UUID.randomUUID(),
                "1234"
        ).toString();
        String refreshToken = new RefreshTokenRequest("refresh-secret").toString();
        String socialLogin = new MobileSocialLoginRequest(
                "device-1",
                Platform.ANDROID,
                "push-token-secret",
                "id-token-secret"
        ).toString();
        String walletPay = new WalletPayRequest(
                "22901020304",
                BigDecimal.valueOf(1200),
                "XOF",
                "wallet payment",
                "idem-2",
                "1234",
                UUID.randomUUID(),
                "654321"
        ).toString();
        String walletWithdraw = new WalletWithdrawRequest(
                BigDecimal.valueOf(500),
                "XOF",
                "idem-3",
                "1234",
                UUID.randomUUID(),
                "654321"
        ).toString();
        String walletPinSetup = new WalletPinSetupRequest("1111", "2222", "2222").toString();
        String walletReserve = new WalletReserveRequest(
                BigDecimal.TEN,
                "XOF",
                "reserve",
                OffsetDateTime.now(),
                "1234"
        ).toString();

        String secureDebitRequest = new WalletDebitFacade.SecureDebitRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                BigDecimal.valueOf(2500),
                "XOF",
                "idem-4",
                "secure debit",
                "fingerprint-secret",
                "471100",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "22901020304",
                "1234",
                UUID.randomUUID(),
                "654321",
                false,
                false,
                "debit",
                "credit",
                null,
                null,
                WalletDebitFacade.SecureDebitPostingMode.NONE,
                "BJ"
        ).toString();

        assertThat(mobilePayment).contains("pin=[REDACTED]").doesNotContain("1234");
        assertThat(refreshToken).contains("refreshToken=[REDACTED]").doesNotContain("refresh-secret");
        assertThat(socialLogin)
                .contains("pushToken=[REDACTED]")
                .contains("idToken=[REDACTED]")
                .doesNotContain("push-token-secret")
                .doesNotContain("id-token-secret");
        assertThat(walletPay).contains("pin=[REDACTED]").contains("otpCode=[REDACTED]").doesNotContain("654321");
        assertThat(walletWithdraw).contains("pin=[REDACTED]").contains("otpCode=[REDACTED]").doesNotContain("1234");
        assertThat(walletPinSetup).contains("currentPin=[REDACTED]").contains("newPin=[REDACTED]");
        assertThat(walletReserve).contains("pin=[REDACTED]").doesNotContain("1234");
        assertThat(secureDebitRequest)
                .contains("fingerprint=[REDACTED]")
                .contains("pin=[REDACTED]")
                .contains("otpCode=[REDACTED]")
                .doesNotContain("fingerprint-secret")
                .doesNotContain("654321");
    }
}
