# Technical Debt Backlog

## 2026-04-11 - Deployment provenance for public API

- Scope: `cc-backend-monolitic` deployment and gateway verification
- Observed during: wallet activation production wiring investigation
- Problem:
  - Maven Docker builds first produce a local `mvp` tag before CI retags the image to `${CI_PIPELINE_IID}`, which still makes local-to-runtime provenance harder to reason about during incident response.
  - The public deployment does not expose build or git revision metadata through an unauthenticated diagnostics endpoint, so API behavior cannot be tied back to a specific commit from the gateway alone.
  - The public gateway does not expose enough upstream diagnostics to quickly distinguish a transient entrypoint failure from an unhealthy backend instance during rollout windows.
- Impact:
  - Production incidents can look like code regressions when they are actually stale images, partial rollouts, or restarted upstream instances.
  - Verifying whether a wallet fix is deployed requires indirect clues such as Swagger timestamps and route behavior instead of a direct build fingerprint.
- Follow-up:
  - Publish immutable image tags per build or commit SHA for `cc-backend-monolitic`.
  - Expose build metadata through a safe diagnostics surface such as `actuator/info` or an internal admin endpoint.
  - Add a safe runtime diagnostic that reveals the active image digest or commit SHA behind `api-gateway.clubchretien.org/cc-backend-monolitic`.
  - Record the deployed image digest or git SHA in the deployment process so gateway incidents can be correlated quickly.

## 2026-04-11 - Legacy subscription initiation references drift from runtime

- Scope: `cc-backend-monolitic` docs and API examples for subscription payments
- Observed during: CC wallet payment final hardening
- Problem:
  - The active Flutter client now initiates subscription payments through `POST /api/mobile/payments/initiate`, and `SubscriptionService.initiate(...)` is guarded as legacy-only.
  - Several artifacts still advertise `POST /subscriptions/initiate`, including `docs/mobile_api_contract_v1.md`, `docs/flutter_prd_club_chretien.md`, the workspace-level `flutter_prd_club_chretien.md`, and `postman/cc-app-backend-local.postman_collection.json`.
- Impact:
  - Internal consumers and test operators can still follow stale integration guidance and hit a deprecated endpoint that is intentionally blocked.
  - Production verification becomes noisier because a failing legacy call can look like a regression in the live wallet subscription flow.
- Follow-up:
  - Update public/internal contract docs so subscription renewals point to `POST /api/mobile/payments/initiate`.
  - Refresh Postman examples and any QA scripts that still exercise `/subscriptions/initiate`.
  - Remove the legacy endpoint entirely once telemetry confirms no remaining non-documentation consumers.
