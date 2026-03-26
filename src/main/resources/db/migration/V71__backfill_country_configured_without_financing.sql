-- V71: Re-run of V70 backfill — V70 was applied to DB with an earlier version of this
-- script (checksum mismatch). This migration replaces it as a safe re-run.
--
-- Financing is optional for country configuration.
--
-- BUSINESS RULE:
--   If core setup is valid, countries.configured must be TRUE even when no financing
--   offer configuration exists.
--   Global platform defaults must never block country configuration backfills.

UPDATE countries c
SET configured = TRUE,
    updated_at = NOW()
WHERE c.active = TRUE
  AND c.configured = FALSE
  AND c.currency IS NOT NULL
  AND LENGTH(BTRIM(c.currency)) = 3
  AND (
      SELECT COUNT(DISTINCT v.config_type)
      FROM country_config_versions v
      WHERE UPPER(v.country_code) = UPPER(c.code)
        AND v.config_type IN ('ACCOUNTING', 'MEMBERSHIP', 'MLM_LIMITS', 'GUARANTEE')
        AND v.payload IS NOT NULL
        AND BTRIM(v.payload) NOT IN ('', '{}', 'null')
  ) = 4
  AND (
      SELECT COUNT(DISTINCT m.system_tag)
      FROM country_account_system_mapping m
      JOIN country_chart_accounts cca
        ON cca.id = m.account_id
       AND UPPER(cca.country_code) = UPPER(c.code)
       AND cca.status = 'ACTIVE'
      WHERE UPPER(m.country_code) = UPPER(c.code)
        AND m.system_tag IN (
            'USER_WALLET',
            'PLATFORM_REVENUE',
            'MARKETPLACE_POOL',
            'MLM_POOL',
            'CIRCLE_POOL',
            'LOAN_PORTFOLIO',
            'INTEREST_REVENUE',
            'PSP_CLEARING'
        )
  ) = 8
  AND EXISTS (
      SELECT 1
      FROM subscription_plans sp
      WHERE UPPER(sp.country) = UPPER(c.code)
        AND sp.active = TRUE
      GROUP BY UPPER(sp.country)
      HAVING COUNT(*) = 10
         AND COUNT(DISTINCT UPPER(COALESCE(sp.currency, ''))) = 1
         AND COUNT(*) FILTER (WHERE sp.grade = 'FREE') = 1
         AND COUNT(*) FILTER (
             WHERE sp.grade = 'FREE'
               AND sp.duration_months = 1
               AND COALESCE(sp.price, 0) = 0
         ) = 1
         AND COUNT(DISTINCT CASE WHEN sp.grade = 'CLASSIC' THEN sp.duration_months END) = 3
         AND COUNT(DISTINCT CASE WHEN sp.grade = 'GOLD' THEN sp.duration_months END) = 3
         AND COUNT(DISTINCT CASE WHEN sp.grade = 'DIAMOND' THEN sp.duration_months END) = 3
         AND COUNT(*) FILTER (WHERE sp.grade = 'CLASSIC' AND sp.duration_months IN (1, 3, 12)) = 3
         AND COUNT(*) FILTER (WHERE sp.grade = 'GOLD' AND sp.duration_months IN (1, 3, 12)) = 3
         AND COUNT(*) FILTER (WHERE sp.grade = 'DIAMOND' AND sp.duration_months IN (1, 3, 12)) = 3
  );
