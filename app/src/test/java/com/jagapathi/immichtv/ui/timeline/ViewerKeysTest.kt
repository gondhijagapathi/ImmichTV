package com.jagapathi.immichtv.ui.timeline

import androidx.compose.ui.input.key.Key
import com.jagapathi.immichtv.ui.timeline.ViewerAction.FocusControls
import com.jagapathi.immichtv.ui.timeline.ViewerAction.LeaveControls
import com.jagapathi.immichtv.ui.timeline.ViewerAction.Next
import com.jagapathi.immichtv.ui.timeline.ViewerAction.Pause
import com.jagapathi.immichtv.ui.timeline.ViewerAction.Play
import com.jagapathi.immichtv.ui.timeline.ViewerAction.PlayPause
import com.jagapathi.immichtv.ui.timeline.ViewerAction.Previous
import com.jagapathi.immichtv.ui.timeline.ViewerAction.SeekBack
import com.jagapathi.immichtv.ui.timeline.ViewerAction.SeekForward
import com.jagapathi.immichtv.ui.timeline.ViewerAction.ToggleDetails
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerKeysTest {

    private fun photo(key: Key) = viewerActionFor(key, isVideo = false, controlsFocused = false)
    private fun video(key: Key) = viewerActionFor(key, isVideo = true, controlsFocused = false)
    private fun videoControls(key: Key) = viewerActionFor(key, isVideo = true, controlsFocused = true)

    @Test
    fun `on a photo the arrows browse and OK toggles the details`() {
        assertEquals(Previous, photo(Key.DirectionLeft))
        assertEquals(Next, photo(Key.DirectionRight))
        for (key in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) {
            assertEquals(ToggleDetails, photo(key))
        }
        assertEquals(Previous, photo(Key.MediaPrevious))
        assertEquals(Next, photo(Key.MediaNext))
        assertEquals(null, photo(Key.MediaPlayPause))
        assertEquals(null, photo(Key.MediaFastForward))
    }

    @Test
    fun `on a video left and right seek, OK plays or pauses and down reaches the controls`() {
        assertEquals(SeekBack, video(Key.DirectionLeft))
        assertEquals(SeekForward, video(Key.DirectionRight))
        assertEquals(PlayPause, video(Key.DirectionCenter))
        assertEquals(PlayPause, video(Key.Enter))
        assertEquals(ToggleDetails, video(Key.DirectionUp))
        assertEquals(FocusControls, video(Key.DirectionDown))
        assertEquals(null, video(Key.Back))
    }

    @Test
    fun `while the video controls have focus the arrows and OK are left to them`() {
        for (key in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionDown, Key.DirectionCenter, Key.Enter)) {
            assertEquals(null, videoControls(key))
        }
        assertEquals(LeaveControls, videoControls(Key.DirectionUp))
    }

    @Test
    fun `media keys work on a video wherever the focus is`() {
        for (action in listOf(::video, ::videoControls)) {
            assertEquals(PlayPause, action(Key.MediaPlayPause))
            assertEquals(Play, action(Key.MediaPlay))
            assertEquals(Pause, action(Key.MediaPause))
            assertEquals(SeekForward, action(Key.MediaFastForward))
            assertEquals(SeekBack, action(Key.MediaRewind))
            assertEquals(Next, action(Key.MediaNext))
            assertEquals(Previous, action(Key.MediaPrevious))
        }
    }
}
