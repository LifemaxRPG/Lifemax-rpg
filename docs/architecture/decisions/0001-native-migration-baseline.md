# ADR 0001 — Incremental native migration baseline

Status: Final planning decision; implementation pending.

## Accepted

- Fully native is the destination; hybrid is the migration period. Integrate usable slices immediately.
- Compose for new native screen presentation; retain custom native drawing where it is appropriate and accessible.
- MainActivity is the default new primary-tab host, with one navigation coordinator and lifecycle-managed web bridge. Existing CashActivity is grandfathered until separately reviewed.
- Keep one app module initially, manual constructor wiring and com.lifemax.rpg identity.
- Register new authored nested Kotlin/Java and JVM test directories directly through Gradle source sets. Retain legacy copying temporarily; no duplicate source inclusion.
- Static versioned bridge schemas/capabilities in source control, compact runtime capability/version negotiation and explicit freshness/lifecycle rules.
- Keep the core gameplay cluster web-owned during Character UI migration. Native projections are disposable; cut over coupled rules/storage together only after writer coverage and recovery gates pass.
- Start with source inventory and a developer-facing read-only Character slice. Default user rollout requires preserving supported interactions through complete native handling or explicitly recorded working delegation.

## Corrected or rejected from the second Gemini draft

- The old-cached-OTA guard is already present in reviewed LiveWeb.prepare; preserve and test it. Capability/schema compatibility still needs separate handling.
- lastCommittedSerialized plus JSON.parse rollback is not zero-copy or sufficient generic transaction isolation. Staged operation-specific commits are preferred; rollback adapters require proof of their limited scope.
- A fixed 64-receipt/16-KB ring is not an idempotency protocol. Evicted requests need a persistent admission/high-water/expiry mechanism that prevents re-execution, with tested client lifecycle. No new mutation capability ships until this is defined.
- Legacy save writers and time-driven projections cannot all be assumed to advance a new durable revision. The read-only slice uses explicit web-session/projection sequencing and validated invalidation/refetch coverage. Durable epoch/revision rules are a later mandatory mutation gate.
- Single-activity hosting and JVM-clean rules are new-code defaults, not instructions to rewrite all existing plugins or platform-required activities first.
- Read-only is lower-risk, not zero-risk. Hidden-host commands and physical-device smoothness require evidence; they are not guaranteed to take milliseconds or consume nearly zero CPU.
- The backing-key suffix _v1 does not by itself prove an explicit save-schema version field. Store schema facts require source verification.

## Deferred behind explicit gates

- Exact Character projection fields and semantic actions: finalize after C0 inventory.
- Complete mutation wire catalog, canonical payload equivalence, receipt capacity/admission scheme and pending-journal implementation: finalize before the first write capability, with adversarial tests.
- Native gameplay persistence engine: choose from actual query, size, transaction, backup and recovery requirements before native ownership.
- Pet/schedule background execution and whole-cluster cutover details: audit when porting those rules; do not create an always-running service preemptively.
- Gradle module splits or DI framework adoption: require a demonstrated boundary/build/maintenance benefit.

## Required evidence before changing authority

Writer/side-effect inventory; tested equivalence; validated import/export; interrupted-cutover recovery; one authoritative commit path; stale-client/backup/reader compatibility; and an explicitly safe or disabled Classic fallback. An appealing directory tree or a count of migrated screens is not evidence.
