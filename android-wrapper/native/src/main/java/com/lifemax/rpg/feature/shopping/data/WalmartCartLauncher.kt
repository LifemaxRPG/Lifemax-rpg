package com.lifemax.rpg.feature.shopping.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** How a link reached the phone's apps. */
enum class OpenedVia {
    /**
     * Plain `ACTION_VIEW`: Android's own link handling chose (the Walmart app when it handles it).
     */
    SYSTEM_LINK_HANDLING,

    /** The phone's browser, after nothing else would take the link. */
    BROWSER,
}

/** The result of one try at opening a link. */
sealed interface OpenResult {
    data class Opened(val via: OpenedVia) : OpenResult

    data class Failed(val reason: String) : OpenResult
}

/** Thrown by a [UrlOpener] when no app could start: nothing was opened. */
class NoAppToOpen(message: String) : Exception(message)

/** The Android part, behind an interface so the launch rules can be tested with a fake. */
interface UrlOpener {
    /** Starts `ACTION_VIEW` for [url] and lets Android's link handling choose. */
    @Throws(NoAppToOpen::class) fun viewWithSystemHandling(url: String)

    /** Starts the browser on [url]. */
    @Throws(NoAppToOpen::class) fun viewInBrowser(url: String)
}

/** What the launcher did with a request. */
sealed interface LaunchOutcome {
    /**
     * The link was handed to the phone. This says NOTHING about the cart: the owner must check it.
     */
    data class Handed(val via: OpenedVia) : LaunchOutcome

    /** The same link was opened a moment ago; opening it again could add everything twice. */
    data object NeedsConfirmation : LaunchOutcome

    /** Nothing on the phone would open the link. Nothing was changed. */
    data class Failed(val reason: String) : LaunchOutcome
}

/**
 * Opens a Walmart cart link.
 * - First it tries `ACTION_VIEW`, so Android's app-link handling opens the Walmart app when it is
 *   installed and handles the link, and the browser otherwise.
 * - Only when Android reports that NO app could start (the call throws before anything opened) does
 *   it try the browser directly, and then it reports a failure. A link that did open is never
 *   opened again by the launcher: a repeat could add the quantities twice.
 * - The same link a second time within [repeatWindowMs] answers [LaunchOutcome.NeedsConfirmation]
 *   until the caller passes `openAgainAnyway = true`.
 * - It never says the cart was updated: it cannot know.
 */
class WalmartCartLauncher(
    private val opener: UrlOpener,
    private val clock: () -> Long = System::currentTimeMillis,
    private val repeatWindowMs: Long = REPEAT_WINDOW_MS,
) {
    private val lastOpened = HashMap<String, Long>()

    fun launch(url: String, openAgainAnyway: Boolean = false): LaunchOutcome {
        val now = clock()
        val before = lastOpened[url]
        if (!openAgainAnyway && before != null && now - before in 0 until repeatWindowMs) {
            return LaunchOutcome.NeedsConfirmation
        }
        val result = open(url)
        if (result is LaunchOutcome.Handed) lastOpened[url] = now
        return result
    }

    /** Whether [url] was opened within the repeat window. */
    fun openedRecently(url: String): Boolean =
        lastOpened[url]?.let { clock() - it in 0 until repeatWindowMs } ?: false

    private fun open(url: String): LaunchOutcome {
        try {
            opener.viewWithSystemHandling(url)
            return LaunchOutcome.Handed(OpenedVia.SYSTEM_LINK_HANDLING)
        } catch (e: NoAppToOpen) {
            // nothing started, so trying the browser cannot duplicate anything
        } catch (e: RuntimeException) {
            return LaunchOutcome.Failed("Could not open the link: ${e.message ?: "unknown error"}")
        }
        return try {
            opener.viewInBrowser(url)
            LaunchOutcome.Handed(OpenedVia.BROWSER)
        } catch (e: Exception) {
            LaunchOutcome.Failed("No app on this phone could open the link.")
        }
    }

    companion object {
        /**
         * UNVERIFIED but conservative: a second launch of the same cart inside this needs a yes.
         */
        const val REPEAT_WINDOW_MS = 10 * 60 * 1000L
    }
}

/** The real opener: `ACTION_VIEW`, and the browser through the app-browser selector. */
class AndroidUrlOpener(private val context: Context) : UrlOpener {
    override fun viewWithSystemHandling(url: String) = start(view(url))

    override fun viewInBrowser(url: String) {
        val selector = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        start(view(url).apply { this.selector = selector })
    }

    private fun start(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            throw NoAppToOpen("No app can open this link")
        } catch (e: SecurityException) {
            throw NoAppToOpen("The system refused to open this link")
        }
    }

    private fun view(url: String) =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
