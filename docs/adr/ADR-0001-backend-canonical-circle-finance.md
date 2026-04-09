# ADR-0001: Backend Canonical Decision For Circle Finance Cleanup

- Status: Accepted
- Date: 2026-04-09
- Scope: Backend only
- Related work: PR1 Deprecation controlee

## Context

Club Chretien is moving to a new backend canon for circles, savings, seed capital, and chat.

The validated product direction is:

- a circle is a social and disciplinary group of 3 to 6 members
- a circle is not a legal entity and must not carry funds
- financial access requires an active monthly membership
- savings are personal and must be routed to personal IMF channels
- seed capital must use group solidarity without turning the circle into a treasury holder
- chat remains central to the UX, but backend business truth must stay separate from derived chat messages

The current monolith contains overlapping implementations:

- social circle management in `circles/*`
- legacy circle finance in `circles/*` controllers and entities
- canonical loan mechanics in `credit/*`
- parallel client loan application flow in `financing/*`
- legacy circle capital pool allocation in `capital/*`
- two circle chat stacks in `chat/*` and `community/*`

This ADR freezes the canonical direction before deeper refactors.

## Decision Summary

### Canonical domains kept as foundation

- Membership and subscriptions remain canonical in `payments.subscription/*`
- Wallet, operations, and accounting remain canonical in `wallet/*`, `payments.operation/*`, and `ledger/*`
- Loan, repayment schedule, and repayment recording remain canonical in `credit/*`
- Financial partner registry remains canonical in `financing/domain/entity/FinancialPartner`
- MLM lineage remains canonical in `mlm/*`
- Notifications remain canonical in `notifications/*`

### Domains explicitly marked legacy

- legacy circle finance endpoints in `circles/controller/CircleFinanceController`
- legacy circle loan repayment endpoint in `circles/controller/CircleLoanRepaymentController`
- legacy circle chat endpoints in `community/controller/CircleChatController`
- direct client creation on `POST /financing/applications`

These routes are kept temporarily for compatibility and telemetry, but they are no longer the target architecture.

### Future canonical build path

- circle stays social and disciplinary in `circles/*`
- personal savings will be modeled by a future `ImfSavingsChannel`
- seed capital will converge on the `credit/*` stack
- future credit application and eligibility logic will sit in dedicated aggregates above `credit/*`
- chat system messages will be derived from business events instead of being business truth

## Why Circle Is No Longer A Fund Holder

The validated product model does not allow the circle to own money or act like a legal treasury.

Therefore:

- no legal bank account of the circle
- no legal pooled cashbox of the circle
- no capital pool tied to a circle as canonical business truth
- no canonical financial cycle where the circle itself is treated as fund holder

The circle remains important, but only as:

- social guarantee group
- discipline group
- meeting group
- progress tracking group
- shared context for eligibility and solidarity

This means existing models that imply circle-owned funds are legacy and must be retired over time.

## Why Credit Becomes The Canonical Loan Path

The `credit/*` stack already contains the most reusable transactional backbone:

- `Loan`
- `RepaymentSchedule`
- `LoanRepayment`
- disbursement through wallet and accounting
- repayment recording through wallet and institution ledger

The current `financing/*` stack is still useful for partner administration and offer catalogs, but direct client creation there is not the future canonical path.

Going forward:

- client-facing seed capital must converge toward canonical eligibility plus `credit/*`
- partner and offer management can continue to exist around that path
- duplicate client loan creation paths must be removed after migration telemetry confirms low usage

## Why Savings Move To A Future ImfSavingsChannel

The target product requires personal savings channels configured with country IMF partners.

Existing code provides:

- partner metadata via `FinancialPartner`
- partner type `IMF`

Existing code does not provide:

- personal IMF savings channel activation
- personal IMF savings account linkage
- circle-level discipline tracking without implying pooled custody

So the future canonical savings path is:

- `MemberFinancialProfile`
- `ImfSavingsChannel`
- `SavingsCycle`
- `MemberSavingsContribution`

This future model must track discipline and confirmations without modeling the circle as the wallet owner.

## Keep / Refactor / Deprecate / Delete / Build New

### Keep

- `payments.subscription/*`
- `wallet/*`
- `payments.operation/*`
- `ledger/*`
- `credit/*` loan and repayment core
- `financing/domain/entity/FinancialPartner`
- `mlm/*`
- `notifications/*`

### Refactor

- `circles/service/CircleService`
- `mobile/onboarding/service/MobileOnboardingJourneyService`
- `credit/service/CreditScoringService`
- `chat/service/ChatMessageService`
- `financing/service/FinancingApplicationService`

### Deprecate now

- `circles/controller/CircleFinanceController`
- `circles/controller/CircleLoanRepaymentController`
- `community/controller/CircleChatController`
- `POST /financing/applications`

### Delete later

- `CircleCycle`
- `CircleContribution`
- `CircleLoan`
- `CircleRepayment`
- `CapitalPool`
- `CapitalFunding`

### Build new

- `FinancialAccessPolicy`
- `MemberFinancialProfile`
- `ImfSavingsChannel`
- `CircleMeeting`
- `CircleTrustScore`
- `SeedCapitalEligibility`
- `CreditApplication`
- `GroupGuaranteeIncident`
- `CircleCoachAssignment`
- `CircleSystemMessage`

## Migration Strategy

### PR1 - Deprecation controlee

- write this ADR into the repository
- mark legacy endpoints as deprecated in code
- add telemetry logs to detect real usage
- keep contracts unchanged

### Next cleanup phases

- centralize financial access checks
- neutralize legacy circle finance logic
- remove capital pool path
- unify circle chat on the canonical chat stack
- introduce new canonical aggregates above the existing reusable core

## Compatibility Rules

For PR1 and nearby cleanup work:

- do not remove legacy routes yet
- do not change request or response contracts yet
- do not break current mobile clients
- do not refactor the full domain yet

This ADR is the repository-level source of truth for upcoming backend cleanup PRs.
