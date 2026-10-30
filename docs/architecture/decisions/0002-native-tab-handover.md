# ADR 0002: Make-before-break hand-over between native tabs, shared bars

Status: Implemented (Native API 15 to 17). Supersedes the independent open/close behavior of the three native tabs.

## Context

Overview (hybrid shell over the live WebView), Character and Food (Compose bodies over the WebView) each opened and closed themselves from their own wrapper around `navigate()`, each with its own copy of the top and bottom bars. Observed on the owner's phone: the covered web page flashing between tabs, two different headers drawn at once, and the bar highlighting the previous tab.

## Decision

1. A tab you leave closes only after the tab you go to has opened or failed (`NativeTabs.leave` / `flush`, JavaScript). A web destination closes it at once. 2.5 s is the last-resort timeout.
2. All close paths use that hand-over: navigation, the Overview MutationObserver, and the Overview and Character refresh handlers.
3. One shared set of bars (`ChromeHost`, Kotlin). It is shown by whichever native screen currently owns it, is not rebuilt on a tab change, ignores scenes whose page sequence number is older than the one showing (within 3 s), and can be released only by its owner. Showing it hides the Overview's own bars; opening Overview removes it.
4. Character and Food stay composed while hidden and are brought to the front when shown. A hidden Food screen disables its Back handler.
5. The bar scene is captured only after `lmaSyncActive()`, with transitions disabled, with the lit tab in the cache key, and re-published 160 ms after a screen opens.
6. The shipped APK is non-debuggable.

## Consequences

- No gap and no overlap between native tabs in simulation; the same test fails on the old logic (`tests/native-handover.test.js`).
- Hidden screens cost memory (unmeasured).
- The bar is still a DOM capture. This ADR narrows its failure modes; it does not remove it. Retiring it is amendment A22 in `CONTRACT_REVISION_2_PROPOSAL.md`.
- Timing on the phone is unmeasured by the author. The instrument is Settings, Appearance, "Show tab timing".
