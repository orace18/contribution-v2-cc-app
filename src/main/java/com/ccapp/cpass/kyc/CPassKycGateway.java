package com.ccapp.cpass.kyc;

import com.ccapp.cpass.client.CPassAuthClient;
import com.ccapp.cpass.client.CPassException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "cpass.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CPassKycGateway {

    private final CPassAuthClient authClient;

    /**
     * Creates a new KYC case or reuses an approved identity when level/validity rules allow it.
     */
    public CPassKycInitResponse checkOrInitKyc(CPassKycInitRequest request) {
        log.info("cpass: kyc check-or-init userId={} level={}", request.userId(), request.requestedLevel());
        try {
            CPassKycInitResponse response = authClient.restClient().post()
                    .uri("/kyc/check-or-init")
                    .header("Authorization", "Bearer " + authClient.getBearerToken())
                    .header("X-Client-ID", authClient.getClientId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(CPassKycInitResponse.class);
            if (response == null || response.kycCaseId() == null) {
                throw new CPassException("CPASS_KYC_INIT_EMPTY_RESPONSE");
            }
            log.info("cpass: kyc case created caseId={} status={} level={}",
                    response.kycCaseId(), response.status(), response.kycLevel());
            return response;
        } catch (RestClientResponseException ex) {
            log.error("cpass: kyc init failed status={} body={}",
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new CPassException("CPASS_KYC_INIT_FAILED");
        } catch (CPassException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("cpass: kyc init error reason={}", ex.getMessage());
            throw new CPassException("CPASS_KYC_INIT_FAILED");
        }
    }

    /**
     * Uploads a document for a KYC case (multipart/form-data).
     * The metadata map may contain: expiryDate, documentNumber.
     */
    public CPassKycDocumentResponse submitDocument(
            String caseId,
            String documentType,
            Resource fileResource,
            Map<String, String> metadata
    ) {
        log.info("cpass: kyc submit-document caseId={} documentType={}", caseId, documentType);
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("caseId", caseId);
            body.add("documentType", documentType);
            body.add("file", fileResource);
            if (metadata != null && !metadata.isEmpty()) {
                body.add("metadata", toJsonString(metadata));
            }

            CPassKycDocumentResponse response = authClient.restClient().post()
                    .uri("/kyc/submit-document")
                    .header("Authorization", "Bearer " + authClient.getBearerToken())
                    .header("X-Client-ID", authClient.getClientId())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(CPassKycDocumentResponse.class);
            if (response == null || response.documentId() == null) {
                throw new CPassException("CPASS_KYC_DOCUMENT_EMPTY_RESPONSE");
            }
            log.info("cpass: kyc document submitted documentId={} validityStatus={}",
                    response.documentId(), response.validityStatus());
            return response;
        } catch (RestClientResponseException ex) {
            log.error("cpass: kyc submit-document failed status={} body={}",
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new CPassException("CPASS_KYC_DOCUMENT_FAILED");
        } catch (CPassException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("cpass: kyc submit-document error reason={}", ex.getMessage());
            throw new CPassException("CPASS_KYC_DOCUMENT_FAILED");
        }
    }

    /**
     * Verifies the OTP contact step for a KYC case.
     */
    public CPassKycVerifyResponse verifyContact(CPassKycVerifyRequest request) {
        log.info("cpass: kyc verify-contact caseId={}", request.caseId());
        try {
            CPassKycVerifyResponse response = authClient.restClient().post()
                    .uri("/kyc/verify")
                    .header("Authorization", "Bearer " + authClient.getBearerToken())
                    .header("X-Client-ID", authClient.getClientId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(CPassKycVerifyResponse.class);
            if (response == null || response.id() == null) {
                throw new CPassException("CPASS_KYC_VERIFY_EMPTY_RESPONSE");
            }
            log.info("cpass: kyc contact verified caseId={} status={}", response.id(), response.status());
            return response;
        } catch (RestClientResponseException ex) {
            log.error("cpass: kyc verify-contact failed status={} body={}",
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new CPassException("CPASS_KYC_VERIFY_FAILED");
        } catch (CPassException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("cpass: kyc verify-contact error reason={}", ex.getMessage());
            throw new CPassException("CPASS_KYC_VERIFY_FAILED");
        }
    }

    private String toJsonString(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> sb.append("\"").append(k).append("\":\"").append(v).append("\","));
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Fetches the current KYC status and actionable details.
     */
    public CPassKycStatusResponse getKycStatus(String caseId) {
        log.info("cpass: kyc get-status caseId={}", caseId);
        try {
            return authClient.restClient().get()
                    .uri("/kyc/status?caseId=" + caseId)
                    .header("Authorization", "Bearer " + authClient.getBearerToken())
                    .header("X-Client-ID", authClient.getClientId())
                    .retrieve()
                    .body(CPassKycStatusResponse.class);
        } catch (RestClientResponseException ex) {
            log.error("cpass: kyc get-status failed status={} body={}",
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return null;
        } catch (Exception ex) {
            log.error("cpass: kyc get-status error reason={}", ex.getMessage());
            return null;
        }
    }
}
