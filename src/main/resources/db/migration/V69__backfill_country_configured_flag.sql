-- V69: Backfill countries.configured = true for countries that have all 6 required
-- config sections saved in country_config_versions.
--
-- Root cause: evaluateReadiness() was including the RBAC check (admin user assignment)
-- in the `configured` flag. Since no country-level ops user existed, the flag stayed
-- false even after a fully completed wizard.
--
-- This migration marks as configured every country that has at least one saved version
-- for each of the 6 required config types: general, accounting, membership,
-- mlm_limits, financing, guarantee.

UPDATE countries c
SET    configured = true
WHERE  c.active = true
  AND  c.configured = false
  AND  (
      SELECT COUNT(DISTINCT v.config_type)
      FROM   country_config_versions v
      WHERE  v.country_code = c.code
        AND  v.config_type IN ('GENERAL', 'ACCOUNTING', 'MEMBERSHIP', 'MLM_LIMITS', 'FINANCING', 'GUARANTEE')
        AND  v.payload IS NOT NULL
        AND  v.payload <> '{}'
        AND  v.payload <> 'null'
  ) = 6;
