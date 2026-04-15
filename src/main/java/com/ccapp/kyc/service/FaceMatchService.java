package com.ccapp.kyc.service;

/**
 * Server-side face comparison abstraction.
 *
 * <p>MVP uses {@link StubFaceMatchService}. Replace with a real provider
 * (AWS Rekognition, Azure Face API, InsightFace) before production.
 *
 * <p>Implementations must operate only on raw bytes passed by the server.
 * Client-provided scores, hashes, or feature vectors must never be trusted.
 */
public interface FaceMatchService {

    FaceMatchResult compare(byte[] face1Bytes, byte[] face2Bytes);

    record FaceMatchResult(
            boolean faceDetectedInFirst,
            boolean faceDetectedInSecond,
            boolean matched,
            int score
    ) {
    }
}
