package com.ccapp.kyc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * MVP stub for {@link OcrService}. Simulates a readable document with moderate confidence.
 *
 * <p>REPLACE with a real OCR provider before production.
 */
@Service
public class StubOcrService implements OcrService {

    private static final Logger log = LoggerFactory.getLogger(StubOcrService.class);

    @Override
    public OcrResult extract(byte[] imageBytes, String contentType) {
        log.info("ocr stub extract contentType={} bytes={}", contentType, imageBytes == null ? 0 : imageBytes.length);

        if (imageBytes == null || imageBytes.length == 0) {
            log.warn("ocr stub received empty image — marking as unreadable");
            return new OcrResult(false, 0, null, null, null, null, null, null);
        }

        if (!isSupportedContentType(contentType)) {
            log.warn("ocr stub unsupported contentType={} — marking as unreadable", contentType);
            return new OcrResult(false, 10, null, null, null, null, null, null);
        }

        int confidence = 80;
        LocalDate stubExpiryDate = LocalDate.now().plusYears(3);

        log.info("ocr stub result readable=true confidence={} expiryDate={}", confidence, stubExpiryDate);

        return new OcrResult(true, confidence, null, null, null, stubExpiryDate, null, null);
    }

    private boolean isSupportedContentType(String contentType) {
        if (contentType == null) return false;
        String normalized = contentType.toLowerCase();
        return normalized.startsWith("image/") || normalized.equals("application/pdf");
    }
}
