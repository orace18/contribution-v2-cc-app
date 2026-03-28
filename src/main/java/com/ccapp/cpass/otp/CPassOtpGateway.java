package com.ccapp.cpass.otp;

import com.ccapp.cpass.client.CPassAuthClient;
import com.ccapp.cpass.client.CPassException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "cpass.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CPassOtpGateway {

    private final CPassAuthClient authClient;

    public CPassOtpSendResponse sendOtp(String phoneNumber) {
        log.info("cpass: otp send to={}", mask(phoneNumber));
        try {
            CPassOtpSendResponse response = authClient.restClient().post()
                    .uri("/otp/send")
                    .header("Authorization", "Bearer " + authClient.getBearerToken())
                    .header("X-Client-ID", authClient.getClientId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("to", phoneNumber))
                    .retrieve()
                    .body(CPassOtpSendResponse.class);
            if (response == null || response.referenceId() == null) {
                throw new CPassException("CPASS_OTP_SEND_EMPTY_RESPONSE");
            }
            log.info("cpass: otp queued referenceId={} expiresAt={}", response.referenceId(), response.expiresAt());
            return response;
        } catch (RestClientResponseException ex) {
            log.error("cpass: otp send failed status={} body={}",
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new CPassException("CPASS_OTP_SEND_FAILED");
        } catch (CPassException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("cpass: otp send error reason={}", ex.getMessage());
            throw new CPassException("CPASS_OTP_SEND_FAILED");
        }
    }

    public CPassOtpVerifyResponse verifyOtp(String referenceId, String otpCode) {
        log.info("cpass: otp verify referenceId={}", referenceId);
        try {
            CPassOtpVerifyResponse response = authClient.restClient().post()
                    .uri("/otp/verify")
                    .header("Authorization", "Bearer " + authClient.getBearerToken())
                    .header("X-Client-ID", authClient.getClientId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("referenceId", referenceId, "otpCode", otpCode))
                    .retrieve()
                    .body(CPassOtpVerifyResponse.class);
            if (response == null) {
                throw new CPassException("CPASS_OTP_VERIFY_EMPTY_RESPONSE");
            }
            log.info("cpass: otp verify referenceId={} verified={} attemptsRemaining={}",
                    referenceId, response.verified(), response.attemptsRemaining());
            return response;
        } catch (RestClientResponseException ex) {
            log.error("cpass: otp verify failed status={} body={}",
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new CPassException("CPASS_OTP_VERIFY_FAILED");
        } catch (CPassException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("cpass: otp verify error reason={}", ex.getMessage());
            throw new CPassException("CPASS_OTP_VERIFY_FAILED");
        }
    }

    private String mask(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}
