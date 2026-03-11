# Documentation API - CC App Backend

Date de mise a jour: 2026-03-07

## Base URL
- Dev/Prod (profiles `dev`/`prod`): `/cc-app-backend`
- Exemples ci-dessous utilises sans host: `POST /cc-app-backend/identity/sync`

## Authentification
- `Bearer <token>` requis pour la plupart des endpoints.
- Meme en `dev`, le token doit etre un `Firebase ID token` valide (`Bearer <idToken>`).
- Endpoints internes (`/internal/**`): header `X-API-KEY`.
- Endpoints publics:
  - `GET /actuator/health`
  - `GET /actuator/info`
  - `GET /public/countries`
  - `POST /psp/jawupay/callback`

## Format de reponse (standard)

### Succes
```json
{
  "success": true,
  "message": "Operation reussie",
  "data": {},
  "timestamp": "2026-03-07T12:00:00Z"
}
```

### Echec
```json
{
  "success": false,
  "message": "Cause de l'erreur",
  "data": null,
  "timestamp": "2026-03-07T12:00:00Z"
}
```

## Codes d'erreur usuels
- `400 Bad Request`: validation, regle metier (`BusinessException`)
- `401 Unauthorized`: token/API key invalide, session invalide
- `403 Forbidden`: acces interdit
- `404 Not Found`: ressource absente
- `409 Conflict`: conflit fonctionnel (idempotence, doublon, etc.)
- `500 Internal Server Error`: erreur inattendue

## Endpoints

## Monitoring

### GET `/actuator/health`
- Auth: publique
- Succes `200`: etat de sante Spring Actuator
- Echec: `500` si application indisponible

### GET `/actuator/info`
- Auth: publique
- Succes `200`: infos applicatives exposees
- Echec: `500` si application indisponible

## Identity

### POST `/identity/sync`
- Auth: Bearer
- Body:
```json
{
  "deviceIdentifier": "device-001",
  "platform": "ANDROID"
}
```
- Succes `200` (`SyncResponse`):
```json
{
  "success": true,
  "message": "Identity synced",
  "data": {
    "userId": "uuid",
    "firebaseUid": "uid",
    "email": "uid@dev.local",
    "role": "USER",
    "accountStatus": "ACTIVE",
    "kycStatus": "NOT_STARTED",
    "sessionId": "uuid",
    "refreshToken": "token"
  },
  "timestamp": "2026-03-07T12:00:00Z"
}
```
- Echec:
  - `401`: token invalide/expire
  - `400`: compte non actif

### POST `/identity/onboarding`
- Auth: Bearer
- Body:
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "country": "CI",
  "phone": "+22501020304"
}
```
- Succes `200` (`MeResponse`)
- Regle metier:
  - `country` doit etre un pays actif (`countries.active=true`).
  - `account_holders.country_code` stocke le pays selectionne par l'utilisateur (immuable).
  - `account_holders.service_country_code` est derive:
    - si `countries.configured=true` pour le pays selectionne: service country = pays selectionne
    - sinon: service country = `INT` (fallback international)
- Echec:
  - `400`: validation payload
  - `401`: utilisateur non authentifie
  - `404`: user introuvable

### GET `/identity/onboarding`
- Auth: Bearer
- Succes `200` (`OnboardingStatusResponse`):
  - `onboardingStep` (`STEP_1|STEP_2|STEP_3|COMPLETED`)
  - `currentStep` (1..3)
  - `totalSteps` (=3)
  - `progressPercentage` (0..100)
  - `step1Completed`, `step2Completed`, `step3Completed`
  - `onboardingCompleted`, `onboardingCompletedAt`
  - `profile` (snapshot profil courant)
- Echec:
  - `401`: utilisateur non authentifie
  - `404`: user introuvable

### PATCH `/identity/onboarding/step-1`
- Auth: Bearer
- Body:
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "country": "CI",
  "phone": "+22501020304"
}
```
- Succes `200` (`OnboardingStatusResponse`)
- Echec:
  - `400`: validation payload ou pays immuable
  - `401`: utilisateur non authentifie
  - `404`: user introuvable

### PATCH `/identity/onboarding/step-2`
- Auth: Bearer
- Body:
```json
{
  "activitySectorCode": "COMMERCE",
  "businessExperience": "THREE_TO_FIVE_YEARS",
  "monthlyIncomeBracketCode": "BETWEEN_300K_600K",
  "projectDescription": "Description detaillee du projet et de l'activite."
}
```
- Succes `200` (`OnboardingStatusResponse`)
- Regles:
  - etape 1 doit etre complete
  - `activitySectorCode` doit exister dans `/public/activity-sectors`
  - `monthlyIncomeBracketCode` doit exister dans `/public/income-brackets`
- Echec:
  - `400`: validation payload / ordre des etapes / code catalogue invalide
  - `401`: utilisateur non authentifie
  - `404`: user introuvable

### PATCH `/identity/onboarding/step-3`
- Auth: Bearer
- Body:
```json
{
  "availableCapital": 15000.00,
  "solidarityGuaranteeAccepted": true,
  "termsAccepted": true
}
```
- Succes `200` (`OnboardingStatusResponse`)
- Regles:
  - etapes 1 et 2 doivent etre completes
  - les 2 consentements doivent etre `true`
- Echec:
  - `400`: validation payload / ordre des etapes / consentements obligatoires
  - `401`: utilisateur non authentifie
  - `404`: user introuvable

## Public

### GET `/public/countries`
- Auth: publique
- Retourne tous les pays avec `active=true`.
- Succes `200` (`List<CountryResponse>`)
- Echec:
  - `500`: erreur inattendue

### GET `/public/activity-sectors`
- Auth: publique
- Succes `200` (`List<OnboardingCatalogItemResponse>`)

### GET `/public/income-brackets`
- Auth: publique
- Succes `200` (`List<OnboardingCatalogItemResponse>`)

## Admin Countries

### GET `/api/admin/countries`
- Auth: Bearer admin (`/api/admin/**`)
- Retourne le catalogue central `countries` (actif/configure/fallback).
- Succes `200` (`List<CountryConfigResponse>`)

### POST `/api/admin/countries/{code}/activate`
- Auth: Bearer admin
- Active le pays cible (`active=true`).
- Succes `200` (`CountryConfigResponse`)

### POST `/api/admin/countries/{code}/configure`
- Auth: Bearer admin
- Configure le pays cible pour services locaux (`configured=true`, et `active=true`).
- Succes `200` (`CountryConfigResponse`)

## Media

### POST `/api/media/upload-url`
- Auth: Bearer
- Body:
```json
{
  "type": "PROFILE_PHOTO",
  "contentType": "image/jpeg",
  "size": 245678,
  "originalFileName": "avatar.jpg"
}
```
- Succes `200` (`CreateUploadUrlResponse`):
  - `mediaId`
  - `bucket`
  - `objectKey`
  - `uploadUrl` (pre-signed PUT)
  - `expiresAt`
- Echec:
  - `400`: validation/regle metier (taille/type)
  - `401`: non authentifie

### POST `/api/media/confirm`
- Auth: Bearer
- Body:
```json
{
  "mediaId": "uuid"
}
```
- Succes `200` (`MediaFileResponse`)
- Regles d'integration:
  - `PROFILE_PHOTO` -> met a jour `user_profiles.profile_photo_media_id`
  - `KYC_*` -> cree une entree `kyc_documents`
- Echec:
  - `400`: objet absent dans MinIO / statut invalide
  - `401`: non authentifie
  - `404`: media introuvable

### GET `/api/media/{id}`
- Auth: Bearer
- Succes `200` (`MediaFileResponse`)
- Echec:
  - `401`: non authentifie
  - `404`: media introuvable

### GET `/api/media/{id}/download-url`
- Auth: Bearer
- Succes `200` (`DownloadUrlResponse`)
- Echec:
  - `400`: media pas encore confirme
  - `401`: non authentifie
  - `404`: media introuvable

### GET `/identity/me`
- Auth: Bearer
- Succes `200` (`MeResponse`):
  - infos user
  - profile
  - devices
  - activeSessions
- Echec:
  - `401`: non authentifie
  - `404`: user introuvable

### POST `/identity/devices`
- Auth: Bearer
- Body:
```json
{
  "deviceIdentifier": "device-002",
  "platform": "IOS"
}
```
- Succes `200` (`UserDeviceDto`)
- Echec:
  - `400`: validation payload
  - `401`: non authentifie
  - `404`: user introuvable

### POST `/identity/devices/{deviceId}/block`
- Auth: Bearer
- Path param: `deviceId` (UUID)
- Succes `200`:
```json
{
  "success": true,
  "message": "Device blocked",
  "data": null,
  "timestamp": "2026-03-07T12:00:00Z"
}
```
- Echec:
  - `401`: non authentifie / action non autorisee
  - `404`: device introuvable

### POST `/identity/sessions/refresh`
- Auth: publique (token de refresh dans body)
- Body:
```json
{
  "refreshToken": "token"
}
```
- Succes `200` (`RefreshTokenResponse`)
- Echec:
  - `400`: payload invalide
  - `401`: refresh token invalide, expire ou revoque

### POST `/identity/sessions/{sessionId}/revoke`
- Auth: Bearer
- Path param: `sessionId` (UUID)
- Succes `200`: session revoquee
- Echec:
  - `401`: non autorise
  - `404`: session introuvable

## Internal Identity

### POST `/internal/identity/users/{userId}/block`
- Auth: `X-API-KEY`
- Body:
```json
{
  "reason": "fraud"
}
```
- Succes `200`: compte bloque
- Echec:
  - `401`: API key invalide
  - `400`: validation payload
  - `404`: user introuvable

## Subscription

### GET `/subscriptions/plans`
- Auth: Bearer
- Pays derive automatiquement de `account_holders.service_country_code`.
- Succes `200` (`List<SubscriptionPlanResponse>`)
- Echec:
  - `401`: non authentifie

### POST `/subscriptions/initiate`
- Auth: Bearer
- Body:
```json
{
  "planCode": "BASIC_MONTHLY",
  "idempotencyKey": "sub-001"
}
```
- Regle metier:
  - le pays est derive de `account_holders.service_country_code` (jamais du payload client).
- Succes `200` (`InitiateSubscriptionResponse`):
  - `operationId`
  - `pspReference`
  - `checkoutUrl`
  - `status`
- Echec:
  - `401`: non authentifie
  - `404`: user ou plan introuvable
  - `400`: compte inactif / payload invalide
  - `409`: conflit d'idempotence (si applicable)

## PSP

### POST `/psp/jawupay/callback`
- Auth: publique
- Header requis: `X-JAWUPAY-SIGNATURE`
- Body:
```json
{
  "pspReference": "JAWU-123",
  "amount": 5000.00,
  "currency": "XOF",
  "status": "SUCCESS",
  "nonce": "abc123",
  "timestamp": 1760000000
}
```
- Succes `200`: callback traite
- Echec:
  - `400`: signature manquante/invalide, replay detecte, timestamp hors fenetre, mismatch metier
  - `429` logique (retourne actuellement `400` avec message rate-limit exceeded)
  - `404`: operation introuvable pour la reference PSP

## Reconciliation

### POST `/reconciliation/import`
- Auth: Bearer
- Body:
```json
{
  "items": [
    {
      "pspReference": "JAWU-123",
      "amount": 5000.00,
      "currency": "XOF"
    }
  ]
}
```
- Succes `200` (`ReconciliationImportResponse`):
  - `batchId`, `total`, `matched`, `mismatched`, `adjustmentsPosted`, `items`
- Echec:
  - `400`: payload invalide
  - `401`: non authentifie

### GET `/reconciliation/settlement-summary?currency={ISO3}`
- Auth: Bearer
- Succes `200` (`List<SettlementSummaryItem>`)
- Echec:
  - `401`: non authentifie

## Circles

### POST `/circles`
- Auth: Bearer
- Body:
```json
{
  "name": "Circle Abidjan 01"
}
```
- Succes `200` (`CircleResponse`)
- Regle metier:
  - le pays du cercle est derive de `account_holders.service_country_code`.
- Echec:
  - `401`: non authentifie
  - `404`: user introuvable
  - `409`: utilisateur deja dans un cercle actif

### POST `/circles/{circleId}/join`
- Auth: Bearer
- Body:
```json
{
  "businessActivity": "Commerce detail",
  "loanStatus": "NONE",
  "repaymentProgress": 0
}
```
- Succes `200` (`CircleMemberResponse`)
- Regle metier:
  - `account_holders.service_country_code` du membre doit matcher le pays du cercle.
- Echec:
  - `401`: non authentifie
  - `404`: cercle actif introuvable / user introuvable
  - `409`: deja membre / deja dans un cercle actif
  - `400`: cercle complet (max 5) ou payload invalide

### GET `/circles/my-circle`
- Auth: Bearer
- Succes `200` (`MyCircleResponse`)
- Echec:
  - `401`: non authentifie
  - `404`: aucun cercle actif pour l'utilisateur

### GET `/circles/{circleId}/members`
- Auth: Bearer
- Succes `200` (`List<CircleMemberResponse>`)
- Echec:
  - `401`: non authentifie
  - `404`: cercle introuvable

## Progress

### GET `/progress/me`
- Auth: Bearer
- Succes `200` (`UserProgressResponse`)
- Echec:
  - `401`: non authentifie
  - `404`: user introuvable

### GET `/tasks/me`
- Auth: Bearer
- Succes `200` (`List<UserTaskResponse>`)
- Echec:
  - `401`: non authentifie
  - `404`: user introuvable

### POST `/tasks/{taskType}/complete`
- Auth: Bearer
- Path param `taskType`:
  - `PHONE_VERIFIED`
  - `KYC_COMPLETED`
  - `CIRCLE_JOINED`
  - `TRAINING_COMPLETED`
  - `PROJECT_SUBMITTED`
  - `PERSONAL_CONTRIBUTION_PAID`
- Succes `200` (`TaskCompletionResponse`):
  - `task` (tache completee)
  - `progress` (progression recalculee)
- Echec:
  - `401`: non authentifie
  - `404`: user introuvable
  - `400`: taskType invalide

## Exemple d'erreur de validation
```json
{
  "success": false,
  "message": "country must be ISO-2 uppercase",
  "data": null,
  "timestamp": "2026-03-07T12:00:00Z"
}
```

## Exemple d'erreur d'authentification
```json
{
  "success": false,
  "message": "Unauthorized",
  "data": null,
  "timestamp": "2026-03-07T12:00:00Z"
}
```
