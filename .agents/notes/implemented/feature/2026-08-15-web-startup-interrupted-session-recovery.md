# Agent Note: Web startup continuation of interrupted sessions

Status: implemented

English | [中文](2026-08-15-web-startup-interrupted-session-recovery.zh.md)

## Problem

Session persistence repairs an open final turn into a logical `turn/end` with reason `interrupted`. A later Web request can cold-resume that session, but no request arrives after a process restart to wake the restored Agent. Replaying the original prompt would append duplicate user input, while an empty ordinary follow-up does not enter the model loop.

## Decision

The Agent interface exposes `resumeInterruptedTurn()`. It requires an idle Agent whose final logical event, ignoring the `session/end-seed` marker, is an interrupted turn. The loop opens a new durable turn and permits its empty first inbox claim only for this explicit wake. The next model request is assembled from the existing session log, so the operation adds no ordinary user message.

`ApiProxyService` exposes the host-only `resumeInterruptedSessions()` runtime helper. When `resumeInterruptedSessions` is enabled, the Web Gateway lists persisted project sessions at startup, inspects their repaired logical views, and cold-resumes each eligible root through `createApiRemoteAgentResolver()`. The resolver composes the preset recorded in the session before publication. Live identities, sessions without a project directory, subagent-owned identities, completed turns, and non-interrupted final outcomes are excluded. One recovery promise per Gateway instance makes repeated startup calls idempotent; a process that fails during recovery leaves an interrupted tail for the next startup to retry.

The generic Gateway configuration defaults `resumeInterruptedSessions` to `false`; the shipped Web composition sets it to `true`. The scan triggers eligible Agents without waiting for their model work to finish, leaving turn execution, provider retries, cancellation, and persistence ownership with AgentLoop and the existing session checkpoint policy.

## Alternatives considered

- Replaying the last user prompt was rejected because the prompt is already durable and would appear twice in model history.
- An unlogged ambient wake was rejected because a recovery request must remain reconstructable from the session log. The explicit turn boundary and existing history satisfy the model-visible/logged invariant without inventing a second prompt.
- Resuming every persisted identity was rejected because subagent sessions belong to their parent delegation lifecycle. Only root sessions are eligible for generic Web recovery.

## Consequences

A Web process restart can continue a task whose durable tail ended during a model request or tool boundary. If the resumed turn ends with an error or the user had intentionally canceled it, a later startup does not retry it automatically; an explicit prompt remains the recovery action. Closing a browser tab is outside this feature because the Web host and its Agent continue running. Dynamic context producers may append their own model-facing context message during the resumed turn, but the original user message is not duplicated.

## Verification

- `packages/core/agent-loop/tests/resume.spec.ts` proves an interrupted log resumes with one original user message and a completed new turn.
- `packages/host/apiproxy/tests/api-proxy-cold.spec.ts` proves root/interrupted filtering, subagent exclusion, and idempotent triggering.
- `apps/web/tests/smoke-real.e2e.ts` boots two real Web processes over one `DSH_HOME`, kills the first during a held provider request, and verifies the second process completes the task through a local provider.
- [`semantic session checkpoints`](../bug-fix/2026-07-21-semantic-session-checkpoints.md) remains the authority for the durable request and tool-boundary evidence used by repair.
