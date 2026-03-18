# Backoffice Country-First UX

## Goal

Simplify the backoffice for operations users with a strict country-first information architecture:

1. `Dashboard`
2. `Countries`
3. `Platform Configuration`

The old mixed `Configuration Center` should disappear as a central entry point.

## Navigation

- `Dashboard`: operational overview only.
- `Countries`: the main entry for any country-specific setup or review.
- `Platform Configuration`: global setup only, never mixed with country setup.

## Countries Page

Purpose: show one clear inventory of country setup progress and the next action.

Content:
- Section `Configured countries`
- Section `Unconfigured countries`
- CTA `Create new country configuration`

Columns:
- `Name`
- `Status`: `NOT_CONFIGURED`, `DRAFT`, `READY`
- `Readiness %`
- `Action`

Action rules:
- `READY` -> `View`
- `DRAFT` -> `Continue`
- `NOT_CONFIGURED` -> `Configure`

Routing rules:
- Row click opens `/countries/{countryCode}`
- `Continue` and `Configure` open `/countries/{countryCode}/setup`

## Country Detail Page

Purpose: give a read-only, confidence-building summary before editing.

Header:
- Country name
- Status
- Readiness
- `Edit`
- `Continue Setup`

Tabs in this order:
- `General`
- `Accounting`
- `Membership`
- `MLM`
- `Financing`
- `Payment`
- `Risk`

Behavior:
- Default mode is read-only
- `Edit` keeps the user on the country context
- `Continue Setup` opens `/countries/{countryCode}/setup`
- The country code must remain visible in the page title, header, or breadcrumb

## Wizard

New canonical route:

`/countries/{countryCode}/setup`

Rules:
- The wizard is always tied to one country
- No wizard entry without selecting a country first
- No global mixed setup screen
- If setup is started from an unconfigured country, initialization happens in that country context only

## Platform Configuration

Purpose: global platform settings only.

Rules:
- Separate menu item: `Platform Configuration`
- Never shown as part of country setup
- Country progress and platform progress should not share the same entry screen

## UX Principles

At every moment the user must know:

1. Where they are
2. What they are configuring
3. What to do next

Design consequences:
- One page, one context
- Prefer primary action over multiple competing actions
- Keep terminology stable across navigation, list actions, page headers, and wizard steps
- Use readiness as guidance, not as a technical diagnostic dump

## Backend Support Added In This Repo

New UX-facing endpoints:
- `GET /admin/country-config/workspace/countries`
- `GET /admin/country-config/workspace/countries/{countryCode}`

These endpoints provide:
- split `configuredCountries` / `unconfiguredCountries`
- country status and readiness percentage
- primary next action for each country
- country detail header data
- ordered tabs for read-only detail rendering
- canonical frontend routes `/countries/{countryCode}` and `/countries/{countryCode}/setup`
