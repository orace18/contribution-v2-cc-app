# Business Prayer Engineering Specification

This document defines the MVP engineering contract for the new Club Chretien backend module:

- product name: `Prieres pour les affaires`
- technical module name: `Business Prayer`
- recommended package family: `com.ccapp.prayer`

The intent is to ship a stable, modular, OpenAPI-first backend slice without broad refactors of the current monolith.

## 1. Feature Summary

### Product goal

Create a prayer-centered social engagement module where users can:

- publish prayer requests
- consume a unified feed of prayer requests and official platform posts
- say Amen
- say "I prayed for this person"
- comment and encourage
- read the daily prayer
- maintain a daily streak
- progress through daily prayer missions
- receive in-app support and reminder notifications
- open approved internal deep links such as wallet and store from official posts

### Engineering contract

- Backend ownership stays inside a new `com.ccapp.prayer` module.
- Public mobile APIs live under `/api/mobile/prayers`.
- Admin APIs live under `/api/admin/prayers`.
- Success and error envelopes use the existing `CommonResponse` contract.
- Pagination reuses the existing `PageResponse` shape.
- Media upload is not reimplemented. The module only stores references to `media_files` already created through `/api/media`.
- Reminder notifications reuse the existing `notifications` module.
- Visibility resolution reuses existing account-holder, delegation, and circle data where available.
- Daily missions and streak state stay owned by the prayer module. Do not retrofit the current `progress` engine for MVP daily reset logic.
- Feed ranking must remain deterministic and simple: scope priority first, then recency.
- The MVP must not require a broad refactor of identity, wallet, shop, or community modules.

### Existing modules to reuse

- `com.ccapp.common.api.CommonResponse`
- `com.ccapp.common.api.PageResponse`
- `com.ccapp.common.exception.*`
- `com.ccapp.common.events.*`
- `com.ccapp.notifications.service.NotificationService`
- `com.ccapp.media.service.MediaService`
- `com.ccapp.identity.delegation.service.AccessControlService`
- `com.ccapp.circles.*` for active-circle visibility checks
- `com.ccapp.admin.*` for admin authentication and audit logging

### Recommended package layout

- `com.ccapp.prayer.controller`
- `com.ccapp.prayer.service`
- `com.ccapp.prayer.domain.entity`
- `com.ccapp.prayer.domain.enums`
- `com.ccapp.prayer.dto`
- `com.ccapp.prayer.repository`
- `com.ccapp.prayer.event`
- `com.ccapp.prayer.scheduler`

### Explicit observability baseline

Structured logs must exist for at least these actions:

- `prayer_feed_loaded`
- `prayer_post_created`
- `prayer_post_published`
- `prayer_post_interaction_recorded`
- `prayer_comment_created`
- `prayer_daily_loaded`
- `prayer_daily_completed`
- `prayer_dashboard_loaded`
- `prayer_reminder_notification_requested`
- `prayer_reminder_notification_skipped`

Each log should carry, when applicable:

- `traceId`
- `actorUserId`
- `accountHolderId`
- `postId`
- `dailyPrayerId`
- `visibility`
- `scopeContext`
- `notificationType`
- `outcome`

Recommended metrics:

- feed latency and result size
- create-request success/failure count by visibility
- interaction count by type
- unique daily completions per day
- current streak distribution
- reminder notifications requested/sent/failed/skipped

## 2. Business Flow

### Flow A: user feed

1. Mobile client calls `GET /api/mobile/prayers/feed`.
2. Backend resolves viewer context:
   - target account holder
   - actor user
   - active circle membership
   - service country
   - optional church reference if available
3. Backend loads visible published posts:
   - own private posts
   - circle posts for the viewer's active circle
   - church posts if church context exists
   - community posts for the viewer's service country
   - global posts
4. Backend sorts results by scope priority, then `publishedAt DESC`, then `id DESC`.
5. Mobile receives a single mixed feed containing user prayer requests and official posts.

### Flow B: publish prayer request

1. Mobile uploads media through the existing media module if needed.
2. Mobile calls `POST /api/mobile/prayers/requests`.
3. Backend validates content, visibility, and ownership/readiness of referenced media.
4. Backend snapshots visibility context:
   - `circleId` for `CIRCLE`
   - `churchRef` for `CHURCH`
   - `serviceCountryCode` for `COMMUNITY`
5. Backend saves a `REQUEST` post directly as `PUBLISHED`.
6. Feed counters start at zero and the post becomes visible according to its scope.

### Flow C: support another user

1. Viewer opens post detail or uses a feed action.
2. Viewer sends `PUT /amen`, `PUT /support`, or `POST /comments`.
3. Backend enforces visibility and interaction rules.
4. Backend updates denormalized counters on the post.
5. Backend emits notification events when the action is user-facing and non-noisy.
6. Backend reevaluates today's mission progress for the actor.

### Flow D: daily prayer and streak

1. Mobile calls `GET /api/mobile/prayers/daily/current`.
2. Backend returns today's published daily prayer plus whether the viewer already completed it.
3. After the user prays, mobile calls `POST /api/mobile/prayers/daily/current/complete`.
4. Backend writes exactly one completion per account holder per local prayer day.
5. Backend recomputes current streak and longest streak.
6. Backend marks relevant daily missions complete.

### Flow E: official content

1. Admin creates an official post as `DRAFT`.
2. Admin optionally attaches media references and an approved deep link.
3. Admin publishes the post.
4. The post becomes visible in the user feed based on its target visibility scope.
5. Admin creates and publishes daily prayer content on a chosen `prayerDate`.

## 3. MVP Scope

### In scope

- mixed feed of prayer requests and official posts
- create prayer request
- post detail
- Amen reaction
- pray/support reaction
- comments
- daily prayer retrieval
- daily prayer completion
- streak computation
- daily mission tracking
- engagement stats
- in-app notification events
- admin official posts
- admin daily prayer publication
- internal deep-link payloads for official posts
- media references only, using existing media upload flow

### Explicitly out of scope for MVP

- report/abuse workflow
- user-side edit/delete for prayer requests
- threaded comment replies beyond simple flat comments
- reaction removal endpoints
- external social sharing
- audio recording/upload pipeline owned by prayer module
- moderation dashboard for user-generated posts
- personalized reminder preferences UI
- church directory or church membership management
- ranking personalization or ML-based ordering

## 4. Domain Invariants

- Each post has a stable UUID and immutable `postType`.
- `REQUEST` posts are authored on behalf of an `account_holder_id`.
- `OFFICIAL` posts are authored by an `admin_user_id`.
- Only `OFFICIAL` posts can carry a deep link.
- `OFFICIAL` posts may target only `CHURCH`, `COMMUNITY`, or `GLOBAL` in MVP.
- Only `REQUEST` posts can receive a `SUPPORT` reaction.
- `PRIVATE` posts are visible only to the owning account holder and permitted delegates/admin support flows.
- `PRIVATE` posts do not accept external social interactions in MVP.
- `CIRCLE` posts require an active circle at creation time and snapshot `circleId`.
- `CHURCH` posts require a non-null resolved `churchRef`; otherwise request creation/publication is rejected with `CHURCH_SCOPE_NOT_AVAILABLE`.
- `COMMUNITY` posts snapshot `serviceCountryCode`.
- `GLOBAL` posts carry no audience filter.
- Feed ordering is deterministic:
  - own private posts first for the owner only
  - then `CIRCLE`
  - then `CHURCH`
  - then `COMMUNITY`
  - then `GLOBAL`
  - then `publishedAt DESC`
  - then `id DESC`
- Exactly one Amen may exist for a `(postId, accountHolderId)` pair.
- Exactly one Support may exist for a `(postId, accountHolderId)` pair.
- Comment creation is append-only in MVP.
- All referenced media files must be `READY` and belong to the target account holder.
- Post detail and interaction endpoints must not leak inaccessible post existence; invisibility is handled as `404`.
- Daily prayer completion is unique per `(accountHolderId, prayerDate)`.
- Daily mission progress is unique per `(accountHolderId, missionDate, missionCode)`.
- Daily prayer completion is the only action that increments streak.
- Reminder notifications must not be sent after the user has already completed today's daily prayer.

## 5. State Model

### Prayer post lifecycle

| Entity | State | Meaning |
| --- | --- | --- |
| `REQUEST` post | `PUBLISHED` | visible according to scope immediately after creation |
| `REQUEST` post | `ARCHIVED` | hidden from feed and detail for normal users |
| `REQUEST` post | `MODERATED` | hidden by platform action |
| `REQUEST` post | `DELETED` | logical delete, retained for audit |
| `OFFICIAL` post | `DRAFT` | admin-only, not visible to mobile feed |
| `OFFICIAL` post | `PUBLISHED` | visible to matching audience |
| `OFFICIAL` post | `ARCHIVED` | removed from feed, kept for audit |

### Daily prayer lifecycle

| Entity | State | Meaning |
| --- | --- | --- |
| daily prayer | `DRAFT` | editable by admin only |
| daily prayer | `PUBLISHED` | visible on matching `prayerDate`; global-only in MVP |
| daily prayer | `ARCHIVED` | not returned to mobile |

### Mission lifecycle

| Entity | State | Meaning |
| --- | --- | --- |
| daily mission | `PENDING` | mission exists for the day and is not completed |
| daily mission | `COMPLETED` | goal reached |
| daily mission | `EXPIRED` | day passed before completion |

### Notification lifecycle

Reuse existing `notifications.status` values:

- `PENDING`
- `SENT`
- `FAILED`

## 6. Domain Entities

### PrayerPost

Core aggregate for the feed.

Recommended fields:

- `id`
- `postType` = `REQUEST | OFFICIAL`
- `lifecycleStatus`
- `authorAccountHolderId`
- `actorUserId`
- `createdByAdminUserId`
- `title`
- `content`
- `visibility`
- `audienceCircleId`
- `audienceChurchRef`
- `audienceCountryCode`
- `deepLinkRouteKey`
- `deepLinkParamsJson`
- `amenCount`
- `supportCount`
- `commentCount`
- `createdAt`
- `publishedAt`
- `updatedAt`
- `archivedAt`

### PrayerPostMediaRef

Join table between prayer posts and existing `media_files`.

Recommended fields:

- `id`
- `postId`
- `mediaFileId`
- `sortOrder`
- `usageType` = `COVER | INLINE | AUDIO | ATTACHMENT`

### PrayerAmen

Unique Amen reaction.

Recommended fields:

- `id`
- `postId`
- `accountHolderId`
- `actorUserId`
- `createdAt`

### PrayerSupport

Unique "I prayed for this person" reaction for request posts only.

Recommended fields:

- `id`
- `postId`
- `accountHolderId`
- `actorUserId`
- `createdAt`

### PrayerComment

Flat comment entity for encouragement and support text.

Recommended fields:

- `id`
- `postId`
- `accountHolderId`
- `actorUserId`
- `content`
- `clientRequestId`
- `createdAt`

### DailyPrayerContent

Admin-authored daily devotional content.

Recommended fields:

- `id`
- `prayerDate`
- `title`
- `scriptureReference`
- `content`
- `status`
- `createdByAdminUserId`
- `publishedAt`
- `createdAt`
- `updatedAt`

### DailyPrayerMediaRef

Join table between daily prayer content and existing `media_files`.

### DailyPrayerCompletion

One completion per account holder per prayer day.

Recommended fields:

- `id`
- `dailyPrayerId`
- `accountHolderId`
- `actorUserId`
- `prayerDate`
- `timezone`
- `completedAt`

### DailyMissionDefinition

Prayer-owned daily mission templates.

Recommended MVP mission codes:

- `READ_DAILY_PRAYER`
- `SUPPORT_ONE_REQUEST`
- `COMMENT_ONE_POST`

### DailyMissionProgress

Per-user, per-day mission state.

Recommended fields:

- `id`
- `accountHolderId`
- `missionDate`
- `missionCode`
- `status`
- `progressValue`
- `targetValue`
- `completedAt`
- `updatedAt`

## 7. OpenAPI Contract

The canonical OpenAPI contract for this MVP lives in:

- `docs/openapi/business-prayer.yaml`

### Mobile endpoints

- `GET /api/mobile/prayers/feed`
- `POST /api/mobile/prayers/requests`
- `GET /api/mobile/prayers/posts/{postId}`
- `PUT /api/mobile/prayers/posts/{postId}/amen`
- `PUT /api/mobile/prayers/posts/{postId}/support`
- `GET /api/mobile/prayers/posts/{postId}/comments`
- `POST /api/mobile/prayers/posts/{postId}/comments`
- `GET /api/mobile/prayers/daily/current`
- `POST /api/mobile/prayers/daily/current/complete`
- `GET /api/mobile/prayers/dashboard`

### Admin endpoints

- `GET /api/admin/prayers/posts`
- `POST /api/admin/prayers/posts`
- `GET /api/admin/prayers/posts/{postId}`
- `PATCH /api/admin/prayers/posts/{postId}`
- `POST /api/admin/prayers/posts/{postId}/publish`
- `POST /api/admin/prayers/posts/{postId}/archive`
- `GET /api/admin/prayers/daily`
- `POST /api/admin/prayers/daily`
- `GET /api/admin/prayers/daily/{dailyPrayerId}`
- `PATCH /api/admin/prayers/daily/{dailyPrayerId}`
- `POST /api/admin/prayers/daily/{dailyPrayerId}/publish`
- `POST /api/admin/prayers/daily/{dailyPrayerId}/archive`

### Contract rules

- Success payloads return `CommonResponse<T>`.
- Error payloads return `CommonResponse<Void>`.
- Error `message` must be a stable machine-readable code for prayer endpoints.
- List endpoints use the existing `PageResponse<T>` shape where pagination is needed.
- `Amen` and `Support` use idempotent `PUT` endpoints to avoid duplicate client retries.
- `daily/current/complete` is also idempotent for same-day retries and returns the same completion snapshot.

## 8. DB Schema

### Migration strategy

Recommended Flyway migration:

- `V90__prayer_business_mvp.sql`

### Proposed tables

#### `prayer_posts`

- `id UUID PRIMARY KEY`
- `post_type VARCHAR(16) NOT NULL`
- `lifecycle_status VARCHAR(16) NOT NULL`
- `author_account_holder_id UUID NULL REFERENCES account_holders(id)`
- `actor_user_id UUID NULL REFERENCES app_users(id)`
- `created_by_admin_user_id UUID NULL REFERENCES admin_users(id)`
- `title VARCHAR(160) NULL`
- `content TEXT NOT NULL`
- `visibility VARCHAR(16) NOT NULL`
- `audience_circle_id UUID NULL`
- `audience_church_ref VARCHAR(128) NULL`
- `audience_country_code VARCHAR(3) NULL`
- `deep_link_route_key VARCHAR(64) NULL`
- `deep_link_params_json TEXT NULL DEFAULT '{}'`
- `amen_count INTEGER NOT NULL DEFAULT 0`
- `support_count INTEGER NOT NULL DEFAULT 0`
- `comment_count INTEGER NOT NULL DEFAULT 0`
- `client_request_id UUID NULL`
- `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
- `published_at TIMESTAMPTZ NULL`
- `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
- `archived_at TIMESTAMPTZ NULL`

Key constraints:

- check `post_type IN ('REQUEST','OFFICIAL')`
- check `lifecycle_status IN ('DRAFT','PUBLISHED','ARCHIVED','MODERATED','DELETED')`
- check `visibility IN ('PRIVATE','CIRCLE','CHURCH','COMMUNITY','GLOBAL')`
- check authoring combination:
  - `REQUEST` requires `author_account_holder_id`
  - `OFFICIAL` requires `created_by_admin_user_id`
- check deep-link presence only when `post_type = 'OFFICIAL'`
- partial unique index on `(author_account_holder_id, client_request_id)` where `client_request_id IS NOT NULL`

Recommended indexes:

- `(lifecycle_status, published_at DESC)`
- `(visibility, audience_circle_id, published_at DESC)`
- `(visibility, audience_church_ref, published_at DESC)`
- `(visibility, audience_country_code, published_at DESC)`
- `(author_account_holder_id, created_at DESC)`

#### `prayer_post_media_refs`

- `id UUID PRIMARY KEY`
- `post_id UUID NOT NULL REFERENCES prayer_posts(id) ON DELETE CASCADE`
- `media_file_id UUID NOT NULL REFERENCES media_files(id)`
- `usage_type VARCHAR(16) NOT NULL`
- `sort_order INTEGER NOT NULL DEFAULT 0`

Constraints:

- unique `(post_id, media_file_id)`
- check `usage_type IN ('COVER','INLINE','AUDIO','ATTACHMENT')`

#### `prayer_amens`

- `id UUID PRIMARY KEY`
- `post_id UUID NOT NULL REFERENCES prayer_posts(id) ON DELETE CASCADE`
- `account_holder_id UUID NOT NULL REFERENCES account_holders(id)`
- `actor_user_id UUID NULL REFERENCES app_users(id)`
- `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`

Constraints and indexes:

- unique `(post_id, account_holder_id)`
- index `(account_holder_id, created_at DESC)`

#### `prayer_supports`

- same layout as `prayer_amens`
- unique `(post_id, account_holder_id)`
- only valid for request posts at service layer

#### `prayer_comments`

- `id UUID PRIMARY KEY`
- `post_id UUID NOT NULL REFERENCES prayer_posts(id) ON DELETE CASCADE`
- `account_holder_id UUID NOT NULL REFERENCES account_holders(id)`
- `actor_user_id UUID NULL REFERENCES app_users(id)`
- `client_request_id UUID NULL`
- `content VARCHAR(2000) NOT NULL`
- `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`

Constraints and indexes:

- partial unique `(account_holder_id, client_request_id)` where `client_request_id IS NOT NULL`
- index `(post_id, created_at DESC)`

#### `prayer_daily_contents`

- `id UUID PRIMARY KEY`
- `prayer_date DATE NOT NULL`
- `title VARCHAR(160) NULL`
- `scripture_reference VARCHAR(160) NULL`
- `content TEXT NOT NULL`
- `status VARCHAR(16) NOT NULL`
- `created_by_admin_user_id UUID NOT NULL REFERENCES admin_users(id)`
- `published_at TIMESTAMPTZ NULL`
- `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
- `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`

Constraints:

- unique `(prayer_date)`
- check `status IN ('DRAFT','PUBLISHED','ARCHIVED')`

#### `prayer_daily_media_refs`

- `id UUID PRIMARY KEY`
- `daily_prayer_id UUID NOT NULL REFERENCES prayer_daily_contents(id) ON DELETE CASCADE`
- `media_file_id UUID NOT NULL REFERENCES media_files(id)`
- `usage_type VARCHAR(16) NOT NULL`
- `sort_order INTEGER NOT NULL DEFAULT 0`

#### `prayer_daily_completions`

- `id UUID PRIMARY KEY`
- `daily_prayer_id UUID NOT NULL REFERENCES prayer_daily_contents(id)`
- `account_holder_id UUID NOT NULL REFERENCES account_holders(id)`
- `actor_user_id UUID NULL REFERENCES app_users(id)`
- `prayer_date DATE NOT NULL`
- `timezone VARCHAR(64) NOT NULL`
- `completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`

Constraints and indexes:

- unique `(account_holder_id, prayer_date)`
- index `(account_holder_id, completed_at DESC)`

#### `prayer_daily_mission_definitions`

- `id UUID PRIMARY KEY`
- `mission_code VARCHAR(64) NOT NULL`
- `title VARCHAR(160) NOT NULL`
- `description VARCHAR(255) NOT NULL`
- `target_value INTEGER NOT NULL`
- `active BOOLEAN NOT NULL DEFAULT TRUE`
- `sort_order INTEGER NOT NULL DEFAULT 0`

Constraints:

- unique `(mission_code)`

#### `prayer_daily_mission_progress`

- `id UUID PRIMARY KEY`
- `account_holder_id UUID NOT NULL REFERENCES account_holders(id)`
- `mission_date DATE NOT NULL`
- `mission_code VARCHAR(64) NOT NULL`
- `status VARCHAR(16) NOT NULL`
- `progress_value INTEGER NOT NULL DEFAULT 0`
- `target_value INTEGER NOT NULL`
- `completed_at TIMESTAMPTZ NULL`
- `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`

Constraints and indexes:

- unique `(account_holder_id, mission_date, mission_code)`
- check `status IN ('PENDING','COMPLETED','EXPIRED')`
- index `(account_holder_id, mission_date DESC)`

### Denormalization rules

- `amen_count`, `support_count`, and `comment_count` are write-through counters on `prayer_posts`.
- `current streak` is derived from `prayer_daily_completions`, not persisted separately in MVP.
- `engagement stats` are query-derived in MVP.

## 9. Security and RBAC

### Mobile access

- All mobile endpoints require authenticated bearer access.
- Read operations require permission to act on the target account holder with `PermissionLevel.VIEW`.
- Write operations require permission to act on the target account holder with `PermissionLevel.TRANSACT`.
- Delegated actors are allowed when existing access-control rules allow them.

### Admin access

- Official post and daily prayer endpoints require authenticated admin bearer access.
- Recommended publish-capable roles:
  - `ROLE_SUPER_ADMIN`
  - `ROLE_COUNTRY_ADMIN`
  - `ROLE_OPERATIONS`
- Recommended read-only support role:
  - `ROLE_SUPPORT`

### Visibility enforcement

- Never return a post the viewer cannot see.
- For detail and interaction endpoints, invisible posts resolve to `404`.
- `CIRCLE` visibility uses the snapped `audienceCircleId` and current membership checks.
- `COMMUNITY` visibility uses snapped `audienceCountryCode` matched to viewer `serviceCountryCode`.
- `CHURCH` visibility uses snapped `audienceChurchRef` when a viewer church context exists.

### Deep-link safety

- Only whitelisted route keys are accepted.
- Route parameters are key-value pairs only.
- No external URLs in MVP.
- No deep link on user-generated request posts.

### Abuse and rate limits

- Apply per-account-holder rate limits on:
  - request creation
  - comment creation
  - repeated interactions on many posts in a short window
- Reuse existing `TooManyRequestsException` and return `429`.

## 10. Notification Events

### In-app events to emit in MVP

- `PRAYER_SUPPORT_RECEIVED`
  - when a viewer records a first support on another user's request
- `PRAYER_COMMENT_RECEIVED`
  - when a viewer comments on another user's visible post
- `PRAYER_REQUEST_PUBLISHED`
  - optional feed fanout event for future use, not required to create notifications in MVP
- `PRAYER_DAILY_REMINDER`
  - scheduled reminder for users who did not complete today's prayer
- `PRAYER_STREAK_MILESTONE`
  - optional in MVP, can remain disabled until milestone UX is confirmed

### Notification types

Recommended additions to `NotificationType`:

- `PRAYER_SUPPORT_RECEIVED`
- `PRAYER_COMMENT_RECEIVED`
- `PRAYER_DAILY_REMINDER`
- `PRAYER_STREAK_MILESTONE`

### MVP anti-noise policy

- No individual notification for each Amen.
- Support notifications are emitted only on first support creation.
- No self-notifications.
- No reminder notification if today's daily prayer is already completed.

### Reminder scheduler policy

- Run on a schedule per operational timezone or grouped timezone batches.
- Default reminder window for MVP: one reminder per local day.
- Skip users with no active push token and no in-app delivery target.
- Persist actual delivery attempts through the existing notifications module.

## 11. Streak and Daily Mission Logic

### Prayer day boundary

- Primary resolver: explicit client timezone parameter if present
- Fallback: timezone resolved from `serviceCountryCode`
- Final fallback: `UTC`

### Streak rule

- A streak day is earned only when `daily/current/complete` succeeds for that local date.
- `daily/current/complete` accepts MVP response types `PRAYED` and `AMEN`.
- If the response payload is omitted for backward compatibility, backend treats the completion as `PRAYED`.
- Multiple completions on the same day do not increment streak again.
- If the user completes on consecutive local dates, streak increments by one.
- If the user skips one or more local dates, current streak resets to one on the next completion.
- `longestStreak` is derived historically from completions.

### MVP daily missions

- `READ_DAILY_PRAYER`
  - target `1`
  - completed by daily completion
- `SUPPORT_ONE_REQUEST`
  - target `1`
  - completed on first support action of the day
- `COMMENT_ONE_POST`
  - target `1`
  - completed on first successful comment of the day

### Mission reset

- Missions are generated lazily for the current local day when dashboard is requested or a tracked action occurs.
- Previous day unfinished missions become `EXPIRED`.
- Completed missions remain queryable historically only if later reporting is added; MVP dashboard returns current day only.

### MVP prayer suggestion model

- Home prayer suggestion stays controlled and curated for MVP. No opaque real-time generative AI is required.
- Source priority:
  - current published daily prayer if it exists and the user has not yet responded for the local date
  - otherwise an active curated suggestion template
- Curated template storage supports:
  - `templateKey`
  - `title`
  - `content`
  - `category`
  - optional flat `tags`
  - `audioStatus`
  - optional `audioUrl`
- Initial categories:
  - `BUSINESS_WISDOM`
  - `PROVISION`
  - `COURAGE`
  - `UNITY`
  - `DISCIPLINE`
  - `GRATITUDE`
- Anti-repetition rule:
  - selection must avoid replaying the same curated template shown on the immediately previous local day when alternatives exist
- MVP relevance hints:
  - financing need biases toward `PROVISION` and `BUSINESS_WISDOM`
  - no circle biases toward `UNITY` and `COURAGE`
  - no active membership biases toward `DISCIPLINE`

## 12. Deep Link Model

### Deep-link payload shape

Recommended canonical payload:

- `routeKey`
- `params`
- `label`

### Allowed route keys for MVP

- `PRAYER_DAY`
- `PRAYER_FEED`
- `WALLET_HOME`
- `WALLET_FUND`
- `STORE_HOME`
- `MEMBERSHIP_PLANS`
- `FINANCING_HOME`
- `NOTIFICATION_CENTER`

### Mapping suggestion for current mobile app

- `PRAYER_DAY` -> `/prayer/day`
- `PRAYER_FEED` -> `/prayer/list`
- `WALLET_HOME` -> `/wallet`
- `WALLET_FUND` -> `/fund-wallet`
- `STORE_HOME` -> `/store`
- `MEMBERSHIP_PLANS` -> `/membership/plans`
- `FINANCING_HOME` -> `/financing`
- `NOTIFICATION_CENTER` -> `/notifications`

### Validation rules

- `routeKey` must be whitelisted.
- `params` must be a flat object of string values.
- Unknown keys are rejected with `PRAYER_DEEP_LINK_INVALID`.
- Deep link payload is optional.

## 13. Test Matrix

| Area | Scenario | Expected result |
| --- | --- | --- |
| migration | apply `V90__prayer_business_mvp.sql` on clean DB | all tables, indexes, and constraints created |
| feed | viewer sees own private request | visible only to owner |
| feed | viewer outside circle queries circle post | post not returned |
| feed | viewer in same service country queries community post | post returned |
| feed | viewer in different country queries community post | post not returned |
| feed | global post and circle post same timestamp | circle post ordered first |
| create request | `CIRCLE` request without active circle | `400 PRAYER_SCOPE_CIRCLE_REQUIRES_ACTIVE_CIRCLE` |
| create request | `CHURCH` request without church context | `400 CHURCH_SCOPE_NOT_AVAILABLE` |
| create request | media ref not owned or not ready | `400 PRAYER_MEDIA_NOT_READY` |
| detail | invisible post requested directly | `404 PRAYER_POST_NOT_FOUND` |
| amen | same user sends Amen twice | second call is idempotent, no duplicate row |
| support | support on official post | `400 PRAYER_SUPPORT_NOT_ALLOWED` |
| support | same user supports twice | second call is idempotent, no duplicate row |
| comments | comment created with duplicate `clientRequestId` retry | same logical comment returned or duplicate prevented |
| comments | comment on invisible post | `404 PRAYER_POST_NOT_FOUND` |
| notifications | support action on another user request | one in-app notification created |
| notifications | Amen action | no support notification created |
| daily | get current daily prayer before publication | `404 PRAYER_DAILY_NOT_FOUND` |
| daily | complete same day twice | one completion row, same streak returned |
| streak | user completes on three consecutive local dates | current streak = 3 |
| streak | user skips a day then completes | current streak resets to 1 |
| missions | daily completion updates mission | `READ_DAILY_PRAYER` completed |
| missions | first support of the day | `SUPPORT_ONE_REQUEST` completed |
| admin posts | support role tries to publish | `403` |
| admin posts | official post with invalid deep link | `400 PRAYER_DEEP_LINK_INVALID` |
| admin daily | second published daily prayer for same date | conflict or validation failure |
| rate limits | spam comment burst | `429` |
| observability | feed load and completion flow | expected structured logs emitted |

## 14. Risks / Open Questions

- `CHURCH` scope currently has no existing identity source in the monolith. Decision required:
  - either add a trusted `churchRef` source before enabling creation
  - or keep the enum in contract and reject `CHURCH` until identity is ready
- `COMMUNITY` can mean either `serviceCountryCode` or a future `communityId`. This spec chooses `serviceCountryCode` for MVP stability.
- Official-post comment policy is included in MVP, but moderation/reporting is not. This can create content hygiene risk.
- Daily reminder preference management is intentionally deferred. Default reminder policy must be accepted by product.
- If media download URLs are required inline in feed responses, signing cost may become noticeable; otherwise clients will need a follow-up media call.
- Feed ranking currently has no featured/pinned official-post override. If product later needs editorial pinning, ranking rules must be extended carefully.
- Reaction removal is out of scope. Product should confirm that one-way Amen/Support is acceptable for MVP.

## 15. Sliced Implementation Plan

### Slice 1: schema and read contract

- Add prayer enums, entities, repositories, and Flyway migration.
- Add admin seed rows for daily mission definitions.
- Implement read-only feed query and post detail.
- Implement visibility resolver for `PRIVATE`, `CIRCLE`, `COMMUNITY`, `GLOBAL`.
- Keep `CHURCH` contract present but guarded if context source is absent.

### Slice 2: user-generated request flow

- Implement `POST /requests`.
- Validate media references and visibility snapshots.
- Add post counters and idempotent create support via `clientRequestId`.
- Add structured create logs.

### Slice 3: interactions and comments

- Implement Amen and Support idempotent writes.
- Implement comment list and create.
- Update counters transactionally.
- Emit support/comment notification events.

### Slice 4: daily prayer engine

- Implement admin daily-prayer CRUD and publish/archive.
- Implement mobile current-daily read and complete endpoints.
- Compute streak from completion history.
- Implement prayer dashboard with today's mission state and engagement stats.

### Slice 5: official content and deep links

- Implement admin official-post CRUD and publish/archive.
- Validate approved deep-link route keys.
- Surface official posts in the mixed feed with same visibility contract.

### Slice 6: reminders, hardening, and observability

- Add reminder scheduler and notification fanout.
- Add rate limits for write-heavy actions.
- Add concurrency tests for unique interactions.
- Add structured logs and high-value metrics.
- Publish Swagger/OpenAPI through Spring annotations matching `docs/openapi/business-prayer.yaml`.
