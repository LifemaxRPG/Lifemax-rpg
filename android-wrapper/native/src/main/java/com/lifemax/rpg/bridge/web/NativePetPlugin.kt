package com.lifemax.rpg.bridge.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.PetContainer
import com.lifemax.rpg.feature.pet.data.PetCommandSender
import com.lifemax.rpg.feature.pet.data.toPetReply
import com.lifemax.rpg.feature.pet.presentation.CatArt
import com.lifemax.rpg.feature.pet.presentation.PetRoute
import com.lifemax.rpg.feature.pet.presentation.PetViewModel
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

/**
 * The Cat tab (Native Cat, beta): the page decides and words everything and stays the only writer;
 * the screen draws its projection (`pet.projection.v1`) with a live room in which the cats live a
 * day of their own, and asks the page to feed, to blink, to pause the clock and to rename with
 * commands. The cats answer a finger on the room themselves and tell the page what a touch came to
 * (`touch`, `pet.touch.v1`), and the room opens full screen on the screen's own. Play and the rest
 * of the Cat page are handed to the page. A genetic coat's sheets are asked of the page with `coat`
 * (`pet.coat.v1`). Everything that is not specific to it lives in [ComposeTabPlugin].
 */
@CapacitorPlugin(name = "NativePet")
class NativePetPlugin : ComposeTabPlugin() {
    private var nextRequest = 0L
    private val nextCoatRequest = AtomicLong()
    private val container =
        PetContainer(
            PetCommandSender { command, payload ->
                "pet-${++nextRequest}".also { sendCommand(command, payload, it) }
            }
        )
    private val petVm: PetViewModel
        get() = ViewModelProvider(store, container.factory)[PetViewModel::class.java]

    override val tabId = "pet"
    override val capabilities = listOf("pet.projection.v1", "pet.coat.v1", "pet.touch.v1")

    /**
     * The live cats' genetic coats: the page composes them and answers `coat {key, kind}` with the
     * sheet. Asked from a worker thread, so the command is only queued, on the UI thread. One
     * source serves every screen with a room, so the screen that opens takes the questions.
     */
    private fun bindCoats() {
        CatArt.coats.bind { coat, kind ->
            "petcoat-${nextCoatRequest.incrementAndGet()}"
                .also { id ->
                    activity.runOnUiThread {
                        sendCommand("coat", JSONObject().put("key", coat).put("kind", kind.key), id)
                    }
                }
        }
    }

    override fun accept(snapshot: JSONObject, explicitOpen: Boolean) {
        if (explicitOpen) bindCoats()
        container.repository.accept(snapshot, explicitOpen)
        if (explicitOpen) petVm.opened() else petVm.projected()
    }

    override fun onReply(reply: JSONObject) {
        if (!CatArt.coats.onReply(reply)) reply.toPetReply()?.let(petVm::onReply)
    }

    @Composable
    override fun Content(
        topInsetDp: Float,
        bottomInsetDp: Float,
        emit: (action: String, id: String) -> Unit,
    ) {
        val viewModel = remember { petVm }
        PetRoute(viewModel, topInsetDp, bottomInsetDp, emit, visible)
    }
}
