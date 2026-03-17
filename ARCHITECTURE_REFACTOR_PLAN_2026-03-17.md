# CC App Refactor Audit and Incremental Plan (2026-03-17)

## 1) Global Architecture Audit (Backend + Backoffice)

### Backend structure snapshot
- Modular monolith is already domain-sliced (`identity`, `circles`, `credit`, `ledger`, `payments`, `chat`, `notifications`, `country`, `financing`, `admin`, etc.).
- Strong reuse of `common` package across modules, but several cross-module business dependencies still exist (notably `admin`, `credit`, `financing`, `payments`, `identity`).
- Flyway history is rich and incremental (`V1` -> `V37`) and production-focused.

### Cross-module dependency hotspots
- `admin` couples to many domains (`payments`, `identity`, `credit`, `mlm`, `capital`, `circles`, `community`).
- `credit` still depends on `admin` policy storage (`admin_policies`) for runtime business checks.
- `financing` maintains a parallel partner ledger/accounting abstraction while also posting to core ledger.

### Configuration scattering
- Country-level behavior is currently split across:
  - `countries` table (`country` module)
  - `admin_policies` (`admin` module)
  - subscription/capital/financing tables
  - service-level hardcoded defaults and normalizations
- No unified versioned configuration engine by country and config domain.

### Backend/Backoffice contract inconsistencies
- API prefixes are mixed (`/api/admin/**`, `/admin/**`, `/financing/**`, `/credit/**`, `/capital/**`, `/chat/**`).
- Backoffice consumes some endpoints that are not yet present in backend (example: `/admin/reports`, `/admin/partners/users`, `/api/admin/media`, `/api/admin/audit`).
- Enum mismatches:
  - Backend `FinancialPartnerType`: `IMF | BANK | FUND`
  - Backoffice `PartnerRecord.type`: `IMF | BANK | FINTECH | OTHER`
  - Backend `PartnerBackofficeRole`: `PARTNER_ADMIN | PARTNER_ANALYST | PARTNER_VIEWER`
  - Backoffice `PartnerUserRole`: includes `PARTNER_REVIEWER`

## 2) Risk Areas

- Idempotency collision risk in financial `OperationService` (same idempotency key could map to a different operation signature).
- Configuration drift risk due to fragmented country/policy storage.
- Route duplication and mixed prefixes increasing maintenance overhead and frontend confusion.
- Parallel ledgers (`partner_ledger_*` and core ledger) require strict invariants to avoid reporting divergence.

## 3) Quick Wins (safe, incremental)

- Introduce unified versioned `country_config_versions` engine scoped by country.
- Keep existing APIs while adding canonical admin config endpoints.
- Add compatibility fallback in credit policy checks (`new engine` + `legacy admin_policies`).
- Harden operation idempotency signature checks.

## 4) Deep Refactors (phased)

- Full API namespace standardization with a deprecation window and compatibility aliases.
- Shared contract generation pipeline for frontend/backend enums and DTOs.
- Financial flow invariants and domain events hardening (wallet as derived read model, ledger as source of truth).
- Query-level optimization pass (N+1 elimination and heavy read path indexing).

## 5) Incremental Delivery Plan

1. Country Configuration Engine (versioned, per country, per config type) + migrations.
2. Credit/financing policy consumers switched to config engine with legacy fallback.
3. API contract harmonization and endpoint compatibility layer.
4. Backoffice contract alignment (services, enums, forms).
5. Performance and safety hardening on highest-traffic endpoints.

## 6) Commit Structure Proposal

1. `feat(country-config): add versioned country configuration engine and admin APIs`
2. `feat(credit): consume versioned risk config with legacy policy fallback`
3. `fix(payments): enforce idempotency signature safety in operation service`
4. `test(country-config): add integration coverage for versioning and validation`
5. `chore(architecture): document quick wins, risks, and deep refactor tracks`
