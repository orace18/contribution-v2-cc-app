package com.ccapp.admin.security;

import com.ccapp.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

@Service
public class TotpService {

    private static final int DIGITS = 6;
    private static final long PERIOD_SECONDS = 30L;

    private final SecureRandom secureRandom = new SecureRandom();
    private final String issuer;

    public TotpService(@Value("${admin.totp.issuer:CC-APP Back Office}") String issuer) {
        this.issuer = issuer;
    }

    public String generateSecret() {
        byte[] bytes = new byte[20];
        secureRandom.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public String buildOtpAuthUri(String email, String secret) {
        String encodedIssuer = urlEncode(issuer);
        String encodedLabel = urlEncode(issuer + ":" + email);
        return "otpauth://totp/" + encodedLabel
                + "?secret=" + secret
                + "&issuer=" + encodedIssuer
                + "&algorithm=SHA1&digits=" + DIGITS
                + "&period=" + PERIOD_SECONDS;
    }

    public boolean verifyCode(String secret, String code) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        if (code == null || !code.matches("\\d{6}")) {
            return false;
        }

        int provided = Integer.parseInt(code);
        long currentWindow = Instant.now().getEpochSecond() / PERIOD_SECONDS;
        byte[] secretBytes = base32Decode(secret);

        for (long offset = -1; offset <= 1; offset++) {
            int expected = generateHotp(secretBytes, currentWindow + offset);
            if (expected == provided) {
                return true;
            }
        }
        return false;
    }

    public void assertValidCode(String secret, String code) {
        if (!verifyCode(secret, code)) {
            throw new BusinessException("Invalid 2FA code");
        }
    }

    private static int generateHotp(byte[] secret, long counter) {
        try {
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return binary % 1_000_000;
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot generate TOTP", ex);
        }
    }

    private static String base32Encode(byte[] data) {
        final char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        StringBuilder output = new StringBuilder((data.length * 8 + 4) / 5);

        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                output.append(alphabet[(buffer >> (bitsLeft - 5)) & 0x1F]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            output.append(alphabet[(buffer << (5 - bitsLeft)) & 0x1F]);
        }
        return output.toString();
    }

    private static byte[] base32Decode(String secret) {
        String normalized = secret.trim().replace("=", "").toUpperCase();
        byte[] bytes = new byte[(normalized.length() * 5) / 8];

        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;

        for (char c : normalized.toCharArray()) {
            int value = base32Value(c);
            if (value < 0) {
                throw new BusinessException("Invalid 2FA secret");
            }

            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bytes[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }

        if (index == bytes.length) {
            return bytes;
        }

        byte[] exact = new byte[index];
        System.arraycopy(bytes, 0, exact, 0, index);
        return exact;
    }

    private static int base32Value(char c) {
        if (c >= 'A' && c <= 'Z') {
            return c - 'A';
        }
        if (c >= '2' && c <= '7') {
            return c - '2' + 26;
        }
        return -1;
    }

    private static String urlEncode(String input) {
        return URLEncoder.encode(input, StandardCharsets.UTF_8);
    }
}
