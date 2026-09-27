package com.jagapathi.immichtv.ui.timeline

import androidx.media3.common.PlaybackException
import com.jagapathi.immichtv.R
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoErrorsTest {

    @Test
    fun `playback errors are explained by what the viewer can do about them`() {
        assertEquals(R.string.video_error_network, videoErrorMessage(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertEquals(R.string.video_error_network, videoErrorMessage(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertEquals(R.string.video_error_server, videoErrorMessage(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertEquals(R.string.video_error_format, videoErrorMessage(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertEquals(R.string.video_error_format, videoErrorMessage(PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES))
        assertEquals(R.string.video_error_format, videoErrorMessage(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
        assertEquals(R.string.video_error_other, videoErrorMessage(PlaybackException.ERROR_CODE_UNSPECIFIED))
    }
}
