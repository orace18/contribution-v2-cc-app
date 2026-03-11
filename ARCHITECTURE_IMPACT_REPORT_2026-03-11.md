# Architecture Impact Report (Pre-Refactor)

Date: 2026-03-11  
Repository: `cc-app-backend-v2-monolitic`  
Scope: Baseline architecture impact assessment before any refactor implementation.

## 1. Executive Summary

The backend is a modular monolith (Spring Boot 3.5.11, Java 21) with strong domain packaging but high central coupling around shared layers. Refactor blast radius is significant if changes touch `common`, `identity`, or financial processing paths.

Current baseline:
- 17 top-level modules under `com.ccapp`
- 375 production Java files (`src/main/java`), 15,442 LOC
- 21 Flyway migrations (`V1`..`V22`, `V12` absent)
- 3 test files in `src/test/java`

Primary conclusion: **Refactor should not start until scope, invariants, migration policy, and regression coverage are explicitly defined.**

## 2. Assessment Method and Boundaries

This report is based on static analysis of:
- `src/main/java` (module structure, imports, hotspot classes)
- `src/main/resources` (properties and Flyway migrations)
- Build/runtime descriptors (`pom.xml`, compose files)

Not included:
- Runtime profiling
- Production traffic traces
- Full integration test execution in this step

## 3. Baseline Architecture Snapshot

### 3.1 Structural Style
- Domain-oriented modular monolith with mostly consistent internal layering (`controller`, `service`, `repository`, `domain`, `dto`).
- Shared cross-cutting modules: `common` and `config`.
- Largest modules by file count:
  - `admin`: 72
  - `identity`: 70
  - `credit`: 29
  - `notifications`: 26
  - `media`: 24
  - `agent`: 23
  - `payments`: 23

### 3.2 Security and Access Model
- Stateless Spring Security filter chain with:
  - Internal API key auth (`/internal/**`)
  - Admin JWT auth (`/api/admin/**`)
  - Firebase token auth (general API)
- Public PSP callback endpoint (`POST /psp/jawupay/callback`) protected by HMAC + replay window + nonce + rate limit logic.

### 3.3 Data and Persistence Model
- PostgreSQL + JPA + Flyway.
- Financial flows span `payments` (operation/psp/subscription) and `ledger`.
- 47 `@Entity` declarations.
- Risk: `spring.jpa.hibernate.ddl-auto=update` remains enabled in both dev and prod properties, which conflicts with strict migration-first governance.

### 3.4 External Integrations (Code-Level)
- Firebase Admin SDK (identity/auth verification).
- S3-compatible object storage (MinIO-style endpoint via AWS SDK v2).
- PSP callback handling and initiation contract; initiation client currently returns generated mock checkout values (stub behavior).
- Scheduled jobs in:
  - `admin.invitation.AdminInvitationExpiryJob`
  - `notifications.scheduler.SubscriptionExpirationJob`

## 4. Coupling and Hotspot Analysis

### 4.1 Cross-Module Coupling (import-based, weighted)

Top outgoing dependencies:
- `admin` 76
- `credit` 51
- `media` 32
- `circles` 29
- `identity` 26

Top incoming dependencies:
- `common` 145
- `identity` 74
- `payments` 28
- `circles` 24
- `country` 20
- `notifications` 20

Top cross-module edges:
- `admin -> common` 26
- `identity -> common` 24
- `media -> identity` 17
- `admin -> identity` 12
- `payments -> common` 12
- `admin -> circles` 11
- `media -> common` 11

Interpretation:
- `common` is the highest fan-in hub; changes here propagate system-wide.
- `identity` is both a provider and consumer hub; auth/session changes have broad effects.
- `admin` and `credit` are orchestration-heavy and generate broad outbound dependencies.

### 4.2 Large-Class Hotspots (LOC)
- `IdentityService` 615
- `CircleService` 313
- `MediaService` 286
- `NotificationService` 225
- `LoanService` 225
- `AdminCountryConfigService` 206
- `AdminAuthService` 196

These classes are high-risk refactor points due to responsibility concentration.

### 4.3 Test Safety Net
- 3 test classes total:
  - accounting ledger posting
  - accounting operation service
  - identity refresh token service
- No broad integration coverage for cross-module flows (auth, onboarding, PSP callback, ledger posting, media lifecycle).

## 5. Impact by Refactor Surface

### 5.1 Identity and Security Refactor
Impact: **Very High**

Why:
- `identity` has high fan-in from domain modules.
- Security filter chain composition depends on identity principal and claim handling.
- Behavior spans sync, session/device, delegation, onboarding contexts.

Controls required:
- Preserve authentication principal contract.
- Add regression tests for login/sync/me/admin auth paths.

### 5.2 Common Layer Refactor (`common`)
Impact: **Very High**

Why:
- Highest incoming dependency count.
- Exception/model changes can alter API error contract across modules.

Controls required:
- Freeze error response schema.
- Add API contract tests on representative endpoints across domains.

### 5.3 Payments, PSP, and Ledger Refactor
Impact: **High**

Why:
- Financial correctness depends on callback validation + operation confirmation + ledger posting chain.
- Idempotency and replay protection are critical.
- PSP initiate client is currently stubbed; replacing it introduces external failure modes.

Controls required:
- End-to-end integration test for callback -> operation status -> accounting event.
- Explicit idempotency and replay regression tests.

### 5.4 Admin/Country/Onboarding Refactor
Impact: **High**

Why:
- Back-office and country configuration touch access policies and business scope resolution.
- Cross-cutting effects on identity, capital, circles, media, notifications.

Controls required:
- State transition tests and authorization matrix validation.

### 5.5 Credit/Capital/Agent Flow Refactor
Impact: **High**

Why:
- Multi-step business workflows across financing, repayment, assisted/offline operations.
- Broad orchestration dependencies increase regression risk.

Controls required:
- Scenario-based workflow tests per lifecycle stage.

## 6. Blocking Risks Before Safe Refactor

1. **Low regression coverage**: 3 tests for a 375-file codebase.
2. **Schema governance mismatch**: Flyway exists, but `ddl-auto=update` is enabled in active profiles.
3. **Configuration and secret hygiene risk**: sensitive values and debug flags are present in committed profile properties.
4. **Hidden wiring debt risk**: `allow-circular-references=true` and `allow-bean-definition-overriding=true` can mask structural issues.
5. **Hub fragility**: `common` and `identity` are high fan-in components.
6. **Service size concentration**: several >200 LOC service classes mix orchestration and domain logic.
7. **Externalization gap**: PSP initiation remains stubbed, so future real integration will alter reliability characteristics.

## 7. Mandatory Entry Criteria (Go/No-Go)

Before any refactor implementation:
1. Define exact refactor scope in one sentence (module + concern + non-goals).
2. List invariants that must remain unchanged (auth contract, accounting consistency, callback idempotency, onboarding transitions).
3. Decide migration policy: Flyway-only schema change path; disable implicit runtime schema mutation.
4. Add minimum regression tests for impacted critical paths before structural edits.
5. Define API compatibility policy (strict backward compatibility or explicit versioning).
6. Prepare rollback runbook (application + DB considerations).

## 8. Suggested Sequencing

1. Safety baseline first: config hardening + regression tests for selected scope.
2. Boundary extraction at high-coupling seams (`identity`, `payments/ledger`, `common`) without behavior changes.
3. Incremental refactor by bounded context with migration-per-change discipline.
4. Validate after each slice using contract and workflow tests.

## 9. Status

This architecture impact report has been generated as requested, prior to refactor work.  
No refactor implementation was performed in this step.
