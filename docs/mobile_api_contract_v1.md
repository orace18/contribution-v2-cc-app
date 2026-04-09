# Mobile API Contract v1

## Scope

This document maps the UI prototype `C:\Users\Elie\Downloads\cercle_flow_v3_complet.html` to the current canonical backend of Club Chretien.

Goal:
- map each JSX/HTML screen to real backend APIs
- identify gaps between the prototype and the canonical backend
- propose the final mobile-facing REST contract without changing backend code in this phase

This document is aligned with the current canonical domains already present in backend:
- Circle
- CircleMeeting
- CircleTrustScore
- CircleCoachAssignment
- SeedCapitalEligibility
- CreditApplication
- Loan
- ImfSavingsChannel
- SavingsCycle
- MemberSavingsContribution

## Canon alignment alerts

The prototype still contains a few concepts that are not canonical anymore and must not drive fake backend endpoints:

1. `caisse du cercle`
   The canonical backend does not support a legal circle fund balance.
   UI should migrate toward:
   - savings discipline
   - savings cycles
   - IMF channel readiness
   - seed capital readiness

2. `cotisation tontine`
   The canonical backend now models:
   - personal IMF savings channel
   - circle savings cycle
   - member savings contribution
   It should not revive legacy circle finance endpoints.

3. `retenue sur financement %`
   No canonical backend concept supports this field.
   This should be removed from the mobile flow, not implemented as a new API.

4. `projet soumis` as a separate pre-credit object
   The canonical direction should be:
   - project information folded into `CreditApplication`
   - not a parallel legacy project module

5. `reunion live / call / camera / hand raise`
   Current backend supports meeting records, not live RTC session orchestration.
   This is a future domain, not a hidden existing API.

## UI -> API mapping

| screen | action | data needed | existing API | new API needed | canon note |
|---|---|---|---|---|---|
| Circle list | Load my circles | circle id, name, status, progress, membersCount, unreadMessages, lastActivity | `GET /circles` | Prefer enriching response with `conversationId`, `nextMeetingAt`, `trustScore`, `creditCandidateState` | Existing list is close, but still too thin for chat-first circle cards |
| Circle list | Open one circle | circle summary, chat conversation reference | `GET /circles` + `GET /circles/my-circle` only partially | `GET /circles/{circleId}` or enrich `GET /circles` with `conversationId` | Needed to open circle chat without guessing conversation |
| Circle list | Open notifications | unread count | `GET /notifications/me/unread-count` | None | Existing |
| Circle list | Open search | none | None | None | Pure navigation |
| Circle list | Start create circle from `+` | circle name and initial metadata | `POST /circles` | No new endpoint required | Prototype has no dedicated creation screen, but backend route exists |
| Notifications | List notifications | title, message, deepLink, read state, createdAt | `GET /notifications/me` | None | Existing |
| Notifications | Mark one notification read | notificationId | `PATCH /notifications/me/{notificationId}/read` | None | Existing |
| Notifications | Mark all read | none | `PATCH /notifications/me/read-all` | None | Existing |
| Search | Search circles | query, circle name, membersCount, stage/status | `GET /circles?search=` | None for circles only | Existing only covers circles |
| Search | Search members | query, member name, activity, avatar | None | `GET /directory/search?q=&scopes=members,circles` | Cross-domain search is missing canonically |
| Private chat list | Load direct conversations | conversationId, title, avatar, lastMessage, unreadCount | `GET /chat/conversations` | None | Existing |
| Private chat | Load one conversation messages | conversationId, messages, statuses | `GET /chat/messages?conversationId=...` | None | Existing |
| Private chat | Send direct message | conversationId or recipientAccountHolderId, content, clientMessageId | `POST /chat/messages` | None | Existing |
| Circle chat | Load circle conversation | conversationId, circle summary, messages | `GET /chat/messages?conversationId=...` | `GET /circles/{circleId}` or enrich list response with `conversationId` | Circle conversation exists, but resolution is incomplete for multi-circle UX |
| Circle chat | Send message in circle | conversationId, content | `POST /chat/messages` | None | Existing generic chat can serve circle conversations |
| Circle chat | Show next meeting banner | next meeting | `GET /circles/{circleId}/meetings` | None | Existing |
| Circle dashboard | Load menu/overview | trust score, coach, members, savings gate, credit candidate, recent activity | `GET /circles/{circleId}/trust-score`, `GET /circles/{circleId}/coach-assignment`, `GET /circles/{circleId}/seed-capital-eligibility`, `GET /circles/{circleId}/savings-gate`, `GET /circles/{circleId}/credit-candidate/me`, `GET /circles/{circleId}/activity` | Optional projection only; no mandatory new endpoint | Better to compose from domain endpoints than create a fake screen API |
| Financing - eligibility step | Show if member/circle can request seed capital | member access, readiness state, trust score, savings gate, maxEligibleAmount, blocking reasons | `GET /circles/{circleId}/credit-candidate/me` + `GET /circles/{circleId}/seed-capital-eligibility` | None | Existing canonical bridge already matches this screen |
| Financing - amount step | Prepare request | requestedAmount, durationMonths, optional interestRate | `POST /credit-applications/seed-capital` | Evolve payload with `project` block | Current canonical request exists but does not yet carry project fields |
| Financing - confirmation | Submit application | same as above + circleId | `POST /credit-applications/seed-capital` | Payload evolution recommended | Existing route is canonical seed capital entry |
| Financing - submitted | Show application status | applicationId, workflowState, bridgeState | `GET /credit-applications/{applicationId}`, `GET /credit-applications/me` | None | Existing |
| Renewal - choose plan | current membership snapshot, available plans | `GET /subscriptions/plans` | `GET /me/financial-profile` or `GET /me/membership` | Current backend lacks a canonical member-facing membership status endpoint |
| Renewal - confirm payment | planId, pin/otp, idempotencyKey | `POST /subscriptions/initiate` | None | Existing |
| Renewal - success | updated membership status | `POST /subscriptions/initiate` response, then future `GET /me/financial-profile` | `GET /me/financial-profile` | Better to expose canonical current membership state |
| Reunion | List upcoming/past meetings | meetingAt, status, attendanceCount, quorumReached, notes | `GET /circles/{circleId}/meetings` | None | Existing |
| Reunion | Record meeting | meetingAt, status, present members, notes | `POST /circles/{circleId}/meetings` | None | Existing |
| Reunion live | Join live meeting | live session id, media state, agenda sync | None | Future meeting live-session domain | Out of scope for current backend |
| Score | Show circle trust score | factor scores, global score, network bonus | `GET /circles/{circleId}/trust-score` | None | Use canonical trust score, not legacy `/score` |
| Score | Show detailed eligibility | thresholds, missing criteria | `GET /circles/{circleId}/seed-capital-eligibility` | None | Existing |
| Circle config | Change meeting frequency | frequency policy | None | `PATCH /circles/{circleId}/meeting-policy` | Current backend has no canonical editable meeting cadence policy |
| Circle config | Change contribution amount | expected amount policy | `POST /circles/{circleId}/savings-cycles` partially | No permanent circle config endpoint; use savings cycle creation | Contribution should belong to savings cycles, not circle core |
| Circle config | Change `retenue sur financement %` | retained percentage | None | None | Remove from mobile flow; not canonical |
| Member list | Load members | member profile summary, role, activity, status | `GET /circles/{circleId}/members` | Enrich response with membership expiry + financial access snapshot | Current response is still too light for renewal reminders |
| Member list | Load coach/advisors card | coach, advisors | `GET /circles/{circleId}/participants` or `GET /circles/{circleId}/network-coaching` | None | Existing |
| Member list | Encourage / pray / advise | target member id, interaction type | `POST /circles/{circleId}/members/{memberAccountHolderId}/interactions` | None | Existing |
| Member list | Show member renewal urgency | membership expiry, active/expired status | None | Extend `GET /circles/{circleId}/members` or expose `GET /circles/{circleId}/member-summaries` | Needed for member cards shown in prototype |
| Member profile | Load profile | name, activity, role, status, joinDate | `GET /circles/{circleId}/members/{memberAccountHolderId}` | None | Existing |
| Member profile | Load financial summary | loanReceived, remainingBalance, installmentsPaid, nextInstallmentDate, status | `GET /circles/{circleId}/members/{memberAccountHolderId}/financials` | None | Existing |
| Member profile | Load member history | timeline items with category, status, amount, date | `GET /circles/{circleId}/members/{memberAccountHolderId}/history` | None | Existing |
| Pay flow | Pay repayment | member loans, active loanId, amount due | `GET /loans/me` + `POST /loans/{loanId}/repay` | None | Existing canonical repayment path |
| Pay flow | Pay savings contribution | current open cycle, expected amount, payment initiation, confirmation | `GET /circles/{circleId}/savings-cycles`, `GET /circles/{circleId}/savings-cycles/{cycleId}/contributions`, `POST /circles/{circleId}/savings-cycles/{cycleId}/contributions/me/confirm` | `POST /circles/{circleId}/savings-cycles/{cycleId}/contributions/me/payment-intents` | Confirmation exists; payment initiation is still missing |
| Pay flow | Pay `ma cotisation` | open savings cycle for current member | Same as previous row | UI copy should become `Mon epargne du cycle` | Avoid reviving legacy circle finance wording |
| Pay flow | Pay `cotisation tontine` | open savings cycle contribution | Same as previous row | UI copy should be replaced, not backed by legacy endpoints | Canonical backend uses savings cycle + IMF contribution |
| Project submission | Submit project before credit | project name, sector, description, amount | None as standalone canonical route | Prefer extending `POST /credit-applications/seed-capital` with `project` object | Do not create a parallel legacy project domain if avoidable |

## Missing endpoints and contract gaps

### A. Missing endpoints

1. `GET /directory/search?q=&scopes=circles,members`
   Purpose:
   - unified search for circles and members
   - powers the prototype search screen

2. `GET /circles/{circleId}`
   Purpose:
   - canonical circle detail
   - expose `conversationId`
   - expose basic current summary without forcing mobile to infer from list pages

3. `PATCH /circles/{circleId}/meeting-policy`
   Purpose:
   - edit recurring meeting cadence if this remains a product requirement
   - should only carry social/discipline settings

4. `POST /circles/{circleId}/savings-cycles/{cycleId}/contributions/me/payment-intents`
   Purpose:
   - create a canonical payment intent for a member savings contribution
   - lets mobile initiate wallet/PSP payment before calling contribution confirmation

5. `GET /me/financial-profile`
   Purpose:
   - canonical member-facing snapshot for:
     - membership status
     - membership expiration
     - wallet status
     - financial access state
     - IMF channel status

### B. Existing endpoints that should evolve instead of adding screen-specific APIs

1. `GET /circles`
   Recommended response evolution:
   - add `conversationId`
   - add `nextMeetingAt`
   - add `trustScore`
   - add `creditCandidateState`
   - add `savingsGateState`

2. `GET /circles/{circleId}/members`
   Recommended response evolution:
   - add `membershipStatus`
   - add `membershipExpiresAt`
   - add `financialAccessState`
   - add `remainingBalance`
   - add `nextInstallmentDate`

3. `POST /credit-applications/seed-capital`
   Recommended request evolution:
   - add `projectName`
   - add `projectSector`
   - add `projectDescription`
   - optionally add `useOfFunds`

### C. Prototype features that should not create backend endpoints

1. `retenue sur financement %`
   This should be removed from mobile UX.

2. `caisse du cercle`
   Replace with:
   - savings discipline
   - readiness
   - trust score
   - credit candidate state

3. `live reunion call controls`
   This is a future live session product, not a hidden existing API.

## Recommended mobile API contract

### Common response envelope

All mobile calls currently use the common envelope:

```json
{
  "success": true,
  "message": "Human readable message",
  "data": {},
  "timestamp": "2026-04-09T18:20:00Z"
}
```

## 1. Circles

### Existing

#### `GET /circles?page=0&size=20&search=Esperance`

Purpose:
- list authenticated member circles

Example response:

```json
{
  "success": true,
  "message": "User circles",
  "data": {
    "items": [
      {
        "circleId": "11111111-1111-1111-1111-111111111111",
        "name": "Cercle Esperance",
        "status": "ACTIVE",
        "progress": 72.5,
        "membersCount": 5,
        "role": "MEMBER",
        "unreadMessages": 3,
        "lastActivity": "2026-04-09T14:10:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalItems": 1,
    "totalPages": 1
  },
  "timestamp": "2026-04-09T18:20:00Z"
}
```

#### `GET /circles/my-circle`

Purpose:
- load active circle with members and conversation id

Example response:

```json
{
  "success": true,
  "message": "My active circle",
  "data": {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "Cercle Esperance",
    "coachId": null,
    "status": "ACTIVE",
    "createdAt": "2026-01-10T08:00:00Z",
    "conversationId": "22222222-2222-2222-2222-222222222222",
    "members": []
  },
  "timestamp": "2026-04-09T18:20:00Z"
}
```

#### `GET /circles/{circleId}/members`

Purpose:
- member list

#### `GET /circles/{circleId}/members/{memberAccountHolderId}`

Purpose:
- member profile

#### `GET /circles/{circleId}/members/{memberAccountHolderId}/financials`

Purpose:
- member financial summary

Example response:

```json
{
  "success": true,
  "message": "Circle member financials",
  "data": {
    "loanReceived": 100000,
    "remainingBalance": 40000,
    "installmentsPaid": 3,
    "nextInstallmentDate": "2026-05-15",
    "status": "ACTIVE"
  },
  "timestamp": "2026-04-09T18:20:00Z"
}
```

#### `GET /circles/{circleId}/members/{memberAccountHolderId}/history?page=0&size=20`

Purpose:
- member timeline

#### `POST /circles/{circleId}/members/{memberAccountHolderId}/interactions`

Request:

```json
{
  "type": "ENCOURAGE"
}
```

#### `GET /circles/{circleId}/participants`

Purpose:
- load members, coach, advisors snapshot

### Proposed

#### `GET /circles/{circleId}`

Purpose:
- canonical circle detail for mobile

Suggested response:

```json
{
  "success": true,
  "message": "Circle detail",
  "data": {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "Cercle Esperance",
    "status": "ACTIVE",
    "conversationId": "22222222-2222-2222-2222-222222222222",
    "membersCount": 5,
    "nextMeetingAt": "2026-04-12T14:00:00Z",
    "trustScore": 74.5,
    "savingsGateState": "READY",
    "creditCandidateState": "CANDIDATE_FOR_CREDIT_APPLICATION"
  }
}
```

## 2. Notifications

### Existing

#### `GET /notifications/me`
#### `GET /notifications/me/unread-count`
#### `PATCH /notifications/me/{notificationId}/read`
#### `PATCH /notifications/me/read-all`

Example unread count:

```json
{
  "success": true,
  "message": "Unread count",
  "data": 6,
  "timestamp": "2026-04-09T18:20:00Z"
}
```

## 3. Chat

### Existing

#### `GET /chat/conversations?page=0&size=20`

Example response:

```json
{
  "success": true,
  "message": "Conversations",
  "data": {
    "items": [
      {
        "conversationId": "33333333-3333-3333-3333-333333333333",
        "type": "DIRECT",
        "title": "Coach Eric",
        "avatar": null,
        "lastMessage": "Continue comme ca",
        "lastActivity": "2026-04-09T11:47:00Z",
        "unreadCount": 2
      }
    ]
  }
}
```

#### `POST /chat/conversations`

Request:

```json
{
  "participants": [
    "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
  ]
}
```

#### `GET /chat/messages?conversationId=33333333-3333-3333-3333-333333333333&limit=30`

#### `POST /chat/messages`

Request:

```json
{
  "conversationId": "33333333-3333-3333-3333-333333333333",
  "content": "Merci coach",
  "channel": "TEXT",
  "clientMessageId": "msg-mobile-001"
}
```

#### `PATCH /chat/messages/{messageId}/read`
#### `PATCH /chat/messages/{messageId}/delivered`

### Contract note

For circle chat, mobile should not use deprecated legacy circle chat endpoints.
Use generic chat plus a canonical circle `conversationId`.

## 4. Meetings

### Existing

#### `GET /circles/{circleId}/meetings`
#### `POST /circles/{circleId}/meetings`

Request:

```json
{
  "meetingAt": "2026-04-12T14:00:00Z",
  "status": "COMPLETED",
  "presentMemberAccountHolderIds": [
    "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
  ],
  "notes": "Monthly discipline review"
}
```

Example response:

```json
{
  "success": true,
  "message": "Circle meeting recorded",
  "data": {
    "id": "44444444-4444-4444-4444-444444444444",
    "circleId": "11111111-1111-1111-1111-111111111111",
    "meetingAt": "2026-04-12T14:00:00Z",
    "status": "COMPLETED",
    "participantCount": 5,
    "attendanceCount": 4,
    "quorumReached": true,
    "notes": "Monthly discipline review",
    "createdByAccountHolderId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "presentMemberAccountHolderIds": [],
    "createdAt": "2026-04-12T16:10:00Z",
    "updatedAt": "2026-04-12T16:10:00Z"
  }
}
```

### Future, not current

- live session join
- media controls
- real-time agenda synchronization

## 5. Trust, coaching and readiness

### Existing

#### `GET /circles/{circleId}/coach-assignment`
#### `GET /circles/{circleId}/network-coaching`
#### `GET /circles/{circleId}/trust-score`
#### `GET /circles/{circleId}/seed-capital-eligibility`
#### `GET /circles/{circleId}/credit-candidate/me`

Example trust score:

```json
{
  "success": true,
  "message": "Circle trust score",
  "data": {
    "circleId": "11111111-1111-1111-1111-111111111111",
    "membersCount": 5,
    "financiallyReadyMembersCount": 5,
    "completedMeetingsCount": 3,
    "recentActivityCount": 8,
    "coachAssigned": true,
    "membershipComplianceScore": 35,
    "circleSizeScore": 20,
    "meetingsScore": 25,
    "activityScore": 10,
    "coachScore": 10,
    "networkSignalScore": 60,
    "networkBonusScore": 6,
    "networkAdvisorsCount": 2,
    "networkCoachLevel": 3,
    "globalScore": 88.5,
    "updatedAt": "2026-04-09T18:20:00Z"
  }
}
```

Example credit candidate:

```json
{
  "success": true,
  "message": "Seed capital credit candidate",
  "data": {
    "accountHolderId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "circleId": "11111111-1111-1111-1111-111111111111",
    "candidate": true,
    "state": "CANDIDATE_FOR_CREDIT_APPLICATION",
    "memberAccessState": "ALLOWED",
    "memberAccessReasonCodes": [],
    "readinessState": "READY_FOR_FUTURE_GATES",
    "readinessTrustScore": 88.5,
    "readinessBlockingReasons": [],
    "readinessPendingCriteria": [],
    "savingsGateState": "READY",
    "configuredImfChannelsCount": 5,
    "readyImfChannelsCount": 5,
    "qualifyingSavingsCyclesCount": 2,
    "requiredSavingsCyclesCount": 2,
    "creditEligibilityScore": 74.0,
    "maxEligibleAmount": 500000,
    "evaluatedAt": "2026-04-09T18:20:00Z"
  }
}
```

## 6. IMF savings and savings discipline

### Existing

#### `GET /me/imf-savings-channel`
#### `PUT /me/imf-savings-channel`

Request:

```json
{
  "partnerId": "55555555-5555-5555-5555-555555555555",
  "partnerMemberReference": "IMF-ELIE-001",
  "externalChannelReference": "EXT-CHAN-001",
  "readinessNote": "Configured from mobile"
}
```

#### `GET /circles/{circleId}/savings-cycles`
#### `POST /circles/{circleId}/savings-cycles`

Request:

```json
{
  "windowStart": "2026-04-10T00:00:00Z",
  "windowEnd": "2026-04-17T23:59:59Z",
  "expectedAmountPerMember": 5000,
  "note": "Weekly discipline cycle"
}
```

#### `GET /circles/{circleId}/savings-gate`

Example response:

```json
{
  "success": true,
  "message": "Circle savings gate",
  "data": {
    "circleId": "11111111-1111-1111-1111-111111111111",
    "countryCode": "BJ",
    "state": "READY",
    "membersCount": 5,
    "configuredChannelsCount": 5,
    "readyChannelsCount": 5,
    "qualifyingSavingsCyclesCount": 2,
    "requiredSavingsCyclesCount": 2,
    "evaluatedAt": "2026-04-09T18:20:00Z"
  }
}
```

#### `GET /circles/{circleId}/savings-cycles/{cycleId}/contributions`
#### `POST /circles/{circleId}/savings-cycles/{cycleId}/contributions/me/confirm`

Request:

```json
{
  "confirmedAmount": 5000,
  "partnerReference": "PAY-123",
  "transactionReference": "TX-123",
  "confirmationNote": "Confirmed after IMF callback"
}
```

### Proposed

#### `POST /circles/{circleId}/savings-cycles/{cycleId}/contributions/me/payment-intents`

Purpose:
- initiate canonical payment for a savings contribution

Suggested request:

```json
{
  "amount": 5000,
  "currency": "XOF",
  "provider": "WALLET",
  "idempotencyKey": "sav-cycle-2026-04-10-member-1"
}
```

Suggested response:

```json
{
  "success": true,
  "message": "Savings contribution payment initiated",
  "data": {
    "intentId": "66666666-6666-6666-6666-666666666666",
    "provider": "WALLET",
    "status": "PENDING_CONFIRMATION",
    "operationId": "77777777-7777-7777-7777-777777777777"
  }
}
```

## 7. Credit applications

### Existing

#### `POST /credit-applications/seed-capital`

Current request:

```json
{
  "circleId": "11111111-1111-1111-1111-111111111111",
  "requestedAmount": 100000,
  "requestedDurationMonths": 6,
  "requestedInterestRate": 0
}
```

Example response:

```json
{
  "success": true,
  "message": "Credit application submitted",
  "data": {
    "id": "88888888-8888-8888-8888-888888888888",
    "accountHolderId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "circleId": "11111111-1111-1111-1111-111111111111",
    "productType": "SEED_CAPITAL",
    "bridgeState": "CANDIDATE_FOR_CREDIT_APPLICATION",
    "workflowState": "SUBMITTED",
    "requestedAmount": 100000,
    "currency": "XOF",
    "requestedDurationMonths": 6,
    "requestedInterestRate": 0,
    "readinessState": "READY_FOR_FUTURE_GATES",
    "readinessTrustScore": 88.5,
    "creditEligibilityScore": 74.0,
    "maxEligibleAmount": 500000,
    "savingsGateState": "READY",
    "configuredImfChannelsCount": 5,
    "readyImfChannelsCount": 5,
    "qualifyingSavingsCyclesCount": 2,
    "requiredSavingsCyclesCount": 2,
    "blockingReasons": [],
    "pendingCriteria": [],
    "linkedLoanId": null,
    "submittedAt": "2026-04-09T18:20:00Z",
    "bridgeEvaluatedAt": "2026-04-09T18:19:58Z",
    "createdAt": "2026-04-09T18:20:00Z",
    "updatedAt": "2026-04-09T18:20:00Z"
  }
}
```

#### `GET /credit-applications/me`
#### `GET /credit-applications/{applicationId}`

### Recommended request evolution

To absorb the prototype project screen into the canonical credit workflow, extend the request with a project block:

```json
{
  "circleId": "11111111-1111-1111-1111-111111111111",
  "requestedAmount": 100000,
  "requestedDurationMonths": 6,
  "requestedInterestRate": 0,
  "project": {
    "name": "Boutique de vetements",
    "sector": "Commerce",
    "description": "Achat de stock initial et amenagement",
    "useOfFunds": "Stock + petit equipement"
  }
}
```

## 8. Loans and repayments

### Existing

#### `GET /loans/me`
#### `POST /loans/{loanId}/repay`

Contract note:
- use this only for repayment flow
- do not use legacy direct loan creation as the mobile seed capital entry point

## 9. Membership and wallet

### Existing

#### `GET /subscriptions/plans?countryCode=BJ`
#### `POST /subscriptions/initiate`
#### `GET /wallet/status`
#### `GET /wallet/balance?currency=XOF`
#### `GET /api/mobile/wallet/dashboard`

Example subscription initiation:

```json
{
  "planId": "99999999-9999-9999-9999-999999999999",
  "idempotencyKey": "membership-2026-04-09-001",
  "pin": "12345"
}
```

### Proposed

#### `GET /me/financial-profile`

Purpose:
- canonical member snapshot for mobile

Suggested response:

```json
{
  "success": true,
  "message": "Financial profile",
  "data": {
    "accountHolderId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "membershipStatus": "ACTIVE",
    "membershipExpiresAt": "2026-05-10T23:59:59Z",
    "walletActive": true,
    "walletPinConfigured": true,
    "financialAccessState": "ALLOWED",
    "financialAccessReasonCodes": [],
    "imfChannelStatus": "ACTIVE",
    "imfChannelReady": true
  }
}
```

## 10. Search

### Proposed

#### `GET /directory/search?q=marie&scopes=circles,members`

Suggested response:

```json
{
  "success": true,
  "message": "Directory search",
  "data": {
    "circles": [
      {
        "circleId": "11111111-1111-1111-1111-111111111111",
        "name": "Cercle Esperance",
        "membersCount": 5
      }
    ],
    "members": [
      {
        "accountHolderId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
        "name": "Marie Kouassi",
        "activity": "Couture",
        "avatarUrl": null
      }
    ]
  }
}
```

## Recommended Flutter integration rules

1. Do not call deprecated legacy circle finance endpoints.
2. Do not build circle fund logic on mobile.
3. Use canonical readiness endpoints for financing eligibility:
   - trust score
   - seed capital eligibility
   - credit candidate
   - savings gate
4. Use `CreditApplication` as the only canonical seed capital entry.
5. Use generic chat endpoints for both direct and circle chat.
6. Keep project information inside the credit application workflow, not as a detached legacy feature.

