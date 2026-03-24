UPDATE countries c
SET configured = FALSE,
    updated_at = NOW()
WHERE c.code <> 'INT'
  AND c.configured = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM subscription_plans sp
      WHERE UPPER(sp.country) = c.code
        AND sp.active = TRUE
      GROUP BY UPPER(sp.country)
      HAVING COUNT(*) = 10
         AND COUNT(DISTINCT UPPER(COALESCE(sp.currency, ''))) = 1
         AND COUNT(*) FILTER (WHERE sp.grade = 'FREE') = 1
         AND COUNT(*) FILTER (WHERE sp.grade = 'FREE' AND sp.duration_months = 1 AND COALESCE(sp.price, 0) = 0) = 1
         AND COUNT(DISTINCT CASE WHEN sp.grade = 'CLASSIC' THEN sp.duration_months END) = 3
         AND COUNT(DISTINCT CASE WHEN sp.grade = 'GOLD' THEN sp.duration_months END) = 3
         AND COUNT(DISTINCT CASE WHEN sp.grade = 'DIAMOND' THEN sp.duration_months END) = 3
         AND COUNT(*) FILTER (WHERE sp.grade = 'CLASSIC' AND sp.duration_months IN (1, 3, 12)) = 3
         AND COUNT(*) FILTER (WHERE sp.grade = 'GOLD' AND sp.duration_months IN (1, 3, 12)) = 3
         AND COUNT(*) FILTER (WHERE sp.grade = 'DIAMOND' AND sp.duration_months IN (1, 3, 12)) = 3
  );
