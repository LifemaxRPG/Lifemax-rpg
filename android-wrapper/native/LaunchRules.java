package com.lifemax.rpg;

/**
 * What MainActivity takes from the intent that starts or resumes it (backlog SEC-3 and NAV-4). MainActivity is exported
 * (it is the launcher and the home screen), so any app can send it an intent.
 *
 * Plain Java with no Android types, so tests/launch-intent.test.py runs it.
 */
final class LaunchRules {
    /**
     * The screens the lma_open extra may name. Only the Life Director dial widget (LifeDialWidget) sends one, and the web
     * side (40-widget.js, lmaWidgetLaunch) opens only that one. Add a name here together with its sender and its handler.
     */
    private static final String[] OPEN_TARGETS = {"life-director"};

    private LaunchRules() {}

    /** The screen to open, or null when the extra is missing or names a screen the app does not open from a launch. */
    static String openTarget(String requested) {
        if (requested == null) return null;
        for (String t : OPEN_TARGETS) if (t.equals(requested)) return t;
        return null;
    }

    /**
     * True when the intent was already delivered once: Android hands an activity its original intent again when it is
     * re-created after process death or a configuration change (saved state is present), and when the task is reopened
     * from Recents (FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY). Such an intent must not open a screen or run a link again.
     */
    static boolean isReplay(boolean hasSavedState, boolean launchedFromHistory) {
        return hasSavedState || launchedFromHistory;
    }
}
