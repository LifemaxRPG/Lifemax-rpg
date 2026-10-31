package com.lifemax.rpg.feature.library.data

import com.lifemax.rpg.feature.library.domain.MediaAction

/**
 * The page's action name and id for an action (50-native-library.js onAction). An action carries
 * only those two, so ticking and unticking are two names; "navigate" carries a page view.
 */
fun MediaAction.toEvent(): Pair<String, String> =
    when (this) {
        MediaAction.Add -> "add" to ""
        MediaAction.DismissTip -> "tip" to ""
        is MediaAction.OpenPane -> "navigate" to id
        is MediaAction.Edit -> "edit" to id
        is MediaAction.SetFinished -> (if (finished) "finished" else "unfinished") to id
        is MediaAction.Remove -> "remove" to id
        is MediaAction.Play -> "play" to id
        is MediaAction.Attach -> "attach" to id
        is MediaAction.Replace -> "replace" to id
        is MediaAction.RemoveAudio -> "removeAudio" to id
        is MediaAction.OpenLink -> "link" to id
    }
