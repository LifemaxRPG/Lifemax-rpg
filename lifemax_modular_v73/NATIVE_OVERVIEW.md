# Live hybrid Overview

Native API 10 replaces the separate Canvas activity with two small native control
strips attached to MainActivity: the header and bottom navigation. The original
hardware-accelerated WebView remains resumed and owns scrolling and the entire
Overview body. Cats, Life Director, timers and other web artwork remain their
original live DOM/canvas elements, with the original handlers and one save.

There is no body snapshot, page-sized software surface, polling loop or activity
transition. The software layers for native controls are bounded to those strips.
Native header position follows WebView scrolling. Closing or disabling native
mode restores the original controls. Old API 9 Canvas shells are never opened by
this bridge. Look & feel → Appearance → Native Overview still selects Classic.

This is deliberately a hybrid page, rather than a full native reconstruction of
its cards. Browser checks cover live animation/timers, original actions, setting
persistence, cleanup and old-shell fallback. Android instrumentation checks that
MainActivity remains resumed, the original WebView remains accelerated, animation
and timers advance, scrolling works, and Classic persists. Phone scrolling and
frame pacing still need physical-device evaluation.
