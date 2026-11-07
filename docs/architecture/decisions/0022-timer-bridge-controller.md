# ADR 0022: Typed timer bridge controller

Status: JVM-tested bridge-facing handler; not registered with Capacitor or active authority.

TimerEndpoint validates protocol, epoch, sequence, timestamp and command identity before
dispatching to the persisted Kotlin repository. It rejects coerced string numbers and fractional
identifiers, resolves eligibility/duration through a supplied schedule provider, and checks a
native-supplied activation gate. Caller-provided ownership flags never activate the engine.
Snapshot is read-only. Raw acknowledgement is intentionally not exposed to UI callers; only
the completion-delivery pipeline can acknowledge durable rewards.

Supported controls: start, replace, pause, resume, reset, stop, finish and catchUp. Replayed
start/replace commands recover the original verified target from persisted receipts even if
that activity has since left the schedule. Catch-up credits planned duration once via the outbox.

Integration outstanding: the Android composition root must hold the single journal/endpoint
owner, supply the lifecycle-bound transport and verified schedule projection, and register
Capacitor methods. Activation must additionally verify both web markers and disable all
legacy focus writers. The controller alone does not provide that three-store handshake,
Android lifecycle integration, restore coordination or device acceptance.
