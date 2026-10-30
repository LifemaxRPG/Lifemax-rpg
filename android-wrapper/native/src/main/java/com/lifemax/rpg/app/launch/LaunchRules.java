package com.lifemax.rpg.app.launch;

/**
 * The screens an intent may ask MainActivity to open (backlog SEC-3). MainActivity is exported (it is the launcher and the
 * home screen), so any app can send it an intent.
 *
 * Plain Java with no Android types, so tests/launch-intent.test.py runs it.
 */
public final class LaunchRules {
    /**
     * The screens the lma_open extra may name. Only the Life Director dial widget (LifeDialWidget) sends one, and the web
     * side (40-widget.js, lmaWidgetLaunch) opens only that one. Add a name here together with its sender and its handler.
     */
    private static final String[] OPEN_TARGETS = {"life-director"};

    private LaunchRules() {}

    /** The screen to open, or null when the extra is missing or names a screen the app does not open from a launch. */
    public static String openTarget(String requested) {
        if (requested == null) return null;
        for (String t : OPEN_TARGETS) if (t.equals(requested)) return t;
        return null;
    }
}
