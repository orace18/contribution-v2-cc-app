# Business Prayer Replacement Audit

Date: `2026-04-07`

Scope: replacement-oriented audit of the existing community-related backend, mobile, backoffice, notification, and data-model surfaces before implementing the new `Business Prayer / Prieres pour les affaires` module.

## Audit Verdict

The current community surface is fragmented.

- A small post/feed/CMS module exists under `com.ccapp.community`.
- Circle chat also lives inside `com.ccapp.community`, even though it is really a circles/chat capability.
- Circle activity, progress, and score live separately under `com.ccapp.circlesocial`.
- Circle member social interactions (`ENCOURAGE`, `PRAY`, `ADVISE`) live under `com.ccapp.circles`.
- Mobile already has a separate prayer UI flow, but it is still mocked and not aligned with the new OpenAPI contract.
- Backoffice still exposes a visible `Community` publishing feature wired to the old admin APIs.

For a clean replacement, the new `com.ccapp.prayer` module should replace the old community feed/CMS and become the only source of truth for prayer/community social interactions. Circle chat and circle finance/social scoring should remain separate, but they must be clearly excluded from the replacement scope so teams do not confuse them with the new prayer feed.

## 1. Inventory Of The Current Community Module

### 1.1 Backend feed/CMS module that overlaps directly

Code locations:

- `src/main/java/com/ccapp/community/controller/MobileCommunityController.java`
- `src/main/java/com/ccapp/community/service/CommunityFeedService.java`
- `src/main/java/com/ccapp/community/service/CommunityEngagementService.java`
- `src/main/java/com/ccapp/community/service/CommunityAdminService.java`
- `src/main/java/com/ccapp/community/service/PostCreatedNotificationListener.java`
- `src/main/java/com/ccapp/admin/controller/AdminCommunityController.java`
- `src/main/java/com/ccapp/community/domain/entity/CommunityPost.java`
- `src/main/java/com/ccapp/community/domain/entity/PostLike.java`
- `src/main/java/com/ccapp/community/domain/entity/PostComment.java`
- `src/main/java/com/ccapp/community/repository/CommunityPostRepository.java`
- `src/main/java/com/ccapp/community/repository/PostLikeRepository.java`
- `src/main/java/com/ccapp/community/repository/PostCommentRepository.java`
- `src/main/java/com/ccapp/community/event/PostCreatedEvent.java`

Current endpoints:

| Endpoint | Purpose |
| --- | --- |
| `GET /api/mobile/community/feed` | Returns `featuredPosts` and `posts` |
| `POST /api/mobile/community/posts/{postId}/like` | Adds a like |
| `DELETE /api/mobile/community/posts/{postId}/like` | Removes a like |
| `GET /api/mobile/community/posts/{postId}/comments` | Lists comments |
| `POST /api/mobile/community/posts/{postId}/comments` | Creates a comment |
| `GET /api/admin/community/posts` | Lists admin community posts |
| `POST /api/admin/community/posts` | Creates an admin community post |
| `POST /api/admin/community/posts/{id}/feature` | Marks post as featured |
| `POST /api/admin/community/posts/{id}/unfeature` | Removes featured flag |

Current DTOs:

- `CommunityFeedResponse`
- `CommunityPostResponse`
- `CreateCommunityPostRequest`
- `FeatureCommunityPostRequest`
- `PostCommentRequest`
- `PostCommentResponse`

Current entities/tables:

- `community_posts`
- `post_likes`
- `post_comments`

Current events/listeners:

- `PostCreatedEvent`
- `PostCreatedNotificationListener`

Current scheduled jobs:

- none found inside `com.ccapp.community`

Important observed behavior:

- The feed implementation ignores `communityId` and `visibility` for retrieval and simply returns featured posts first, then all active non-featured posts by recency.
- `CreateCommunityPostRequest` still carries `communityId` and free-form `visibility`, but the only backoffice caller currently hardcodes `visibility: 'PUBLIC'`.
- `CommunityEngagementService` supports threaded comments via `parentCommentId`, while the Business Prayer MVP only needs flat comments.
- The old module models `like`, not `Amen`.

### 1.2 Fragmented package content inside `com.ccapp.community`

These files live in the same package family but are not the same domain as the old feed/CMS:

- `src/main/java/com/ccapp/community/controller/CircleChatController.java`
- `src/main/java/com/ccapp/community/service/CircleChatService.java`
- `src/main/java/com/ccapp/community/domain/entity/CircleChatMessage.java`
- `src/main/java/com/ccapp/community/domain/entity/CircleChatReadReceipt.java`
- `src/main/java/com/ccapp/community/repository/CircleChatMessageRepository.java`
- `src/main/java/com/ccapp/community/repository/CircleChatReadReceiptRepository.java`
- `src/main/java/com/ccapp/community/dto/CircleChatMessageRequest.java`
- `src/main/java/com/ccapp/community/dto/CircleChatMessageResponse.java`
- `src/main/java/com/ccapp/community/dto/CircleChatEvent.java`

Current endpoints:

| Endpoint | Purpose |
| --- | --- |
| `GET /api/mobile/circles/{circleId}/chat` | Cursor-based circle chat history |
| `POST /api/mobile/circles/{circleId}/chat` | Sends circle chat message |
| `POST /api/mobile/circles/{circleId}/chat/{messageId}/read` | Marks a chat message as read |
| `POST /api/mobile/circles/{circleId}/chat/read-all` | Marks chat messages as read |

Related tables:

- `circle_chat_messages`
- `circle_chat_read_receipts`

Events:

- WebSocket `NEW_MESSAGE`
- WebSocket `READ_RECEIPT`
- `MessageSentEvent` publication for notification fanout

Replacement status:

- not replaced by Business Prayer
- should stay separate from the prayer replacement plan
- current package placement is naming debt and a source of confusion

### 1.3 Adjacent circle social/engagement features that overlap conceptually

Code locations:

- `src/main/java/com/ccapp/circles/controller/CircleController.java`
- `src/main/java/com/ccapp/circles/service/CircleMobileQueryService.java`
- `src/main/java/com/ccapp/circles/domain/entity/CircleMemberInteraction.java`
- `src/main/java/com/ccapp/circles/domain/enums/CircleMemberInteractionType.java`
- `src/main/java/com/ccapp/circlesocial/service/CircleActivityService.java`
- `src/main/java/com/ccapp/circlesocial/service/CircleProgressService.java`
- `src/main/java/com/ccapp/circlesocial/service/CircleScoreService.java`

Current endpoints:

| Endpoint | Purpose |
| --- | --- |
| `POST /circles/{circleId}/members/{memberAccountHolderId}/interactions` | Creates `ENCOURAGE`, `PRAY`, or `ADVISE` interaction |
| `GET /circles/{circleId}/activity` | Circle social activity feed |
| `GET /circles/{circleId}/progress` | Circle progress |
| `GET /circles/{circleId}/score` | Circle score |

Related tables:

- `circle_member_interactions`
- `circle_activity`
- `circle_progress`
- `circle_scores`

Observed overlap:

- `CircleMemberInteractionType.PRAY` is conceptually close to the new `support / I prayed for this person` action.
- `CircleMemberInteractionType.ENCOURAGE` is conceptually close to prayer comments/encouragement.
- The interaction endpoint is not currently used by the mobile app, which makes it a strong deprecation candidate if the new prayer module becomes the source of truth.

### 1.4 Notification surfaces that overlap

Code locations:

- `src/main/java/com/ccapp/notifications/domain/enums/NotificationType.java`
- `src/main/java/com/ccapp/notifications/service/NotificationService.java`
- `src/main/java/com/ccapp/notifications/domain/entity/Notification.java`
- `src/main/java/com/ccapp/notifications/controller/NotificationController.java`
- `src/main/resources/db/migration/V14__notifications_module.sql`
- `src/main/resources/db/migration/V88__notifications_read_at_and_new_types.sql`

Current relevant notification types:

- `PRAYER_REMINDER`
- `CIRCLE_PRAYED`
- `TASK_AVAILABLE`
- `TASK_COMPLETED`
- `AGENT_ACTION`
- `CHAT_MESSAGE`

Observed overlap:

- `PRAYER_REMINDER` is reusable for deep-linking to prayer day, but the new spec recommends clearer prayer-specific event names.
- `CIRCLE_PRAYED` is legacy and semantically too circle-centric for the new Business Prayer support model.
- `PostCreatedNotificationListener` currently emits old community publication notifications through `AGENT_ACTION` with template `COMMUNITY_POST_CREATED`, which should not survive the replacement.
- The backend notification entity does not carry first-class deep-link metadata, while the mobile app notification model already expects optional `targetId`.

### 1.5 Home/dashboard and mobile-facing contract overlap

Backend locations:

- `src/main/java/com/ccapp/mobile/home/service/MobileHomeService.java`
- `src/main/java/com/ccapp/mobile/home/dto/MobileHomeResponse.java`
- `src/main/java/com/ccapp/mobile/home/dto/MobileHomeCommunityHighlightResponse.java`
- `src/main/java/com/ccapp/mobile/home/service/HomeFacadeService.java`
- `src/main/java/com/ccapp/mobile/home/dto/MobileHomeDashboardResponse.java`
- `src/main/java/com/ccapp/mobile/home/controller/MobileHomeController.java`

Observed backend state:

- `MobileHomeService` still builds `communityHighlights` from featured community posts.
- The active `MobileHomeController` uses `HomeFacadeService`, not `MobileHomeService`.
- `HomeFacadeService` returns the newer `MobileHomeDashboardResponse` and no longer includes `communityHighlights`.
- `HomeFacadeService.calculateStreakDays` computes streak from `user_tasks.completed_at`, which is not prayer-specific.

Mobile locations:

- `cc-app-v2/lib/features/dashboard/data/repositories/dashboard_repository_impl.dart`
- `cc-app-v2/lib/features/dashboard/presentation/controllers/dashboard_controller.dart`
- `cc-app-v2/lib/features/dashboard/domain/entities/community_post.dart`
- `cc-app-v2/lib/features/dashboard/domain/entities/dashboard_home_data.dart`
- `cc-app-v2/lib/features/dashboard/presentation/widgets/community_tab.dart`
- `cc-app-v2/lib/features/prayer/**`
- `cc-app-v2/lib/features/notifications/notification_routes.dart`

Observed mobile state:

- The dashboard repository still parses `communityHighlights` from `/api/mobile/home`.
- The dashboard repository still contains mock `CommunityPost` fallback data.
- `DashboardController` still holds a `posts` collection fed by `communityHighlights`.
- The mobile prayer feature already exists, but `PrayerRepositoryImpl` is fully mocked and its placeholder endpoints do not fully match the new OpenAPI contract.
- The `CommunityTab` already renders `PrayerCommunityContent`, which is a good migration anchor, but the naming still carries old `community` vocabulary.

### 1.6 Backoffice publishing surfaces that overlap

Code locations:

- `cc-app-backoffice/src/app/features/community/services/community.service.ts`
- `cc-app-backoffice/src/app/features/community/community-list-page.ts`
- `cc-app-backoffice/src/app/features/community/community-create-page.ts`
- `cc-app-backoffice/src/app/app.routes.ts`
- `cc-app-backoffice/src/app/shared/components/sidebar/sidebar.ts`

Observed behavior:

- Backoffice exposes a visible `Community` menu entry and routes `/community` and `/community/create`.
- The backoffice service calls the old admin endpoints under `/api/admin/community/posts`.
- The create form only supports `title`, `content`, and `mediaUrl`.
- The backoffice create flow hardcodes `visibility: 'PUBLIC'`.
- Featured/unfeatured editorial actions are first-class in the old backoffice UI.

## 2. Overlap Matrix

| Old surface | New Business Prayer surface | Action |
| --- | --- | --- |
| `GET /api/mobile/community/feed` | `GET /api/mobile/prayers/feed` | replace, then deprecate and delete old route |
| `community_posts` + `CommunityPost` | `prayer_posts` + `PrayerPost` | do not reuse; retire old table/entity after cutover |
| `POST /api/mobile/community/posts/{postId}/like` | `PUT /api/mobile/prayers/posts/{postId}/amen` | replace; do not alias `like` long-term |
| `DELETE /api/mobile/community/posts/{postId}/like` | no MVP removal endpoint | deprecate and delete |
| `post_likes` | `prayer_amens` | do not reuse; semantics differ |
| `GET/POST /api/mobile/community/posts/{postId}/comments` | `GET/POST /api/mobile/prayers/posts/{postId}/comments` | replace; keep prayer comments flat in MVP |
| `post_comments` | `prayer_comments` | do not reuse automatically; old model supports threaded replies |
| Admin `POST /api/admin/community/posts` | Admin prayer official posts CRUD/publish/archive | replace |
| Admin `feature/unfeature` flow | no featured official-post concept in MVP | deprecate and delete unless product explicitly reintroduces pinning |
| `MobileHomeResponse.communityHighlights` | prayer feed and prayer dashboard | delete old home-field expectations; do not carry forward |
| Mobile `CommunityPost` fallback/mock data | real prayer feed data | delete |
| `CircleMemberInteractionType.PRAY` | prayer `support` action | deprecate; do not keep two competing pray semantics |
| `CircleMemberInteractionType.ENCOURAGE` | prayer comments | deprecate if prayer becomes source of truth for encouragement actions |
| `CircleMemberInteractionType.ADVISE` | no direct MVP equivalent | keep only if explicitly required for circle-member profile, otherwise deprecate entire endpoint |
| `NotificationType.PRAYER_REMINDER` | prayer daily reminder | reuse or map forward; acceptable to keep temporarily |
| `NotificationType.CIRCLE_PRAYED` | prayer support/comment notifications | deprecate and replace with explicit prayer notification types |
| `NotificationService` + `notifications` tables | new prayer in-app notifications | reuse |
| `mediaUrl` string on `community_posts` | `prayer_post_media_refs` + `media_files` | merge by reusing existing media infrastructure, but retire raw `mediaUrl` model |
| `user_tasks` streak shown on home | prayer-owned streak and mission dashboard | keep generic task engine for non-prayer work, but do not reuse it for prayer streak source of truth |
| `CircleChatController` and circle chat tables | none | keep separate, not replaced |
| `circlesocial` activity/progress/score | none | keep separate, not replaced |

## 3. API Cleanup Plan

### 3.1 Routes to keep

Keep these because they are reusable infrastructure or adjacent capabilities, not the old community feed:

- `/notifications/me`
- `/notifications/me/unread-count`
- `/notifications/me/{notificationId}/read`
- `/notifications/me/read-all`
- `/api/media/**`
- `/api/mobile/home`
- `/api/mobile/circles/{circleId}/chat`
- `/api/mobile/circles/{circleId}/chat/{messageId}/read`
- `/api/mobile/circles/{circleId}/chat/read-all`
- `/circles/{circleId}/activity`
- `/circles/{circleId}/progress`
- `/circles/{circleId}/score`

### 3.2 Routes to deprecate

Deprecate immediately once the Business Prayer mobile and admin contracts are ready:

- `/api/mobile/community/feed`
- `/api/mobile/community/posts/{postId}/like`
- `/api/mobile/community/posts/{postId}/comments`
- `/api/admin/community/posts`
- `/api/admin/community/posts/{id}/feature`
- `/api/admin/community/posts/{id}/unfeature`

Also deprecate, pending product confirmation, because they create semantic overlap:

- `/circles/{circleId}/members/{memberAccountHolderId}/interactions`

### 3.3 Routes to replace

The old feed/CMS should be replaced by:

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
- all `/api/admin/prayers/**` endpoints defined in `docs/openapi/business-prayer.yaml`

### 3.4 Backward-compatibility risks

- Mobile dashboard code still expects `communityHighlights`, even though the active home API no longer serves it.
- The mobile prayer repository currently assumes unofficial placeholder endpoints:
  - `GET /api/mobile/prayers/community`
  - `POST /api/mobile/prayers/{id}/thank`
  - `POST /api/mobile/prayers/{id}/amen`
- Those placeholders do not match the new spec and must not become accidental contracts.
- Backoffice currently points directly at `/api/admin/community/posts`, so admin users will see duplicate publishing concepts if old and new modules are both left visible.
- Older mobile builds may still call old community routes if they ever shipped against them.
- Notification routing on mobile already knows `PRAYER_REMINDER` and `CIRCLE_PRAYED`; changing those types without a mobile update will break deeplinks/categories.

## 4. Domain Cleanup Plan

### 4.1 Reusable existing building blocks

Reuse these as infrastructure:

- `notifications`, `notification_attempts`, `notification_templates`
- `NotificationService`
- `media_files` and `/api/media/**`
- account-holder and delegation access control
- circles membership data for `CIRCLE` visibility resolution
- service-country data for `COMMUNITY` visibility resolution

### 4.2 Entities and tables to retire

Retire after cutover:

- `community_posts`
- `post_likes`
- `post_comments`
- `CommunityPost`
- `PostLike`
- `PostComment`
- `PostCreatedEvent`
- `PostCreatedNotificationListener`

Retire or deprecate because they create overlapping prayer/social semantics:

- `circle_member_interactions`
- `CircleMemberInteractionType.PRAY`
- `CircleMemberInteractionType.ENCOURAGE`

Retire mobile/backoffice contract remnants:

- `MobileHomeResponse.communityHighlights`
- mobile `CommunityPost` dashboard model
- backoffice `features/community/*`

### 4.3 Naming collisions to resolve

- Old admin UI uses `Community`, while new source of truth is `Prayer`.
- Old visibility uses `PUBLIC`, while the new contract uses `GLOBAL`.
- Old table/entity carries `communityId`, while new visibility scope `COMMUNITY` means `serviceCountryCode`.
- Old interaction name is `like`; new interaction name is `Amen`.
- Old notification type `CIRCLE_PRAYED` conflicts with the new post-based support model.
- `com.ccapp.community` contains both feed/CMS and circle chat, which makes `"community"` an unreliable package name.

## 5. Package And Module Cleanup Plan

### 5.1 New module/package layout to use

Use the new module family only:

- `com.ccapp.prayer.controller`
- `com.ccapp.prayer.service`
- `com.ccapp.prayer.domain.entity`
- `com.ccapp.prayer.domain.enums`
- `com.ccapp.prayer.dto`
- `com.ccapp.prayer.repository`
- `com.ccapp.prayer.event`
- `com.ccapp.prayer.scheduler`

Recommended admin/mobile split:

- `com.ccapp.prayer.controller.mobile`
- `com.ccapp.prayer.controller.admin`

### 5.2 Old package names to retire

Retire these as part of the replacement:

- `com.ccapp.community.controller.MobileCommunityController`
- `com.ccapp.community.service.CommunityFeedService`
- `com.ccapp.community.service.CommunityEngagementService`
- `com.ccapp.community.service.CommunityAdminService`
- `com.ccapp.community.dto.Community*`
- `com.ccapp.community.domain.entity.CommunityPost`
- `com.ccapp.community.domain.entity.PostLike`
- `com.ccapp.community.domain.entity.PostComment`
- `com.ccapp.community.event.PostCreatedEvent`

Backoffice package to retire:

- `cc-app-backoffice/src/app/features/community/*`

Mobile cleanup targets:

- `cc-app-v2/lib/features/dashboard/domain/entities/community_post.dart`
- dashboard repository/community highlight parsing tied to the old module

### 5.3 What should not be refactored in the same slice

Do not broad-refactor these during Business Prayer implementation:

- move circle chat out of `com.ccapp.community`
- rename `com.ccapp.circlesocial`
- redesign the generic notifications module
- retrofit prayer streak into the existing progress engine

Those are separate cleanup tasks, not blockers for the replacement.

## 6. Migration Strategy

Recommended strategy: `lightweight migration`

### What this means

- no automated migration of old community likes or comments
- no automated migration of old user-generated post semantics into prayer posts
- optional manual recreation of a very small set of still-relevant admin community posts as official prayer posts
- no attempt to preserve old featured ordering as a first-class concept unless product explicitly wants editorial pinning in prayer

### Why not `no data migration needed`

Because there is an existing admin community publishing flow and currently featured posts may still be considered visible business content. A manual editorial carry-over is safer than an empty replacement launch.

### Why not `complex migration`

Because the old and new models are not equivalent:

- old feed does not enforce visibility
- old `communityId` does not map cleanly to new audience scopes
- old `PUBLIC` visibility does not map directly to new strict enum semantics
- old likes/comments do not align with new `Amen` and `Support`
- old comments are threaded, new MVP comments are flat

### Data-compatibility concerns

- Old `community_posts.visibility` is free-form and normalized to uppercase only.
- Old `community_posts.community_id` is stored but not enforced by feed reads.
- Old feed ranking is `featured` plus recency, not scope priority plus recency.
- Old backoffice only knows `PUBLIC`, not `PRIVATE/CIRCLE/CHURCH/COMMUNITY/GLOBAL`.

## 7. Mobile Contract Impact

### Endpoints the mobile team must stop using

- all `/api/mobile/community/**` routes
- any home payload dependence on `communityHighlights`
- placeholder prayer routes that are not in the OpenAPI spec:
  - `GET /api/mobile/prayers/community`
  - `POST /api/mobile/prayers/{id}/thank`
  - `POST /api/mobile/prayers/{id}/amen` as a non-idempotent shape

### Endpoints that remain stable

- `/api/mobile/home`
- `/notifications/me`
- `/notifications/me/unread-count`
- `/notifications/me/{notificationId}/read`
- `/notifications/me/read-all`
- `/api/media/**`
- `/api/mobile/circles/{circleId}/chat`
- circle listing/member endpoints under `/circles` and `/api/mobile/circles`

### Endpoints that are newly introduced

- the Business Prayer mobile endpoints defined in `docs/openapi/business-prayer.yaml`

### Mobile code cleanup required

- remove dashboard `CommunityPost` fallback content
- remove `communityHighlights` parsing from the dashboard repository
- align `PrayerRepositoryImpl` with the new spec:
  - `GET /api/mobile/prayers/feed`
  - `POST /api/mobile/prayers/requests`
  - `GET /api/mobile/prayers/posts/{postId}`
  - `PUT /api/mobile/prayers/posts/{postId}/amen`
  - `PUT /api/mobile/prayers/posts/{postId}/support`
  - comments, daily prayer, dashboard endpoints
- update notification routing once final prayer notification types are chosen

## 8. Recommended Replacement Strategy

Recommended strategy: `phased replacement`

### Why phased replacement is the best fit

- There are backend, mobile, and backoffice consumers.
- The current community surface is fragmented and partly stale.
- A big-bang switch would make it easy to miss hidden consumers.
- A long coexistence period would create exactly the ambiguity we are trying to avoid.

### Recommended phases

1. Freeze old community scope
   - stop expanding `/api/mobile/community/**`
   - stop expanding `/api/admin/community/posts/**`
   - mark old module as deprecated in backend and backoffice docs

2. Ship the new source of truth
   - implement `com.ccapp.prayer`
   - wire mobile prayer flows to the new contract
   - build backoffice prayer publishing pages

3. Cut traffic over
   - switch backoffice menu/routing from `Community` to prayer publishing
   - remove mobile dependence on `communityHighlights` and any old community routes
   - republish any curated official posts manually if needed

4. Remove the old module
   - delete old community feed/CMS routes, services, DTOs, and entities
   - archive or drop old tables after the agreed deprecation window

### Compatibility-adapter stance

Do not create a long-lived server-side adapter that maps `/api/mobile/community/**` to the new prayer APIs. The semantics are too different and that adapter would preserve the old ambiguity.

## 9. Explicit List Of Things That Must Be Removed Or Deprecated

Must be deprecated first, then removed:

- `GET /api/mobile/community/feed`
- `POST /api/mobile/community/posts/{postId}/like`
- `DELETE /api/mobile/community/posts/{postId}/like`
- `GET /api/mobile/community/posts/{postId}/comments`
- `POST /api/mobile/community/posts/{postId}/comments`
- `GET /api/admin/community/posts`
- `POST /api/admin/community/posts`
- `POST /api/admin/community/posts/{id}/feature`
- `POST /api/admin/community/posts/{id}/unfeature`
- backoffice routes `/community` and `/community/create`
- backoffice sidebar item `Community`

Must be removed from active mobile contracts:

- `communityHighlights` from dashboard/home assumptions
- dashboard mock `CommunityPost` data
- old community-post parsing logic
- unofficial prayer placeholder endpoints that do not match the OpenAPI spec

Must be retired from the domain model:

- `community_posts`
- `post_likes`
- `post_comments`
- `CommunityPost`
- `PostLike`
- `PostComment`
- `PostCreatedEvent`
- `PostCreatedNotificationListener`
- old `COMMUNITY_POST_CREATED` notification template usage

Must be deprecated to avoid semantic overlap:

- `NotificationType.CIRCLE_PRAYED`
- `CircleMemberInteractionType.PRAY`
- `CircleMemberInteractionType.ENCOURAGE`
- `/circles/{circleId}/members/{memberAccountHolderId}/interactions` unless product explicitly confirms it stays as a separate non-prayer feature

## 10. Risks And Open Questions

- Hidden consumers: confirm whether any released mobile build or external client still calls `/api/mobile/community/**`.
- Notification contract gap: backend notifications do not currently expose first-class deep-link metadata, but the mobile notification model already has `targetId`.
- Notification naming: decide whether to keep `PRAYER_REMINDER` temporarily for compatibility or replace it with `PRAYER_DAILY_REMINDER`.
- Support notification naming: decide whether `CIRCLE_PRAYED` is already used by campaigns/push and how it should be replaced.
- Editorial carry-over: decide whether any existing featured community posts should be manually recreated as official prayer posts before cutover.
- UI naming: decide whether the mobile/backoffice label should stay `Community` in some places or move fully to prayer-centered wording.
- Church scope: `CHURCH` remains guarded in the new spec until a real church identity source exists.
- Circle-member interactions: decide whether `ADVISE` remains a distinct circle feature or the whole interactions endpoint should be retired.
- Package naming debt: circle chat living under `com.ccapp.community` should not block the replacement, but it should be tracked as a separate cleanup ticket.

## 11. Final Recommendation Table

| Existing artifact | Keep / Reuse / Rename / Deprecate / Delete | Reason | Replacement target |
| --- | --- | --- | --- |
| `GET /api/mobile/community/feed` | Deprecate | Old community feed conflicts directly with the new prayer feed source of truth. | `GET /api/mobile/prayers/feed` |
| `POST /api/mobile/community/posts/{postId}/like` | Deprecate | `like` semantics do not match the new `Amen` contract. | `PUT /api/mobile/prayers/posts/{postId}/amen` |
| `DELETE /api/mobile/community/posts/{postId}/like` | Delete | Reaction removal is not part of the Business Prayer MVP. | None in MVP |
| `GET /api/mobile/community/posts/{postId}/comments` | Deprecate | Old community comments overlap with the new prayer post comments contract. | `GET /api/mobile/prayers/posts/{postId}/comments` |
| `POST /api/mobile/community/posts/{postId}/comments` | Deprecate | Old community comments overlap with the new prayer post comments contract. | `POST /api/mobile/prayers/posts/{postId}/comments` |
| `GET /api/admin/community/posts` | Deprecate | Old admin community publishing would create two official content systems. | `GET /api/admin/prayers/posts` |
| `POST /api/admin/community/posts` | Deprecate | Old admin community publishing would create two official content systems. | `POST /api/admin/prayers/posts` |
| `POST /api/admin/community/posts/{id}/feature` | Delete | Featured editorial logic is not part of the new MVP contract. | None in MVP |
| `POST /api/admin/community/posts/{id}/unfeature` | Delete | Featured editorial logic is not part of the new MVP contract. | None in MVP |
| `community_posts` | Delete | Old post model does not enforce the new visibility and feed rules. | `prayer_posts` |
| `post_likes` | Delete | Old likes do not match unique `Amen` semantics. | `prayer_amens` |
| `post_comments` | Delete | Old comments support reply trees, while the MVP prayer model is flat. | `prayer_comments` |
| `CommunityPost` / `CommunityPostResponse` | Delete | Old domain vocabulary and payload shape conflict with the new prayer model. | `PrayerPost` and prayer DTOs |
| `CreateCommunityPostRequest` | Delete | Old request shape uses `PUBLIC`, `communityId`, and a legacy post model. | Admin prayer post create request |
| `PostCreatedEvent` and `PostCreatedNotificationListener` | Delete | Old event flow emits community publication notifications through legacy `AGENT_ACTION` semantics. | Prayer-specific publication and notification events |
| `NotificationService` and `notifications` tables | Reuse | Stable infrastructure already exists for in-app notification persistence and delivery. | Reused by `com.ccapp.prayer` |
| `NotificationType.PRAYER_REMINDER` | Keep | It already exists and can support daily-prayer deeplinks during migration. | Daily prayer reminder notification |
| `NotificationType.CIRCLE_PRAYED` | Deprecate | The name is tied to circle semantics and is too ambiguous for the new prayer support model. | `PRAYER_SUPPORT_RECEIVED` and related prayer types |
| `media_files` and `/api/media/**` | Reuse | Media infrastructure is already presign-ready and should not be rebuilt in the prayer module. | `prayer_post_media_refs` and `prayer_daily_media_refs` |
| `MobileHomeResponse.communityHighlights` | Delete | This is a stale home contract and should not survive the prayer replacement. | Prayer feed and prayer dashboard endpoints |
| `MobileHomeService` | Delete | It is legacy home composition and is already bypassed by the active controller path. | `HomeFacadeService` plus prayer APIs |
| Mobile dashboard `CommunityPost` model and fallback mock posts | Delete | The mobile app should not keep a second legacy social content model once prayer becomes the source of truth. | Prayer feed models from `features/prayer` |
| Mobile `PrayerRepositoryImpl` placeholder endpoints | Rename | The prayer feature should align to the new OpenAPI contract instead of keeping ad hoc placeholder routes. | `docs/openapi/business-prayer.yaml` endpoints |
| Backoffice `features/community/*` | Deprecate | The old admin UI would keep the obsolete community publishing flow visible. | New prayer backoffice feature |
| Backoffice sidebar label `Community` | Rename | The label should reflect the new source of truth and reduce admin ambiguity. | `Prayer` or `Business Prayer` |
| `CircleChatController` and `CircleChatService` | Keep | Circle chat is adjacent but not replaced by Business Prayer. | No replacement |
| `circle_chat_messages` and `circle_chat_read_receipts` | Keep | These tables belong to chat, not the old feed/CMS. | No replacement |
| `/circles/{circleId}/members/{memberAccountHolderId}/interactions` | Deprecate | `PRAY` and `ENCOURAGE` overlap with the new prayer interaction model. | Prayer support/comments endpoints, or none if retired |
| `circle_member_interactions` | Deprecate | The table carries overlapping prayer-social semantics and is not used by the current mobile app. | Prayer support/comments tables, or none if retired |
| `com.ccapp.circlesocial` activity/progress/score surfaces | Keep | These are circle engagement features, not the old community wall. | No replacement |
| `HomeFacadeService.calculateStreakDays` task streak | Keep | It should remain a non-prayer engagement signal and must not become the prayer streak source of truth. | Prayer streak stays in `com.ccapp.prayer` |
