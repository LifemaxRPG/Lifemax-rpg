# ADR 0024: Overview card launch reveal in the shared shell

Explicit Overview navigate actions and its plan command arm a short-lived screenshot cover
at the last tap position. The incoming native screen's pre-draw starts a 560ms circular reveal;
classic destinations use a 500ms fallback. The mask only covers content between the shared bars.
No feature-specific state, reward, or simulation code participates.

Touch origin is consumed once. Scrolls, cancelled gestures, and navigation without a tap do not
arm a reveal. Existing tab swipe and scrubber routes are unchanged. Reduced motion and disabled
system animation skip capture. A new launch, mismatched destination, page load, detach, or timeout
cleans up the cover. The capture is downscaled with filtered drawing to bound memory use. It is
released through normal detached-view lifetime rather than recycling a bitmap a display list
may still reference. The deadline prevents a failed destination from indefinitely blocking UI.

This effect is a visual cover, not a new navigator. Existing native/web navigation owns all
destination and back behavior. No Cat feature implementation is modified. Release CI validates
Android compilation and existing navigation tests; visual/device performance still needs review.
