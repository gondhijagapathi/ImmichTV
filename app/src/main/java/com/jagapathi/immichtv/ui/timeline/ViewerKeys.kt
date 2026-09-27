package com.jagapathi.immichtv.ui.timeline

import androidx.compose.ui.input.key.Key

/** Something a remote key does in the [AssetViewer]. */
internal enum class ViewerAction {
    Previous,
    Next,
    ToggleDetails,
    SeekBack,
    SeekForward,
    PlayPause,
    Play,
    Pause,

    /** Shows the video controls and moves focus onto them. */
    FocusControls,

    /** Moves focus off the video controls, back to the video. */
    LeaveControls
}

/**
 * What [key] does in the viewer. On a photo, left and right move through the timeline and the
 * other arrows and OK toggle the details. On a video they work like other TV video apps: left and
 * right seek, OK plays or pauses, and down reaches the controls, which have buttons for moving on.
 *
 * While the video controls have focus, the arrows and OK are left to them, and up moves back to
 * the video. Media keys always work. Returns null for keys the viewer doesn't handle.
 */
internal fun viewerActionFor(key: Key, isVideo: Boolean, controlsFocused: Boolean): ViewerAction? {
    when (key) {
        Key.MediaNext -> return ViewerAction.Next
        Key.MediaPrevious -> return ViewerAction.Previous
    }
    if (!isVideo) {
        return when (key) {
            Key.DirectionRight -> ViewerAction.Next
            Key.DirectionLeft -> ViewerAction.Previous
            Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
                ViewerAction.ToggleDetails
            else -> null
        }
    }
    return when (key) {
        Key.MediaPlayPause -> ViewerAction.PlayPause
        Key.MediaPlay -> ViewerAction.Play
        Key.MediaPause -> ViewerAction.Pause
        Key.MediaFastForward -> ViewerAction.SeekForward
        Key.MediaRewind -> ViewerAction.SeekBack
        else -> if (controlsFocused) {
            if (key == Key.DirectionUp) ViewerAction.LeaveControls else null
        } else {
            when (key) {
                Key.DirectionRight -> ViewerAction.SeekForward
                Key.DirectionLeft -> ViewerAction.SeekBack
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> ViewerAction.PlayPause
                Key.DirectionUp -> ViewerAction.ToggleDetails
                Key.DirectionDown -> ViewerAction.FocusControls
                else -> null
            }
        }
    }
}
