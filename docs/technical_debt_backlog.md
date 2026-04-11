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

## 2026-04-11 - Wallet security policy must be configurable by country and operation type

- Domain:
  - `wallet / payments / security / country configuration`
- Description:
  - La politique de securite wallet ne doit pas reposer sur une regle globale unique de type `otpThreshold = X`.
  - Le systeme doit distinguer au minimum :
    - les paiements internes
    - les payouts / cashout / retraits
  - Et appliquer des regles variables selon :
    - le pays
    - le type d operation
    - le canal de paiement
  - Exemples d operations a distinguer :
    - `SUBSCRIPTION_PAYMENT`
    - `INTERNAL_PAYMENT`
    - `CIRCLE_CONTRIBUTION`
    - `SHOP_PAYMENT`
    - `WITHDRAWAL`
    - `PAYOUT`
    - `CASHOUT`
  - Exemples de parametres a rendre configurables :
    - `enabled`
    - `pinRequired`
    - `otpRequiredAbove`
    - `kycLevelRequired`
    - `minAmount`
    - `maxAmount`
    - `dailyLimit`
    - `monthlyLimit`
    - `allowedChannels`
- Impact:
  - `high`
- Why not fixed now:
  - Le focus actuel est la stabilisation QA et la mise en production du wallet, des abonnements et des flux de paiement existants.
  - La structuration complete d une policy wallet configurable par pays et type d operation depasse le perimetre de correction immediate.
- Suggested fix:
  - Introduire un modele de politique explicite, par exemple `WalletOperationPolicy`, pilote par :
    - `countryCode`
    - `operationType`
    - `channel`
  - Puis faire appliquer cette policy par le moteur de securite wallet avant execution.
  - Priorite de mise en oeuvre recommandee :
    - `SUBSCRIPTION_PAYMENT`
    - `WITHDRAWAL`
    - autres paiements internes
- Discovered in session:
  - `Wallet / subscription payment hardening and production-readiness review`
