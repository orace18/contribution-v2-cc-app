package com.ccapp.kyc.service;

import java.time.LocalDate;

/**
 * Server-side OCR abstraction for identity document analysis.
 *
 * <p>MVP uses {@link StubOcrService}. Replace with a real provider (AWS Textract,
 * Google Vision, Azure Form Recognizer, Tesseract) before going to production.
 *
 * <p>Implementations must never trust client-provided values. All data must be
 * extracted server-side from raw image bytes.
 */
public interface OcrService {

    OcrResult extract(byte[] imageBytes, String contentType);

    record OcrResult(
            boolean readable,
            int confidence,
            String fullName,
            String documentNumber,
            LocalDate dateOfBirth,
            LocalDate expiryDate,
            String nationality,
            byte[] portraitBytes
    ) {
    }
}
