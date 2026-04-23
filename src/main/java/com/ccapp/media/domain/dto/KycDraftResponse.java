package com.ccapp.media.domain.dto;

import com.ccapp.identity.domain.enums.KycLevel;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.media.domain.enums.KycDocumentType;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Snapshot of the current KYC draft for an account holder.
 *
 * <p>All identity fields reflect what was last stored via
 * {@code POST /api/mobile/kyc/declare-identity}.
 * {@code uploadedMediaIds} maps each document type to the media file UUID
 * that was confirmed during the upload step.
 * {@code identityDeclared} is {@code true} when all required identity fields
 * (birthDate, nationality, declaredDocumentType, documentNumber) are present,
 * i.e. the user has completed step 1 of the KYC flow.
 */
public record KycDraftResponse(
        UUID accountHolderId,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String nationality,
        String declaredDocumentType,
        String documentNumber,
        Map<KycDocumentType, UUID> uploadedMediaIds,
        KycStatus kycStatus,
        KycLevel kycLevel,
        boolean identityDeclared,
        /** CPaaS KYC case identifier — present once the dossier has been submitted to CPaaS.
         *  The mobile client must pass this as {@code ?caseId=} on GET /kyc/status to receive
         *  the enriched CPaaS verdict (productStatus, reasonCode, nextAction, …). */
        String caseId
) {
}
