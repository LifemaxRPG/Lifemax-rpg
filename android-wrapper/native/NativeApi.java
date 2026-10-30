package com.lifemax.rpg;

/**
 * The version of the Android shell's abilities, as far as the web app is concerned. Bump this by one whenever the native
 * side changes in a way the web app depends on (a new plugin, a new permission, a changed plugin method). A web build
 * is only applied over the air to an APK whose number is at least what that web build was made for; an older APK is
 * offered the new APK instead. Web-only changes never need a bump.
 */
final class NativeApi {
    static final int VALUE = 19;
    private NativeApi() {}
}


