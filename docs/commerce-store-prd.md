# Commerce Store PRD

## 1. Objective

Define a `commerce` module for the Store experience that:

- reuses the existing MLM binary tree, CV propagation, commission engine, wallet ledger, and payment orchestration
- does not modify the current MLM calculation logic
- introduces only one new business concept: `Direct Selling Bonus (DSB)`
- settles DSB only after delivery confirmation
- keeps MLM volume propagation delegated to the existing MLM volume engine

This PRD is intentionally aligned with the backend that already exists in `cc-backend-monolitic`.

## 2. Architecture Constraints

### Mandatory constraints

- Do not refactor the MLM engine
- Do not create a new MLM calculation logic
- Do not duplicate sponsor tree, placement tree, volume propagation, or wallet posting mechanisms
- Reuse existing `shop`, `mlm`, `payments`, `wallet`, `ledger`, and `operation` modules

### Design principle

The new `commerce` module is an orchestration layer for Store-specific rules.

It owns:

- merchant storefront semantics
- promo codes
- DSB distribution rules
- Store/mobile APIs

It does not own:

- MLM genealogy computation
- MLM volume propagation
- subscription commission calculation
- wallet ledger math
- payment orchestration

## 3. Existing Services To Reuse

The backend already contains the exact building blocks needed for Store integration.

### MLM tree and sponsor lookup

- `MLMQueryService`
  - file: `src/main/java/com/ccapp/mlm/service/MLMQueryService.java`
  - role: upline, downline, team-size, level queries
- `MlmGraphService`
  - file: `src/main/java/com/ccapp/mlm/graph/MlmGraphService.java`
  - role: sponsor/downline graph traversal and binary tree summaries
- `MlmMemberLinkRepository`
  - file: `src/main/java/com/ccapp/mlm/repository/MlmMemberLinkRepository.java`
  - role: direct sponsor link resolution and sponsor/placement recursive trees
- `MLMPlacementService`
  - file: `src/main/java/com/ccapp/mlm/service/MLMPlacementService.java`
  - role: placement creation during onboarding, must remain unchanged

### MLM volume engine

- `MlmVolumeLedgerService`
  - file: `src/main/java/com/ccapp/mlm/service/MlmVolumeLedgerService.java`
  - role: records delivered-order CV/business volume in placement and sponsor trees
- `ShopOrderDeliveredMlmVolumeListener`
  - file: `src/main/java/com/ccapp/mlm/service/ShopOrderDeliveredMlmVolumeListener.java`
  - role: already listens to delivered shop orders and forwards CV to the MLM volume ledger

Important conclusion:

- the Store must continue publishing a delivered-order event
- the commerce module must not implement its own CV propagation

### Wallet ledger and accounting

- `AccountingPostingService`
  - file: `src/main/java/com/ccapp/ledger/service/AccountingPostingService.java`
  - role: double-entry posting for marketplace payment, merchant payout, wallet deposit, wallet withdrawal, commission payout
- `WalletDebitFacade`
  - file: `src/main/java/com/ccapp/wallet/service/WalletDebitFacade.java`
  - role: secure debits with PIN/OTP/idempotency and posting
- `WalletPaymentService`
  - file: `src/main/java/com/ccapp/wallet/service/WalletPaymentService.java`
  - role: wallet-to-wallet transfer flow
- `WalletDepositService`
  - file: `src/main/java/com/ccapp/wallet/service/WalletDepositService.java`
  - role: PSP and agent wallet credits
- `WalletGuardService`
  - file: `src/main/java/com/ccapp/wallet/service/WalletGuardService.java`
  - role: active wallet validation and balance constraints
- `WalletRepository`
  - file: `src/main/java/com/ccapp/identity/wallet/repository/WalletRepository.java`
  - role: wallet lookup for merchant and beneficiaries

Important conclusion:

- DSB payouts must use the existing operation + accounting + wallet event model
- DSB must not directly mutate balances

### Commission engine

- `MlmCommissionService`
  - file: `src/main/java/com/ccapp/payments/commission/service/MlmCommissionService.java`
  - role: subscription unilevel commission calculation
- `CommissionPayoutBatchProcessor`
  - file: `src/main/java/com/ccapp/payments/commission/service/CommissionPayoutBatchProcessor.java`
  - role: payout of accumulated MLM commissions to wallets

Important conclusion:

- this engine is subscription-oriented and must remain unchanged
- DSB is not a replacement for MLM commissions
- DSB should not force a redesign of `MlmCommissionService`

### Existing Store / marketplace implementation already present

- `ShopOrderService`
  - file: `src/main/java/com/ccapp/shop/service/ShopOrderService.java`
  - role: checkout, payment, delivery settlement, merchant payout, marketplace allocation, refund publishing
- `ShopProductService`
  - file: `src/main/java/com/ccapp/shop/service/ShopProductService.java`
  - role: product CRUD for the marketplace
- `ShopOrderDeliveredEvent`
  - file: `src/main/java/com/ccapp/shop/event/ShopOrderDeliveredEvent.java`
  - role: existing delivered event, already consumed by MLM volume listeners

Important conclusion:

- the new `commerce` module should be implemented as an extension/facade around the existing `shop` lifecycle
- do not create a second disconnected order pipeline

## 4. Existing APIs Already Present

### Store / marketplace APIs

- `GET /shop/products`
- `GET /shop/products/{productId}`
- `POST /shop/products`
- `POST /shop/orders/checkout`
- `GET /shop/orders/mine`
- `GET /shop/orders/{orderId}`
- `PATCH /shop/orders/{orderId}/status`
- `POST /shop/orders/{orderId}/refund`

### MLM APIs

- `POST /mlm/placements`
- `GET /mlm/{accountHolderId}/upline`
- `GET /mlm/{accountHolderId}/downline`
- `GET /mlm/{accountHolderId}/team-size`
- `GET /mlm/{accountHolderId}/levels/{level}/members`

## 5. Target Commerce Module

## 5.1 Module responsibility

Create a new package family `com.ccapp.commerce` that acts as a Store domain layer.

Recommended packages:

- `com.ccapp.commerce.controller`
- `com.ccapp.commerce.service`
- `com.ccapp.commerce.domain.entity`
- `com.ccapp.commerce.domain.enums`
- `com.ccapp.commerce.dto`
- `com.ccapp.commerce.repository`
- `com.ccapp.commerce.event`

## 5.2 Core domain objects

### Merchant

Purpose:

- represent a sellable storefront tied to an existing account holder

Recommended fields:

- `id`
- `accountHolderId`
- `displayName`
- `countryCode`
- `status`
- `logoUrl`
- `createdAt`

Rule:

- merchant wallet remains the existing wallet of the merchant account holder

### Product

Purpose:

- store sellable catalog items

Recommended fields:

- `id`
- `merchantId`
- `name`
- `description`
- `price`
- `currency`
- `cvValue`
- `dsbAmount`
- `active`
- `createdAt`

Rule:

- `cvValue` is a marketing/store attribute only
- effective volume propagation remains delegated to `MlmVolumeLedgerService` after delivery

### Order

Purpose:

- represent Store transactions and their lifecycle

Recommended fields:

- `id`
- `buyerAccountHolderId`
- `merchantId`
- `countryCode`
- `status`
- `grossAmount`
- `discountAmount`
- `netPaidAmount`
- `currency`
- `promoCodeId`
- `deliveryConfirmedAt`
- `createdAt`

Recommendation:

- do not duplicate the current shop order state machine
- either reuse existing `shop_orders` as the persistence backbone, or keep a 1:1 extension table keyed by the same order id

### PromoCode

Purpose:

- allow buyer discount and alternative DSB routing

Fields required by business:

- `id`
- `code`
- `ownerAccountHolderId`
- `discountAmount`
- `country`
- `status`
- `expiryDate`
- `usageLimit`
- `usedCount`
- `createdAt`

Recommended extra fields:

- `minOrderAmount`
- `active`
- `lastUsedAt`

### DSBService

Purpose:

- orchestrate bonus distribution after delivery

Responsibilities:

- validate idempotent order settlement
- detect promo-code path vs sponsor path
- fetch sponsor chain using the existing MLM tree/link model
- create payout operations
- call wallet/accounting services for postings
- publish wallet and DSB events

Non-responsibilities:

- no MLM placement logic
- no MLM volume logic
- no subscription commission logic

## 6. Business Rules

## 6.1 Delivery-driven settlement

The settlement trigger is delivery confirmation.

Trigger:

- existing event: `ShopOrderDeliveredEvent`

Settlement sequence:

1. order becomes `DELIVERED`
2. merchant payout is calculated
3. DSB is distributed
4. order CV is sent to the existing MLM volume engine
5. settlement completion event is published

## 6.2 DSB calculation

DSB is distributed only after delivery.

### Case A: promo code used

Input:

- `dsbAmount`
- `discountAmount`

Formula:

- `promoOwnerReward = dsbAmount - discountAmount`

Rules:

- buyer receives the discount immediately in order pricing
- promo-code owner receives `dsbAmount - discountAmount`
- if result is less than or equal to zero, no payout is created
- MLM sponsor-based DSB distribution is skipped in this case

### Case B: no promo code used

Use the existing sponsor chain only.

Lookup:

- `sponsor1 = getSponsor(buyer)`
- `sponsor2 = getSponsor(sponsor1)`
- `sponsor3 = getSponsor(sponsor2)`

Distribution:

- `G1 = 50%`
- `G2 = 30%`
- `G3 = 20%`

Rules:

- payout only to resolved sponsors
- if one sponsor level is missing, the unpaid share remains in the platform pool unless business later defines a reallocation rule
- no new MLM tree structure may be introduced

## 6.3 Merchant payout

Formula:

- `NetMerchant = SalePrice - PlatformCommission - DSB`

Rules:

- merchant payout is executed only after delivery
- merchant payout continues using the existing wallet and accounting flow
- if `NetMerchant <= 0`, no merchant payout operation is created and the order is flagged for finance review

## 6.4 MLM volume propagation

When the order is delivered:

- send `product.cvValue` to the existing MLM volume engine
- do not compute sponsor/placement CV in the commerce module
- do not alter binary matching rules

Implementation alignment:

- continue relying on `ShopOrderDeliveredEvent`
- continue relying on `MlmVolumeLedgerService.recordForDeliveredOrder(...)`

## 7. Recommended Technical Integration

## 7.1 Reuse strategy

### Keep existing `shop` lifecycle

Recommended:

- checkout stays on the current marketplace order flow
- order status transitions remain the current `CREATED -> PAID -> SHIPPED -> DELIVERED -> REFUNDED`
- current merchant payout pattern remains the accounting model to follow

### Add `commerce` as orchestration

Recommended orchestration services:

- `CommerceOrderService`
- `PromoCodeService`
- `DSBService`
- `CommerceSettlementService`

Recommended wiring:

- `CommerceOrderService` extends checkout validation and promo-code application
- `CommerceSettlementService` subscribes to or is invoked from delivered order flow
- `DSBService` uses `MlmMemberLinkRepository` or `MLMQueryService` for sponsor resolution
- wallet postings use `OperationService + AccountingPostingService + WalletRepository + WalletCreditedEvent`

## 7.2 New operation types recommended

To preserve ledger traceability without touching MLM logic, add dedicated operation types for DSB flows.

Recommended new `OperationType` values:

- `COMMERCE_DSB_PAYOUT`
- `COMMERCE_DSB_REVERSAL`

Reuse existing operation types where possible:

- keep `MARKETPLACE_PAYMENT` for buyer payment
- keep `MARKETPLACE_MERCHANT_PAYOUT` for merchant net settlement

## 7.3 Recommended accounting posting additions

Add explicit posting methods in `AccountingPostingService` instead of generic or ad hoc ledger writes.

Recommended additions:

- `postCommerceDsbPayout(operation, beneficiaryAccountHolderId, countryCode)`
- `postCommerceDsbReversal(operation, beneficiaryAccountHolderId, countryCode)`

Recommended ledger direction for DSB payout:

- debit `MARKETPLACE_POOL`
- credit beneficiary wallet

This preserves the existing accounting approach used for merchant payouts and commission payouts.

## 8. APIs Needed

All new mobile-facing APIs should return `ApiEnvelope`.

Admin/backoffice APIs may continue using existing `CommonResponse` patterns if they remain under the current controller families.

## 8.1 Mobile APIs

### 1. List products

`GET /api/mobile/commerce/products`

Response:

```json
{
  "success": true,
  "message": "Commerce products",
  "data": {
    "items": [
      {
        "productId": "uuid",
        "merchantId": "uuid",
        "merchantName": "Grace Market",
        "name": "Pack Riz 25kg",
        "description": "Produit alimentaire",
        "price": 25000,
        "currency": "XOF",
        "cvValue": 5,
        "dsbAmount": 3000,
        "active": true
      }
    ]
  }
}
```

### 2. Product details

`GET /api/mobile/commerce/products/{productId}`

### 3. Validate promo code

`POST /api/mobile/commerce/promo-codes/validate`

Request:

```json
{
  "code": "GRACE2026",
  "country": "BJ",
  "cartAmount": 25000
}
```

Response:

```json
{
  "success": true,
  "message": "Promo code validated",
  "data": {
    "valid": true,
    "code": "GRACE2026",
    "discountAmount": 1000,
    "ownerAccountHolderId": "uuid",
    "expiresAt": "2026-06-30T23:59:59Z"
  }
}
```

### 4. Checkout order

`POST /api/mobile/commerce/orders/checkout`

Request:

```json
{
  "items": [
    {
      "productId": "uuid",
      "quantity": 2
    }
  ],
  "deliveryAddressId": "uuid",
  "paymentMethod": "WALLET",
  "promoCode": "GRACE2026"
}
```

Response:

```json
{
  "success": true,
  "message": "Commerce order created",
  "data": {
    "orderId": "uuid",
    "status": "CREATED",
    "grossAmount": 50000,
    "discountAmount": 1000,
    "netPaidAmount": 49000,
    "currency": "XOF",
    "promoCodeApplied": true
  }
}
```

### 5. List buyer orders

`GET /api/mobile/commerce/orders/mine`

### 6. Get order details

`GET /api/mobile/commerce/orders/{orderId}`

Response should include settlement-facing data for mobile transparency:

- order status
- merchant name
- promo discount
- gross amount
- net paid amount
- DSB amount
- delivery timestamps

### 7. Order settlement details

`GET /api/mobile/commerce/orders/{orderId}/settlement`

Response:

```json
{
  "success": true,
  "message": "Commerce settlement",
  "data": {
    "orderId": "uuid",
    "status": "DELIVERED",
    "grossAmount": 25000,
    "platformCommission": 2000,
    "dsbAmount": 3000,
    "merchantNetAmount": 20000,
    "volumeCv": 5,
    "distributionMode": "PROMO_CODE",
    "beneficiaries": [
      {
        "accountHolderId": "uuid",
        "label": "PROMO_OWNER",
        "amount": 2000,
        "currency": "XOF"
      }
    ]
  }
}
```

## 8.2 Merchant / backoffice APIs

Existing APIs already cover a large part of merchant operations:

- `POST /shop/products`
- `PATCH /shop/orders/{orderId}/status`
- `POST /shop/orders/{orderId}/refund`

Recommended additive APIs:

### 1. Create promo code

`POST /api/merchant/commerce/promo-codes`

### 2. List merchant promo codes

`GET /api/merchant/commerce/promo-codes`

### 3. Delivery confirmation

`POST /api/merchant/commerce/orders/{orderId}/confirm-delivery`

Purpose:

- safer semantic endpoint for final settlement trigger
- internally maps to delivered-order transition without renaming current shop APIs

### 4. Merchant settlement history

`GET /api/merchant/commerce/settlements`

## 9. Events

## 9.1 Existing events to keep

- `ShopOrderPaidEvent`
- `ShopOrderDeliveredEvent`
- `ShopOrderRefundedEvent`
- `MarketplaceCommissionAllocatedEvent`
- `WalletCreditedEvent`
- `WalletDebitedEvent`
- `MlmVolumeUpdatedEvent`

## 9.2 Event mapping required by the new Store rules

### Delivery trigger

Business event name:

- `ORDER_DELIVERED`

Backend mapping:

- reuse existing `ShopOrderDeliveredEvent`

No duplicate delivered event should be introduced if the existing event already carries the needed payload.

## 9.3 New commerce events recommended

### PromoCodeAppliedEvent

Published when:

- checkout accepts a promo code

Payload:

- `orderId`
- `promoCodeId`
- `code`
- `buyerAccountHolderId`
- `discountAmount`
- `occurredAt`

### DsbDistributedEvent

Published when:

- DSB settlement succeeds

Payload:

- `orderId`
- `buyerAccountHolderId`
- `distributionMode` (`PROMO_CODE` or `SPONSOR_CHAIN`)
- `totalDsbAmount`
- `currency`
- `beneficiaries[]`
- `occurredAt`

### DsbDistributionFailedEvent

Published when:

- DSB distribution fails after order delivery

Payload:

- `orderId`
- `reason`
- `retryable`
- `occurredAt`

### MerchantPayoutSettledEvent

Published when:

- merchant net payout is posted successfully

Payload:

- `orderId`
- `merchantAccountHolderId`
- `amount`
- `currency`
- `occurredAt`

### PromoCodeRedeemedEvent

Published when:

- promo code usage is finalized after successful delivered settlement

Payload:

- `promoCodeId`
- `orderId`
- `ownerAccountHolderId`
- `discountAmount`
- `occurredAt`

### DsbReversedEvent

Published when:

- a refunded delivered order requires DSB reversal

Payload:

- `orderId`
- `reversedAmount`
- `beneficiaries[]`
- `occurredAt`

## 10. UX Specification

## 10.1 Product catalog

User story:

- a buyer browses products and sees price, merchant, and displayed CV

UI requirements:

- product list
- product details
- merchant name
- displayed CV label
- promo-code entry point in cart

Important note:

- displayed CV is informational in the Store
- actual MLM propagation happens only after delivery

## 10.2 Checkout

User story:

- buyer checks out with wallet payment and optionally applies a promo code

UI elements:

- cart items
- subtotal
- promo-code field
- discount line
- net amount
- wallet payment confirmation
- OTP challenge when required

Success rules:

- order is created first
- payment confirmation marks it `PAID`
- delivery does not happen at checkout

## 10.3 Order tracking

User story:

- buyer follows the order lifecycle from created to delivered

Statuses:

- `CREATED`
- `PAID`
- `SHIPPED`
- `DELIVERED`
- `REFUNDED`

Displayed data:

- order id
- dates
- merchant
- total amount
- discount
- final paid amount
- delivery status

## 10.4 Settlement transparency

User story:

- after delivery, buyer or support can inspect what happened financially

Displayed data:

- merchant net payout
- DSB amount
- promo discount
- MLM volume sent
- settlement status

## 10.5 Merchant UX

User story:

- merchant publishes products, confirms deliveries, and sees settlements

Displayed data:

- active products
- orders awaiting shipment
- orders awaiting delivery confirmation
- settled orders
- merchant payout history
- promo code performance

## 11. State Machines

## 11.1 Order state machine

- `CREATED -> PAID`
- `CREATED -> CANCELLED`
- `PAID -> SHIPPED`
- `PAID -> DELIVERED`
- `SHIPPED -> DELIVERED`
- `DELIVERED -> REFUNDED`

Rule:

- DSB and merchant payout are executed only on `DELIVERED`

## 11.2 PromoCode state machine

- `ACTIVE`
- `DISABLED`
- `EXPIRED`
- `USAGE_LIMIT_REACHED`

Validation rules:

- code must exist
- code must be active
- country must match
- expiry date must be in the future
- usage limit must not be exceeded

## 11.3 DSB settlement state machine

- `PENDING_DELIVERY`
- `READY_TO_SETTLE`
- `SETTLED`
- `FAILED`
- `REVERSED`

Operational rule:

- settlement must be idempotent by `orderId`

## 12. Error Handling

Recommended business errors:

- `PROMO_CODE_NOT_FOUND`
- `PROMO_CODE_EXPIRED`
- `PROMO_CODE_DISABLED`
- `PROMO_CODE_USAGE_LIMIT_REACHED`
- `PROMO_CODE_COUNTRY_MISMATCH`
- `PROMO_CODE_INVALID_FOR_ORDER`
- `ORDER_NOT_FOUND`
- `ORDER_NOT_DELIVERED`
- `ORDER_ALREADY_SETTLED`
- `ORDER_ALREADY_REFUNDED`
- `MERCHANT_WALLET_NOT_FOUND`
- `BENEFICIARY_WALLET_NOT_FOUND`
- `MLM_TREE_NOT_INITIALIZED`
- `SPONSOR_CHAIN_NOT_FOUND`
- `DSB_ALREADY_DISTRIBUTED`
- `DSB_POSTING_FAILED`
- `WALLET_INSUFFICIENT_FUNDS`

API response style:

- mobile APIs use `ApiEnvelope`
- validation and business messages should be short, stable, and machine-friendly

## 13. Non-Goals

The following are explicitly out of scope:

- any modification of the binary MLM engine
- any new CV propagation algorithm
- any new commission rule inside the existing MLM commission engine
- direct wallet balance mutation outside the accounting/operation flow
- replacing current `shop` order lifecycle with a new disconnected engine

## 14. Final Recommendation

The safest implementation path is:

1. keep the current `shop` order lifecycle as the transactional backbone
2. add a new `commerce` module for promo code and DSB orchestration
3. reuse `ShopOrderDeliveredEvent` as the settlement trigger
4. reuse `MlmVolumeLedgerService` unchanged for CV propagation
5. reuse `AccountingPostingService`, `OperationService`, and wallet events for merchant and DSB payouts
6. treat DSB as a Store settlement flow, not as a new MLM commission engine

This approach satisfies the architecture rule:

- Store aligns with the MLM infrastructure
- MLM remains external and unchanged
- DSB is introduced cleanly as an additional commerce-layer reward
