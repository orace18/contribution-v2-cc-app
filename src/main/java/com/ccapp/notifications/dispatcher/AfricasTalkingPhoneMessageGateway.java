package com.ccapp.notifications.dispatcher;

import com.ccapp.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "messaging.africastalking.enabled", havingValue = "true")
@Slf4j
public class AfricasTalkingPhoneMessageGateway implements PhoneMessageGateway {

    private final RestClient restClient;
    private final String baseUrl;
    private final String username;
    private final String apiKey;
    private final String smsPath;
    private final String whatsappPath;
    private final String smsFrom;
    private final String whatsappFrom;
    private final boolean logRawToken;

    public AfricasTalkingPhoneMessageGateway(
            RestClient.Builder restClientBuilder,
            @Value("${messaging.africastalking.base-url:https://api.africastalking.com}") String baseUrl,
            @Value("${messaging.africastalking.username:sandbox}") String username,
            @Value("${messaging.africastalking.api-key:}") String apiKey,
            @Value("${messaging.africastalking.sms-path:/version1/messaging}") String smsPath,
            @Value("${messaging.africastalking.whatsapp-path:/version1/whatsapp/message}") String whatsappPath,
            @Value("${messaging.africastalking.sms-from:}") String smsFrom,
            @Value("${messaging.africastalking.whatsapp-from:}") String whatsappFrom,
            @Value("${messaging.africastalking.log-raw-token:false}") boolean logRawToken
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.baseUrl = baseUrl;
        this.username = username;
        this.apiKey = apiKey;
        this.smsPath = smsPath;
        this.whatsappPath = whatsappPath;
        this.smsFrom = smsFrom;
        this.whatsappFrom = whatsappFrom;
        this.logRawToken = logRawToken;
    }

    @Override
    public PhoneMessageDispatchResult sendSms(String toPhoneNumber, String message) {
        ensureCredentials();
        String body = buildSmsFormBody(toPhoneNumber, message);
        String endpoint = resolveEndpoint(smsPath);
        log.info(
                "Outbound API request provider=africastalking channel=sms method=POST endpoint={} recipient={} messageLength={}",
                endpoint,
                maskPhone(toPhoneNumber),
                messageLength(message)
        );
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(smsPath)
                    .header("apiKey", apiKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            log.info(
                    "Outbound API response provider=africastalking channel=sms method=POST endpoint={} recipient={} status={} providerResponseLength={}",
                    endpoint,
                    maskPhone(toPhoneNumber),
                    response.getStatusCode().value(),
                    responseLength(response.getBody())
            );
            return new PhoneMessageDispatchResult(true, trimResponse(response.getBody()));
        } catch (RestClientResponseException ex) {
            log.error(
                    "Outbound API error provider=africastalking channel=sms method=POST endpoint={} recipient={} status={} responseLength={} reason={}",
                    endpoint,
                    maskPhone(toPhoneNumber),
                    ex.getStatusCode().value(),
                    responseLength(ex.getResponseBodyAsString()),
                    ex.getMessage()
            );
            throw new BusinessException("PHONE_MESSAGE_DELIVERY_FAILED");
        } catch (RuntimeException ex) {
            log.error(
                    "Outbound API error provider=africastalking channel=sms method=POST endpoint={} recipient={} reason={}",
                    endpoint,
                    maskPhone(toPhoneNumber),
                    ex.getMessage()
            );
            throw new BusinessException("PHONE_MESSAGE_DELIVERY_FAILED");
        }
    }

    @Override
    public PhoneMessageDispatchResult sendWhatsApp(String toPhoneNumber, String message) {
        ensureCredentials();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", username);
        payload.put("to", toPhoneNumber);
        payload.put("message", message);
        if (hasText(whatsappFrom)) {
            payload.put("from", whatsappFrom);
        }
        String endpoint = resolveEndpoint(whatsappPath);
        log.info(
                "Outbound API request provider=africastalking channel=whatsapp method=POST endpoint={} recipient={} messageLength={}",
                endpoint,
                maskPhone(toPhoneNumber),
                messageLength(message)
        );

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(whatsappPath)
                    .header("apiKey", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);
            log.info(
                    "Outbound API response provider=africastalking channel=whatsapp method=POST endpoint={} recipient={} status={} providerResponseLength={}",
                    endpoint,
                    maskPhone(toPhoneNumber),
                    response.getStatusCode().value(),
                    responseLength(response.getBody())
            );
            return new PhoneMessageDispatchResult(true, trimResponse(response.getBody()));
        } catch (RestClientResponseException ex) {
            log.error(
                    "Outbound API error provider=africastalking channel=whatsapp method=POST endpoint={} recipient={} status={} responseLength={} reason={}",
                    endpoint,
                    maskPhone(toPhoneNumber),
                    ex.getStatusCode().value(),
                    responseLength(ex.getResponseBodyAsString()),
                    ex.getMessage()
            );
            throw new BusinessException("PHONE_MESSAGE_DELIVERY_FAILED");
        } catch (RuntimeException ex) {
            log.error(
                    "Outbound API error provider=africastalking channel=whatsapp method=POST endpoint={} recipient={} reason={}",
                    endpoint,
                    maskPhone(toPhoneNumber),
                    ex.getMessage()
            );
            throw new BusinessException("PHONE_MESSAGE_DELIVERY_FAILED");
        }
    }

    private String buildSmsFormBody(String toPhoneNumber, String message) {
        StringBuilder builder = new StringBuilder();
        builder.append("username=").append(urlEncode(username));
        builder.append("&to=").append(urlEncode(toPhoneNumber));
        builder.append("&message=").append(urlEncode(message));
        if (hasText(smsFrom)) {
            builder.append("&from=").append(urlEncode(smsFrom));
        }
        return builder.toString();
    }

    private void ensureCredentials() {
        if (!hasText(apiKey) || !hasText(username)) {
            throw new BusinessException("AFRICASTALKING_CONFIGURATION_INVALID");
        }
    }

    private String trimResponse(String response) {
        if (response == null) {
            return "";
        }
        String trimmed = response.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    private String resolveEndpoint(String path) {
        if (path == null || path.isBlank()) {
            return baseUrl;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBaseUrl + normalizedPath;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "***";
        }
        int keep = Math.min(4, phoneNumber.length());
        return "*".repeat(phoneNumber.length() - keep) + phoneNumber.substring(phoneNumber.length() - keep);
    }

    private int messageLength(String message) {
        return message == null ? 0 : message.length();
    }

    private int responseLength(String response) {
        return response == null ? 0 : response.length();
    }
}
