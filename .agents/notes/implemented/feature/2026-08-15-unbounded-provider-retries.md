# Agent Note: Unbounded retries in shipped provider profiles

Status: implemented

English | [中文](2026-08-15-unbounded-provider-retries.zh.md)

## Problem

The shipped Web and Headless profiles mount `llm-retry` but omit a provider policy on the base DeepSeek row, so the provider resolver supplies a finite two-retry budget. An intermittent endpoint interruption can therefore close a session even when a later request would succeed.

## Decision

The `dsh-base` bundle configures the DeepSeek row with `retryPolicy.mode: always`. The existing bounded exponential backoff remains in force, and retries continue until the request succeeds, the session is cancelled, or the owning runtime is disposed. A later profile or home patch can replace the row with an explicit `normal` policy when a finite request budget is required.

## Alternatives considered

- **Change the `dsh-llm` resolver default to `always`** — rejected for this product change because it would alter every adapter and standalone composition, including dormant `llm-pi-ai` routes and consumers that intentionally rely on a finite default.
- **Use a very large `maxRetries` value** — rejected because it remains finite, exposes a misleading numeric budget, and does not express cancellation-driven completion.
- **Retry inside each provider adapter** — rejected because `llm-retry` already owns failed-step recovery, durable retry events, cancellation, and preservation of discarded partial streams.

## Consequences

- The shipped DeepSeek route retries permanent provider failures as well as transport failures; deployments that need fail-fast behavior must set `mode: normal` explicitly.
- Local backoff remains bounded by the provider defaults, so a prolonged outage schedules attempts no faster than the configured maximum delay while still issuing an unbounded number of requests.
- Each retry is a new provider request and can repeat input-token and provider-cost charges; the existing `llm/retry` events and UI continue to expose the attempts.

## Verification

- `packages/bundle/base/tests/base.spec.ts` asserts the shipped row resolves `retryPolicy.mode: always`.
- `apps/web/tests/smoke-real.e2e.ts` starts the shipped Web composition, injects three transport failures, and requires the fourth request and its recovered response.
