# Business Prayer Mobile Handover

## What To Use

- Use only `GET|POST|PUT|PATCH /api/mobile/prayers/**` for prayer feed, post detail, interactions, daily prayer, dashboard, and media upload confirmation.
- Use `POST /api/mobile/prayers/media/upload-url` followed by `POST /api/mobile/prayers/media/{mediaId}/confirm` for prayer post media.
- Use the home `prayerSuggestion` block and prayer dashboard data for prayer-related home UX.
- Use prayer notification types plus `targetId` and `deepLink` for navigation.

## What Not To Use

- Do not build new mobile features on `/api/mobile/community/**`.
- Do not use `communityHighlights` as a source of prayer or social feed content.
- Do not rely on `CIRCLE_PRAYED` for new prayer support/comment experiences.

## What Is Deprecated

- `/api/mobile/community/**` is a compatibility adapter only and points to `/api/mobile/prayers/**`.
- `/api/admin/community/posts/**` is a compatibility adapter only and points to `/api/admin/prayers/posts/**`.
- `MobileHomeResponse` and `communityHighlights` are legacy compatibility surfaces only.

## What Is Unavailable For Now

- `CHURCH` scope is still guarded and should not be exposed as an active mobile choice yet.
- Push delivery is not part of this backend handover; notification support is currently in-app/event level.
