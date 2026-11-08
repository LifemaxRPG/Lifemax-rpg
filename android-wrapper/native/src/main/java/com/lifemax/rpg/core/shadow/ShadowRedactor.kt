package com.lifemax.rpg.core.shadow

import java.security.MessageDigest

/**
 * Keeps the owner's words out of anything the shadow stores or reports (ECONOMY_SHADOW_PLAN 3.2). A
 * name that is not one of the app's own ([ShadowKeys.FIXED_MEMBERS]) is shown only as a salted
 * hash: the first [ShadowLimits.HASH_DIGITS] hex digits of SHA-256 over the ring's per-install
 * [salt] and the text, so a short name cannot be looked up in a table made elsewhere.
 */
class ShadowRedactor(private val salt: ByteArray) {
    init {
        require(salt.size == ShadowLimits.SALT_BYTES) { "salt" }
    }

    /** The salted hash of [text], as hex digits. */
    fun hash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(ShadowLimits.HASH_DIGITS)
    }

    /** A member name inside a path: as it is when the app owns the word, else `*#a1b2c3`. */
    fun member(name: String): String =
        if (name in ShadowKeys.FIXED_MEMBERS) name else "*#" + hash(name)

    /** Any free text: its length and salted hash only, `<text n=12 #a1b2c3>`. */
    fun text(value: String): String = "<text n=${value.length} #${hash(value)}>"
}
