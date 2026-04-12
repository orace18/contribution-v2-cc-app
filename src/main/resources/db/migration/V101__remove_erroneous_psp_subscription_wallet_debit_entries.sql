-- Root cause: WalletLedgerProjectionService unconditionally created a DEBIT
-- entry in wallet_ledger_entries for every SUBSCRIPTION operation, including
-- those funded via an external PSP (MoMo, card). For PSP-funded subscriptions,
-- the user's internal wallet must NOT be debited — the payment was collected
-- externally. This caused the mobile wallet dashboard to show a lower balance
-- than the authoritative wallet balance endpoint.
--
-- The application-level fix (WalletLedgerProjectionService) prevents new
-- erroneous entries from being created. This migration removes the stale
-- entries already present in the database.
--
-- Safe criteria:
--   - entry_type = 'DEBIT' and category = 'PAYMENT'     → subscription DEBIT shape
--   - operation.type = 'SUBSCRIPTION'                   → subscription operation
--   - operation.psp_reference IS NOT NULL and != ''     → PSP-funded (not wallet-funded)
--
-- Wallet-funded subscriptions (psp_reference IS NULL) are untouched.

DELETE FROM wallet_ledger_entries wle
WHERE wle.entry_type = 'DEBIT'
  AND wle.category   = 'PAYMENT'
  AND wle.operation_id IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM operations o
      WHERE o.id            = wle.operation_id
        AND o.type          = 'SUBSCRIPTION'
        AND o.psp_reference IS NOT NULL
        AND o.psp_reference <> ''
  );
