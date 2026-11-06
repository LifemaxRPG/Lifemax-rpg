# Native bar scrub previews

Native chrome owns a disposable preview overlay during bar scrubbing. Existing BarScrubber selection, settling and navigation remain authoritative; the preview does not navigate or mutate gameplay. The shell reads the existing web thumbnail cache and tab label asynchronously. A generation check rejects obsolete replies; missing images show a label on a tinted background. Journal images are never displayed.

Images are bounded before decoding while retaining thumbnail detail (up to approximately 320 pixels wide). Android 12+ applies a hardware RenderEffect blur to the image layer; older Android versions use the platform intrinsic bitmap blur. The label and enlarged navbar icon remain sharp. Icon geometry comes from the selected navbar button's own SVG rectangles, avoiding a separate icon mapping. No screenshot is captured during the gesture. Native scrub state suppresses delayed web thumbnail capture so the preview cannot capture itself. Detachment invalidates pending replies and clears images; shell closure clears the web flag.

Selection and strip motion use short, interruptible platform animators, respecting Android animation duration settings. Keep the navigation simulation and native bar tests in the release gate. These previews are temporary gesture feedback, not replacements for live native screens.
