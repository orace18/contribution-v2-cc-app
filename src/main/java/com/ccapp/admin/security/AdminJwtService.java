package com.ccapp.admin.security;

import com.ccapp.admin.domain.entity.AdminUser;
import com.ccapp.common.exception.UnauthorizedException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminJwtService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final String jwtSecret;
    private final long accessTtlMinutes;
    private final long challengeTtlMinutes;

    public AdminJwtService(ObjectMapper objectMapper,
                           @Value("${admin.jwt.secret:change-me-admin-jwt-secret}") String jwtSecret,
                           @Value("${admin.jwt.access-token-ttl-minutes:480}") long accessTtlMinutes,
                           @Value("${admin.jwt.challenge-token-ttl-minutes:10}") long challengeTtlMinutes) {
        this.objectMapper = objectMapper;
        this.jwtSecret = jwtSecret;
        this.accessTtlMinutes = accessTtlMinutes;
        this.challengeTtlMinutes = challengeTtlMinutes;
    }

    public String generateAccessToken(AdminUser adminUser) {
        return sign(adminUser, AdminTokenType.ACCESS, accessTtlMinutes);
    }

    public String generateChallengeToken(AdminUser adminUser) {
        return sign(adminUser, AdminTokenType.CHALLENGE, challengeTtlMinutes);
    }

    public AdminTokenClaims verify(String token, AdminTokenType expectedType) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new UnauthorizedException("Invalid admin token format");
            }

            String signingInput = parts[0] + "." + parts[1];
            byte[] expectedSignature = hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8), jwtSecret.getBytes(StandardCharsets.UTF_8));
            byte[] providedSignature = Base64.getUrlDecoder().decode(parts[2]);
            if (!java.security.MessageDigest.isEqual(expectedSignature, providedSignature)) {
                throw new UnauthorizedException("Invalid admin token signature");
            }

            Map<String, Object> payload = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), MAP_TYPE);
            String type = payload.get("typ") == null ? null : String.valueOf(payload.get("typ"));
            if (type == null || !expectedType.name().equals(type)) {
                throw new UnauthorizedException("Unexpected admin token type");
            }

            long exp = number(payload.get("exp"));
            long now = Instant.now().getEpochSecond();
            if (exp < now) {
                throw new UnauthorizedException("Admin token expired");
            }

            UUID adminUserId = UUID.fromString(String.valueOf(payload.get("sub")));
            String email = payload.get("email") == null ? null : String.valueOf(payload.get("email"));
            String role = payload.get("role") == null ? null : String.valueOf(payload.get("role"));
            UUID countryId = payload.get("countryId") == null ? null : UUID.fromString(String.valueOf(payload.get("countryId")));

            return new AdminTokenClaims(adminUserId, email, role, countryId, exp, type);
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnauthorizedException("Invalid admin token");
        }
    }

    private String sign(AdminUser adminUser, AdminTokenType tokenType, long ttlMinutes) {
        try {
            long now = Instant.now().getEpochSecond();
            long exp = Instant.now().plusSeconds(ttlMinutes * 60).getEpochSecond();

            Map<String, Object> header = Map.of(
                    "alg", "HS256",
                    "typ", "JWT"
            );

            Map<String, Object> payload = new HashMap<>();
            payload.put("sub", adminUser.getId().toString());
            payload.put("email", adminUser.getEmail());
            payload.put("role", adminUser.getRole().name());
            payload.put("typ", tokenType.name());
            payload.put("iat", now);
            payload.put("exp", exp);
            if (adminUser.getCountryId() != null) {
                payload.put("countryId", adminUser.getCountryId().toString());
            }

            String headerEncoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(header));
            String payloadEncoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(payload));

            String signingInput = headerEncoded + "." + payloadEncoded;
            byte[] signature = hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8), jwtSecret.getBytes(StandardCharsets.UTF_8));
            String signatureEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
            return signingInput + "." + signatureEncoded;
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot generate admin token", ex);
        }
    }

    private static byte[] hmacSha256(byte[] content, byte[] secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(content);
    }

    private static long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    public record AdminTokenClaims(
            UUID adminUserId,
            String email,
            String role,
            UUID countryId,
            long expiresAtEpoch,
            String tokenType
    ) {
    }
}
