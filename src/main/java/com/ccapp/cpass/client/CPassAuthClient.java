package com.ccapp.cpass.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Component
@ConditionalOnProperty(name = "cpass.enabled", havingValue = "true")
@Slf4j
public class CPassAuthClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;
    private final ReentrantLock lock = new ReentrantLock();

    public CPassAuthClient(
            RestClient.Builder restClientBuilder,
            @Value("${cpass.base-url}") String baseUrl,
            @Value("${cpass.client-id}") String clientId,
            @Value("${cpass.client-secret}") String clientSecret
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public RestClient restClient() {
        return restClient;
    }

    public String getClientId() {
        return clientId;
    }

    public String getBearerToken() {
        if (isTokenValid()) {
            return cachedToken;
        }
        lock.lock();
        try {
            if (isTokenValid()) {
                return cachedToken;
            }
            return fetchNewToken();
        } finally {
            lock.unlock();
        }
    }

    private boolean isTokenValid() {
        // keep a 60-second buffer before actual expiry
        return cachedToken != null && Instant.now().plusSeconds(60).isBefore(tokenExpiresAt);
    }

    private String fetchNewToken() {
        log.info("cpass: refreshing access token");
        try {
            Map<String, String> body = Map.of(
                    "client_id", clientId,
                    "client_secret", clientSecret
            );
            CPassTokenResponse response = restClient.post()
                    .uri("/auth/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(CPassTokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new CPassException("CPASS_AUTH_EMPTY_RESPONSE");
            }
            cachedToken = response.accessToken();
            tokenExpiresAt = Instant.now().plusSeconds(response.expiresIn());
            log.info("cpass: access token refreshed, expires_in={}", response.expiresIn());
            return cachedToken;
        } catch (RestClientResponseException ex) {
            log.error("cpass: token fetch failed status={} body={}",
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new CPassException("CPASS_AUTH_FAILED");
        } catch (CPassException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("cpass: token fetch error reason={}", ex.getMessage());
            throw new CPassException("CPASS_AUTH_FAILED");
        }
    }
}
