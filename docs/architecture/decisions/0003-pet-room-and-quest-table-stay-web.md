# 0003: The pet room and the 3D quest table stay in the WebView until their turn

Status: accepted by the owner. Recorded with the owner's words: "we can definitely keep those in WebView, until we're ready to work on those."

## Context

The rewrite proceeds tab by tab, presentation first, with logic staying in web code until every tab's visuals are native. Two surfaces are hard to reproduce natively: the 3D card table and gym scenes of Quests, and the pet room of Cat. Both depend on WebGL/canvas code in the page (the quests-table, gym3d and pet segments).

## Decision

- Quests (the 3D table) and Cat (the pet room) remain WebView destinations until each is scheduled. The order stays: Log activity, Workout plan, Library, Life Director, Quests, Cat last.
- A native shell and native tabs around them are fine: a web destination closes the previous native tab at once (A23 item 2), and the safety net (`NativeTabs.heal`) treats a view with no registered native tab as web.
- Nothing is rebuilt natively for them until their own tab starts; when it does, a spike decides between a Compose rebuild, a SurfaceView or an embedded WebView, using the screenshot and timing tools that exist by then.

## Consequences

- The WebView stays alive underneath for the whole rewrite; the memory budget of A24/A25 must be measured with it present.
- The web bundle, update path and the DOM-captured shell (A22) stay in service until those two tabs are native; A22 is therefore not a prerequisite for the first tabs.
- Revisit when Life Director is done.
