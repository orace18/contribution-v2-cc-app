# Event Ticketing PRD

## 1. Objective

Add a new `events` backend module that:

- creates and manages events
- sells tickets through the existing Store infrastructure
- validates attendee QR codes
- publishes attendance signals for engagement scoring
- reuses the existing Store, wallet, ledger, MLM volume, and compensation flows

This module is intentionally designed as an extension of the current backend, not a replacement of any existing subsystem.

## 2. Architecture Constraints

### Mandatory rules

- do not refactor `identity`
- do not refactor `membership`
- do not refactor `wallet` or `ledger`
- do not refactor the MLM binary engine
- do not create a new commission engine
- do not create a new commerce or payment system
- tickets must be treated as Store products
- compensation must keep using the existing delivery-driven Store lifecycle

### Integration principle

The `events` module owns:

- event lifecycle
- event speakers
- ticket types
- issued tickets
- event scanner access
- QR check-in
- Store product synchronization for ticket products

The existing Store / commerce path still owns:

- checkout
- wallet payment posting
- merchant payout
- DSB distribution
- MLM volume propagation
- refund financial reversals

## 3. Domain Model

### Event

Fields:

- `eventId`
- `title`
- `description`
- `country`
- `startDateTime`
- `endDateTime`
- `locationType`
- `locationAddress`
- `videoLink`
- `organizerAccountHolderId`
- `status`

Status:

- `DRAFT`
- `PUBLISHED`
- `COMPLETED`
- `CANCELLED`

### Event Speaker

Fields:

- `speakerId`
- `eventId`
- `name`
- `title`
- `photo`

### Ticket Type

Fields:

- `ticketTypeId`
- `eventId`
- `name`
- `price`
- `currency`
- `stock`
- `cv`
- `platformCommissionRate`
- `dsbAmount`
- `shopProductId`
- `productSyncStatus`

### Ticket

Fields:

- `ticketId`
- `eventId`
- `orderId`
- `buyerAccountHolderId`
- `ticketTypeId`
- `qrCode`
- `status`
- `checkinTimestamp`

Status:

- `VALID`
- `USED`
- `INVALID`
- `REFUNDED`

## 4. Store Integration

Each ticket type is synchronized into the existing Store asynchronously.

Store mapping:

- product name = `event.title + " - " + ticketType.name`
- product price = `ticketType.price`
- product currency = `ticketType.currency`
- product CV = `ticketType.cv`
- product DSB = `ticketType.dsbAmount`
- merchant = `event.organizerAccountHolderId`
- ticket product semantic type = `EVENT_TICKET` via the `events` module linkage

Implementation path:

1. create or update `shop_products`
2. create or update matching `commerce_products`
3. keep the ticket type linked to the Store product id

### Product deactivation rules

Ticket products are automatically deactivated when:

- ticket stock reaches `0`
- the event end date has passed
- the event is cancelled
- the event is completed

## 5. Ticket Sales

Ticket sales reuse the existing Store order backbone.

Operational model:

- a ticket type exposes its `productId`
- Store checkout remains the transaction backbone
- when a Store order for ticket products becomes `PAID`, the `events` module issues tickets
- ticket issuance decrements stock and generates QR payloads

Guardrails:

- an order containing event tickets cannot mix ticket and non-ticket products
- an order containing event tickets must belong to one event only
- ticket issuance fails if the event is not `PUBLISHED`
- ticket issuance fails if the event has already ended
- ticket issuance fails if stock is insufficient

## 6. Event Lifecycle

### Draft

- organizer creates the event
- organizer adds speakers
- organizer adds ticket types
- ticket products may already be synchronized but remain inactive

### Published

- ticket products become active if stock is available and the event date is still in the future
- users can buy tickets through the existing Store flow
- ticket issuance happens on successful payment

### Completed

- event tickets are considered delivered only when the event is marked `COMPLETED`
- every paid ticket order is moved through the existing Store delivery transition
- the current compensation engine handles:
  - merchant payout
  - DSB distribution
  - MLM volume propagation

### Cancelled

- ticket products are deactivated
- unused tickets become `INVALID`
- check-in is blocked
- no compensation is triggered

## 7. Ticket Lifecycle

### Ticket creation

- ticket is issued on `ShopOrderPaidEvent`
- stock is decremented
- QR payload is generated and stored
- ticket starts as `VALID`

### Ticket use

- scanner sends the QR payload to `/api/events/checkin`
- if valid, ticket becomes `USED`
- `checkinTimestamp` is written
- `MemberEventAttendanceEvent` is published

### Ticket invalidation

Ticket becomes unusable if:

- the QR payload signature is invalid
- the ticket was already used
- the event is cancelled
- the ticket was refunded
- the ticket was explicitly invalidated by event cancellation

### Ticket refund

- existing Store refund flow remains the financial backbone
- refunded Store orders mark issued tickets as `REFUNDED`

## 8. RBAC

The module uses the existing authentication layers plus event-scoped assignments.

### ADMIN_CENTRAL

Represented by existing admin JWT access on `/api/events/**`.

Permissions:

- full access to all event endpoints

### ORGANIZER

Represented by:

- `event.organizerAccountHolderId`
- plus an event-scoped organizer assignment

Permissions:

- create events
- update events
- publish events
- complete events
- cancel events
- create ticket types
- view event statistics
- scan tickets for owned events

### CONTROL_AGENT

Represented by an event-scoped control-agent assignment.

Permissions:

- QR scan only

### USER

Represented by the existing Firebase-authenticated account holder.

Permissions:

- buy tickets through the Store flow
- view own tickets
- browse published events

## 9. API Contracts

### Event management

- `POST /api/events`
- `GET /api/events`
- `GET /api/events/{id}`
- `PATCH /api/events/{id}`
- `POST /api/events/{id}/publish`
- `POST /api/events/{id}/complete`
- `POST /api/events/{id}/cancel`

### Ticket types

- `POST /api/events/{eventId}/tickets`
- `GET /api/events/{eventId}/tickets`

### User tickets

- `GET /api/me/tickets`

### Scanner

- `POST /api/events/checkin`
- `POST /api/events/checkin/batch`

### Statistics

- `GET /api/events/{eventId}/stats`

Response data includes:

- `ticketsSold`
- `revenue`
- `attendance`
- `conversionRate`

### Store dependency

Ticket purchasing itself stays on the existing Store transaction flow and uses the synchronized ticket product ids.

## 10. QR Validation Flow

QR payload structure:

```json
{
  "ticketId": "uuid",
  "eventId": "uuid",
  "buyerId": "uuid",
  "signature": "hmac-signature"
}
```

Validation sequence:

1. parse QR payload
2. verify HMAC signature
3. load ticket
4. confirm payload matches ticket id, event id, and buyer id
5. verify event is not cancelled
6. verify ticket status is still `VALID`
7. mark ticket `USED`
8. write `checkinTimestamp`
9. publish `MemberEventAttendanceEvent`

Possible outcomes:

- `VALID`
- `ALREADY_USED`
- `INVALID`

## 11. Offline Check-in

Backend support:

- batch synchronization endpoint
- check-in attempt persistence for deduplication and audit
- optional client scan id support for idempotent offline uploads

Batch sync behavior:

- repeated `clientScanId` submissions return the already processed outcome
- valid scans still produce a single ticket usage transition

## 12. Compensation Flow

The `events` module does not implement new compensation logic.

Compensation path:

1. ticket products are sold through Store-backed orders
2. tickets are issued on order payment
3. event completion marks ticket orders as delivered
4. current Store settlement runs unchanged
5. existing subsystems handle:
   - merchant payout
   - DSB distribution
   - MLM volume propagation

This keeps delivery as the compensation trigger, exactly like the current Store model.

## 13. Engagement Scoring Integration

When a ticket is successfully used, the module publishes:

- `MemberEventAttendanceEvent`

Payload includes:

- attended event id
- ticket id
- ticket type id
- member account holder id
- check-in timestamp

This event is the integration contract for the engagement scoring engine and avoids embedding scoring logic in the `events` module itself.

## 14. UX Notes

### Organizer UX

- create event in draft
- define speakers
- define ticket tiers
- publish once ticketing is ready
- monitor sales and attendance
- complete event to release compensation

### User UX

- browse published events
- buy ticket products through the Store experience
- retrieve QR tickets from `GET /api/me/tickets`
- present QR for check-in

### Scanner UX

- scan QR online for immediate validation
- collect scans offline and sync later through batch upload

## 15. Final Recommendation

The safest architecture is:

1. keep Store checkout and delivery as the transactional backbone
2. let `events` own event-specific lifecycle and QR logic only
3. synchronize ticket types into Store asynchronously
4. issue tickets on Store payment
5. trigger compensation only on event completion by reusing Store delivery
6. publish attendance events for engagement without embedding a new scoring engine

This satisfies the architecture rule:

- Store stays the commerce backbone
- payments stay in the current orchestration
- MLM stays unchanged
- compensation stays unchanged
- the new capability is a focused `events` module connected to the existing platform
