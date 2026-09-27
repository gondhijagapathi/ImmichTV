package com.jagapathi.immichtv.ui.video

import androidx.media3.common.Player

/** Makes a player for Immich videos. Whoever creates one must release it. */
fun interface VideoPlayerFactory {
    fun create(): Player
}
