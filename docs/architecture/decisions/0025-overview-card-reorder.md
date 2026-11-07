# ADR 0025: Direct Overview card reorder

Long-press dragging operates on the normal keyed LazyColumn cards. Local presentation state
owns pointer position and hover order; OverviewEditController remains the saved-layout owner.
Only drop sends a full layout command. Visible slots are merged into the full saved order,
preserving hidden/unavailable card slots. Save refusal follows the existing controller rollback.
Cancelled gestures restore the original local order and do not persist.

The card lifts with scale/shadow, neighbors use lazy item placement motion, and dropping eases
the translation and lift back to zero. Reduced motion omits decorative lift and placement motion.
Edge scrolling uses the list's existing scroll state. A held item is pinned while moving.
Tab-swipe interception is disallowed only after a long press claims the gesture. Regular taps
still open cards; the existing Edit flow and its accessibility actions remain available.

Release CI compiles and runs layout/controller/screenshot tests. Physical-device gesture and
frame-time review remains required to assess the new interaction; no measured FPS is claimed.
