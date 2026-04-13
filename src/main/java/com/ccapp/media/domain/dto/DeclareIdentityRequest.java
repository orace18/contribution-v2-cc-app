package com.ccapp.media.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Civil identity declared by the user before uploading KYC documents.
 * Stored on account_holder so it can be compared against the uploaded documents.
 */
public record DeclareIdentityRequest(

        /**
         * Target account holder. When null the backend resolves the actor from the
         * authenticated JWT principal, which is the correct behaviour for own-account KYC.
         * Provide a value only for delegated (managed-account) flows.
         */
        @Nullable
        UUID accountHolderId,

        @NotBlank(message = "firstName is required")
        @Size(max = 120)
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 120)
        String lastName,

        /** Date of birth in ISO format (yyyy-MM-dd). */
        @NotNull(message = "birthDate is required")
        LocalDate birthDate,

        /** ISO 3166-1 alpha-2 country code of the document issuer (e.g. "BJ"). */
        @NotBlank(message = "nationality is required")
        @Size(max = 3)
        String nationality,

        /**
         * Type of identity document declared by the user.
         * Accepted values: CNI, PASSPORT, RESIDENCE_PERMIT, DRIVING_LICENSE.
         */
        @NotBlank(message = "documentType is required")
        @Size(max = 32)
        String documentType,

        @NotBlank(message = "documentNumber is required")
        @Size(max = 128)
        String documentNumber
) {
}
