# Mobile Network / Team Audit and PRD

Date: 2026-04-03
Scope: `cc-backend-monolitic` mobile MLM/network support for the Flutter Team module

## 1. API Inventory

### Existing APIs discovered before this implementation

| API | Controller | Service | Repository / Core data source | Response model |
| --- | --- | --- | --- | --- |
| `POST /api/mobile/sponsors/lookup` | `MobileSponsorController` | `MobileSponsorLookupService` | `AccountHolderRepository`, `MlmRelationshipRepository` via `SponsorEligibilityValidator` | `ApiEnvelope<SponsorLookupResponse>` |
| `POST /api/mobile/onboarding/set-sponsor` | `MobileOnboardingController` | `MobileOnboardingJourneyService` | `MobileOnboardingJourneyRepository`, `MlmRelationshipRepository`, `MlmMemberLinkRepository` | `ApiEnvelope<MobileSponsorSelectionResponse>` |
| `GET /api/mobile/mlm/dashboard` | `MobileMlmController` | `MobileMlmDashboardService` | `MlmMemberMetricsRepository`, `MlmCommissionRepository`, `MlmAnalyticsService` | `CommonResponse<MobileMlmDashboardResponse>` |
| `POST /mlm/placements` | `MLMController` | `MLMPlacementService` | `MlmRelationshipRepository`, `MlmMemberLinkRepository` | `CommonResponse<MlmPlacementResponse>` |
| `GET /mlm/{accountHolderId}/upline` | `MLMController` | `MLMQueryService` | `MlmGraphService`, `MlmRelationshipRepository`, `AccountHolderRepository` | `CommonResponse<List<MlmHierarchyMemberResponse>>` |
| `GET /mlm/{accountHolderId}/downline` | `MLMController` | `MLMQueryService` | `MlmGraphService`, `MlmRelationshipRepository`, `AccountHolderRepository` | `CommonResponse<List<MlmHierarchyMemberResponse>>` |
| `GET /mlm/{accountHolderId}/team-size` | `MLMController` | `MLMQueryService` | `MlmGraphService`, `MlmRelationshipRepository` | `CommonResponse<MlmTeamSizeResponse>` |
| `GET /mlm/{accountHolderId}/levels/{level}/members` | `MLMController` | `MLMQueryService` | `MlmGraphService`, `MlmRelationshipRepository`, `AccountHolderRepository` | `CommonResponse<List<MlmHierarchyMemberResponse>>` |
| `GET /api/mobile/home` | `MobileHomeController` | `HomeFacadeService` | `UserTaskRepository`, `MembershipRepository`, `CircleService`, `UserProfileRepository` | `ApiEnvelope<MobileHomeDashboardResponse>` |
| `GET /tasks` | `TaskController` | `TaskService` | `UserTaskRepository`, `TaskEngineService` | `CommonResponse<List<TaskResponse>>` |
| `GET /tasks/next` | `TaskController` | `TaskService` | `UserTaskRepository`, `TaskEngineService` | `CommonResponse<TaskResponse>` |
| `GET /tasks/me` | `TaskController` | `TaskService` | `UserTaskRepository` | `CommonResponse<List<UserTaskResponse>>` |
| `GET /api/admin/commissions` | `AdminMlmMonitoringController` | `AdminMlmMonitoringService` | `MlmCommissionRepository` | `CommonResponse<List<AdminCommissionResponse>>` |
| `GET /api/admin/commission-payouts` | `AdminMlmMonitoringController` | `AdminMlmMonitoringService` | `CommissionPayoutBatchRepository` | `CommonResponse<List<AdminCommissionPayoutBatchResponse>>` |
| `GET /api/admin/mlm/network-health` | `AdminMlmMonitoringController` | `AdminMlmMonitoringService` | MLM analytics repositories | `CommonResponse<AdminMlmNetworkHealthResponse>` |
| `GET /api/admin/weekly-volume` | `AdminMlmMonitoringController` | `AdminMlmMonitoringService` | `MlmVolumeLedgerRepository` | `CommonResponse<List<AdminWeeklyVolumeResponse>>` |

### Gap assessment against requested mobile contract

| Requested API | Status before work | Result |
| --- | --- | --- |
| `GET /api/mobile/network/team/stats` | Missing | Implemented |
| `GET /api/mobile/network/directs` | Missing | Implemented |
| `GET /api/mobile/network/activity/today` | Missing | Implemented |
| `GET /api/mobile/network/followups` | Missing | Implemented |
| `GET /api/mobile/network/tree` | Missing | Implemented |
| `GET /api/mobile/network/search?q=` | Missing | Implemented |
| `GET /api/mobile/earnings/summary` | Missing | Implemented |
| `GET /api/mobile/earnings/history` | Missing | Implemented |

## 2. Implemented APIs

### New mobile endpoints

| API | Controller | Service | Notes |
| --- | --- | --- | --- |
| `GET /api/mobile/network/team/stats` | `MobileNetworkController` | `MobileNetworkService` | Sponsor-network active/inactive/follow-up counters |
| `GET /api/mobile/network/directs` | `MobileNetworkController` | `MobileNetworkService` | Direct sponsored members only |
| `GET /api/mobile/network/activity/today` | `MobileNetworkController` | `MobileNetworkService` | New sponsor-network joins plus memberships activated today |
| `GET /api/mobile/network/followups` | `MobileNetworkController` | `MobileNetworkService` | Members inactive long enough to require relaunch |
| `GET /api/mobile/network/tree?view=GENEALOGY|PLACEMENT` | `MobileNetworkController` | `MobileNetworkService` | `GENEALOGY` uses sponsor links, `PLACEMENT` uses placement links |
| `GET /api/mobile/network/search?q=` | `MobileNetworkController` | `MobileNetworkService` | Sponsor-network search by name or phone |
| `GET /api/mobile/earnings/summary` | `MobileEarningsController` | `MobileEarningsService` | RMS totals, weekly delta, potential |
| `GET /api/mobile/earnings/history` | `MobileEarningsController` | `MobileEarningsService` | Personal MLM commission history |

### Code references

- Controller: `src/main/java/com/ccapp/mobile/network/controller/MobileNetworkController.java`
- Service: `src/main/java/com/ccapp/mobile/network/service/MobileNetworkService.java`
- DTOs: `src/main/java/com/ccapp/mobile/network/dto/*`
- Controller: `src/main/java/com/ccapp/mobile/earnings/controller/MobileEarningsController.java`
- Service: `src/main/java/com/ccapp/mobile/earnings/service/MobileEarningsService.java`
- DTOs: `src/main/java/com/ccapp/mobile/earnings/dto/*`
- Sponsor and placement recursive projections: `src/main/java/com/ccapp/mlm/repository/MlmMemberLinkRepository.java`
- Bulk membership lookup: `src/main/java/com/ccapp/payments/subscription/repository/MembershipRepository.java`
- Earnings aggregation: `src/main/java/com/ccapp/payments/commission/repository/MlmCommissionRepository.java`
- Avatar bulk lookup: `src/main/java/com/ccapp/identity/app_user/repository/AppUserRepository.java`, `src/main/java/com/ccapp/identity/repository/UserProfileRepository.java`

## 3. PRD

### Domain interpretation

- `directs`, `team stats`, `followups`, `search`, and default `tree` use the sponsor hierarchy from `mlm_member_links.sponsor_account_holder_id`.
- Placement visualization uses `tree?view=PLACEMENT` and relies on `mlm_member_links.placement_account_holder_id`.
- Dashboard daily tasks are already covered by existing `/tasks` and `/tasks/next` endpoints, so no duplicate task API was added.

### Screen: Network Dashboard

Purpose:
- Give the user an at-a-glance view of network growth, RMS/earnings, members needing attention, and daily action prompts.

Data sources:
- Today activity: `GET /api/mobile/network/activity/today`
- RMS block: `GET /api/mobile/earnings/summary`
- Team counts: `GET /api/mobile/network/team/stats`
- Members to follow up: `GET /api/mobile/network/followups`
- Daily tasks: existing `GET /tasks` or `GET /tasks/next`

Expected UX:
- Show today growth cards
- Show RMS total and weekly progression
- Show sponsor-network active/inactive/follow-up counts
- Surface the highest-priority follow-up members
- Show task checklist from the existing task engine

### Screen: Team Screen

Purpose:
- List direct sponsored members only, generation 1.

API:
- `GET /api/mobile/network/directs`

Response contract:
- `accountHolderId`
- `firstName`
- `lastName`
- `avatar`
- `generation`
- `downlineCount`
- `membershipStatus`
- `nextAction`

Membership state rules:
- `ACTIVE`: active membership and not near expiry
- `EXPIRING`: active membership with expiry inside the configured warning window
- `INACTIVE`: no active membership and not yet beyond follow-up threshold
- `TO_FOLLOW_UP`: inactive beyond follow-up threshold

Recommended action rules:
- `VERIFY_CONTRIBUTION` for expiring memberships
- `REMIND_MEMBER` for recent inactivity
- `CONTACT_MEMBER` for escalated inactivity

### Screen: Follow-ups

Purpose:
- Show sponsor-network members who have been inactive long enough to deserve human follow-up.

API:
- `GET /api/mobile/network/followups`

Response contract:
- `memberId`
- `name`
- `inactiveDays`
- `phone`

Sorting:
- Highest `inactiveDays` first
- Name as stable tie-breaker

### Screen: Genealogy

Purpose:
- Show the sponsor genealogy tree used for referral lineage.

API:
- `GET /api/mobile/network/tree`
- Default view: `GENEALOGY`

Response contract:
- `view`
- `totalMembers`
- `maxDepth`
- `root`

Tree node contract:
- `accountHolderId`
- `name`
- `phone`
- `avatar`
- `generation`
- `downlineCount`
- `membershipStatus`
- `nextAction`
- `children`

### Screen: Placement View

Purpose:
- Show actual placement structure independently of sponsor lineage.

API:
- `GET /api/mobile/network/tree?view=PLACEMENT`

Behavior:
- Same payload shape as genealogy
- Different parent-child computation based on placement parent instead of sponsor parent

### Screen: Search

Purpose:
- Search the sponsor-network by name or phone.

API:
- `GET /api/mobile/network/search?q=`

Behavior:
- Blank query returns an empty result set
- Matching is case-insensitive
- Search is limited to sponsor-network descendants

### Screen: Earnings

Purpose:
- Expose RMS summary plus underlying commission history.

APIs:
- `GET /api/mobile/earnings/summary`
- `GET /api/mobile/earnings/history`

Summary contract:
- `totalRms`
- `weeklyGrowth`
- `potential`

Summary rules:
- `totalRms`: sum of commissions earned by the beneficiary account holder
- `weeklyGrowth`: sum of commissions created since the start of the current week
- `potential`: `credit_eligibility.max_eligible_amount` when available, otherwise `totalRms * 10`

History contract:
- `amount`
- `type`
- `status`
- `date`

History type mapping:
- `GROUP_BONUS` -> `TEAM_BONUS`
- `SUBSCRIPTION` with generation -> `LEVEL_{generation}_COMMISSION`
- `SUBSCRIPTION` without generation -> `DIRECT_COMMISSION`
- Circle-related source fallback -> `CIRCLE_COMMISSION`
- Any other type -> enum name

### Events

Existing events already sufficient for this mobile module:
- `MlmPlacementCreatedEvent`: network join / placement propagation
- `CommissionGeneratedEvent`: commission notifications
- `MlmVolumeUpdatedEvent`: sponsor/placement volume refresh
- `BonusPaidEvent`: payout-related updates
- `MobileOnboardingEvent`: onboarding workflow milestones

Decision:
- No new domain event was added because the requested mobile read APIs can be satisfied directly from current persisted state, and the repo already emits the core MLM signals needed by notifications and follow-on workflows.

### Error handling

Response envelope:
- New endpoints use `ApiEnvelope`
- Existing MLM endpoints continue using `CommonResponse`

Standard behaviors:
- `401` when the bearer token is missing or invalid
- `404` when the account holder cannot be resolved
- `400` when enum query parameters such as `view` are invalid
- Empty list for valid searches with no matches

### Mobile optimization and anti-N+1 strategy

- Sponsor and placement trees are loaded via recursive CTEs in `MlmMemberLinkRepository`
- Latest memberships are fetched in one bulk query
- Avatars are resolved in bulk through `AppUserRepository` plus `UserProfileRepository`
- Descendant counts are computed in memory from one already-loaded tree result instead of per-member queries
- Search, follow-up, and direct-member views all reuse the same preloaded sponsor-network context per request

## Validation notes

- A tolerant `clean compile` shows the new package integrates without surfacing additional compile errors from the new code paths.
- The repo currently has pre-existing compile/test blockers outside this scope, especially in `PhoneVerificationService` and multiple historical test suites, so a fully green Maven run is not currently possible without fixing unrelated code first.
