-- psql -v firebase_uid='SBSQfK8OHFQVOKAmrxjS0gaSA5G2' \
--      -v account_holder_id='6208ebec-af8f-455f-b2de-cf9c9ad8c755' \
--      -v email='user@example.com' \
--      -f scripts/audit-identity-graph.sql
-- Audits the Club Chretien identity graph for one Firebase user / email / account holder.

\if :{?firebase_uid}
\else
\set firebase_uid 'SBSQfK8OHFQVOKAmrxjS0gaSA5G2'
\endif

\if :{?account_holder_id}
\else
\set account_holder_id '6208ebec-af8f-455f-b2de-cf9c9ad8c755'
\endif

\if :{?email}
\else
\set email ''
\endif

\echo '1) users by firebase_uid / email / account_holder_id'
SELECT
    u.id,
    u.firebase_uid,
    u.email,
    u.role,
    u.account_status,
    u.kyc_status,
    u.created_at,
    u.updated_at
FROM users u
WHERE u.firebase_uid = :'firebase_uid'
   OR (:'email' <> '' AND lower(u.email) = lower(:'email'))
   OR u.id = :'account_holder_id'::uuid
ORDER BY u.created_at NULLS LAST;

\echo '2) app_users by firebase_uid / account_holder_id'
SELECT
    au.id,
    au.firebase_uid,
    au.account_holder_id,
    au.role,
    au.created_at
FROM app_users au
WHERE au.firebase_uid = :'firebase_uid'
   OR au.account_holder_id = :'account_holder_id'::uuid
   OR au.id = :'account_holder_id'::uuid
ORDER BY au.created_at NULLS LAST;

\echo '3) account_holders by target and linked app_users'
WITH linked AS (
    SELECT account_holder_id FROM app_users WHERE firebase_uid = :'firebase_uid'
    UNION
    SELECT :'account_holder_id'::uuid
)
SELECT
    ah.id,
    ah.first_name,
    ah.last_name,
    ah.phone_number,
    ah.country_code,
    ah.service_country_code,
    ah.account_type,
    ah.created_via,
    ah.created_by_user_id,
    ah.kyc_status,
    ah.kyc_level,
    ah.birth_date,
    ah.document_number,
    ah.nationality,
    ah.declared_document_type,
    ah.phone_verified_at,
    ah.cpass_case_id,
    ah.created_at
FROM account_holders ah
WHERE ah.id IN (SELECT account_holder_id FROM linked)
ORDER BY ah.created_at NULLS LAST;

\echo '4) identity profile / devices / sessions for candidate users'
WITH candidate_users AS (
    SELECT id FROM users WHERE firebase_uid = :'firebase_uid'
    UNION
    SELECT id FROM users WHERE :'email' <> '' AND lower(email) = lower(:'email')
    UNION
    SELECT id FROM users WHERE id = :'account_holder_id'::uuid
    UNION
    SELECT id FROM app_users WHERE firebase_uid = :'firebase_uid'
)
SELECT 'user_profiles' AS table_name, count(*) AS rows FROM user_profiles WHERE user_id IN (SELECT id FROM candidate_users)
UNION ALL
SELECT 'user_devices', count(*) FROM user_devices WHERE user_id IN (SELECT id FROM candidate_users)
UNION ALL
SELECT 'user_sessions', count(*) FROM user_sessions WHERE user_id IN (SELECT id FROM candidate_users);

\echo '5) mobile onboarding and KYC state for candidate account holders'
WITH candidate_holders AS (
    SELECT account_holder_id AS id FROM app_users WHERE firebase_uid = :'firebase_uid'
    UNION
    SELECT :'account_holder_id'::uuid
)
SELECT
    ah.id AS account_holder_id,
    moj.status AS onboarding_status,
    moj.country_code AS onboarding_country_code,
    moj.sponsor_set,
    moj.network_linked,
    moj.onboarding_completed,
    ah.kyc_status,
    ah.kyc_level,
    ah.cpass_case_id
FROM candidate_holders ch
LEFT JOIN account_holders ah ON ah.id = ch.id
LEFT JOIN mobile_onboarding_journeys moj ON moj.account_holder_id = ch.id
ORDER BY ah.created_at NULLS LAST;

\echo '6) KYC draft artifacts for candidate account holders'
WITH candidate_holders AS (
    SELECT account_holder_id AS id FROM app_users WHERE firebase_uid = :'firebase_uid'
    UNION
    SELECT :'account_holder_id'::uuid
)
SELECT
    'kyc_documents' AS source,
    kd.account_holder_id,
    kd.id,
    kd.document_type::text AS type,
    kd.status::text AS status,
    kd.created_at
FROM kyc_documents kd
WHERE kd.account_holder_id IN (SELECT id FROM candidate_holders)
UNION ALL
SELECT
    'media_files',
    mf.account_holder_id,
    mf.id,
    mf.type::text,
    mf.status::text,
    mf.created_at
FROM media_files mf
WHERE mf.account_holder_id IN (SELECT id FROM candidate_holders)
  AND mf.type IN ('KYC_IDENTITY_FRONT', 'KYC_IDENTITY_BACK', 'KYC_SELFIE')
ORDER BY account_holder_id, created_at NULLS LAST;

\echo '7) graph consistency verdict'
WITH u AS (
    SELECT id FROM users WHERE firebase_uid = :'firebase_uid'
),
au AS (
    SELECT id, account_holder_id FROM app_users WHERE firebase_uid = :'firebase_uid'
)
SELECT
    (SELECT count(*) FROM u) AS users_by_firebase_uid,
    (SELECT count(*) FROM au) AS app_users_by_firebase_uid,
    (SELECT count(*) FROM account_holders ah WHERE ah.id IN (SELECT account_holder_id FROM au)) AS account_holders_linked_to_app_users,
    EXISTS (
        SELECT 1
        FROM u
        JOIN au ON au.id = u.id AND au.account_holder_id = u.id
    ) AS has_canonical_same_id_triplet,
    EXISTS (
        SELECT 1
        FROM u
        JOIN au ON au.id <> u.id OR au.account_holder_id <> u.id
    ) AS has_divergent_user_app_user_mapping;
