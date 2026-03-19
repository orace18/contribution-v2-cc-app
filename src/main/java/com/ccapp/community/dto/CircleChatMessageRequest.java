package com.ccapp.community.dto;

import com.ccapp.community.domain.enums.CircleChatMessageType;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Requête d'envoi d'un message dans le chat de cercle.
 *
 * Flux media (image / vidéo / audio) :
 *   1. Appeler POST /api/media/upload-url (type=CIRCLE_CHAT) → obtenir uploadUrl + mediaId
 *   2. Uploader le fichier directement sur MinIO via uploadUrl
 *   3. Confirmer via POST /api/media/confirm → mediaId validé
 *   4. Envoyer ce message avec messageType=IMAGE|VIDEO|AUDIO et mediaId
 *
 * Le champ content est optionnel pour les messages media (légende).
 */
public record CircleChatMessageRequest(
        CircleChatMessageType messageType,

        @Size(max = 2000, message = "content max size is 2000")
        String content,

        /** UUID du MediaFile confirmé dans MinIO (obligatoire pour IMAGE/VIDEO/AUDIO) */
        UUID mediaId,

        /** Durée en secondes pour audio/vidéo — métadonnée fournie par le client */
        Integer mediaDurationSecs,

        @Size(max = 128, message = "clientMessageId max size is 128")
        String clientMessageId
) {
}
