# ADR 0023: Android staging timer bridge and lifecycle owner

The existing NativeLifeDirector Capacitor plugin exposes timer({request}). It owns a
TimerBridgeOwner until plugin destruction. The owner serializes requests and filesystem work
on one IO executor, snapshots payloads before crossing threads, releases its journal on close,
and drops queued work after closure. No Activity or WebView reference enters the repository.

API below 26 returns UNSUPPORTED. A missing native journal returns NOT_PREPARED without creating
one. A previously prepared journal supports read-only snapshots. Mutation activation is
hard-disabled in the native owner; web flags cannot enable it. Existing timer commands still
route to JavaScript. No capability advertises native timer authority.

The dedicated migration workflow now compiles an Android APK in addition to JVM tests.
This is a compile check only: it neither uploads an APK nor publishes an OTA or release.

Next integration gates remain: persist the cross-store ownership handshake, register web-side
caller/DTO routing, supply a verified schedule source, disable every legacy focus writer, wire
completion delivery and foreground/resume catch-up, coordinate backup restore, then device-test.
Only then may the activation gate become dynamic. File locking and close are tested on JVM;
these tests are not Android process-death or real-device evidence.
