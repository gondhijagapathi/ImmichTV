package com.jagapathi.immichtv.ui.video

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.SurfaceControl
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.window.SurfaceSyncGroup
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player

/**
 * Shows [player]'s video in a [SurfaceView], like media3's `PlayerSurface`, but without asking the
 * window to leave out the hole the SurfaceView cuts for the video. Android then splits the window
 * into the parts around the video, and some TVs' display hardware can only show a window that's a
 * single rectangle (a Realtek-based one on Android 9 logs "Visible region with 2 rects, only '1'
 * supported"). With the video letterboxed, the full-screen viewer vanished there: its black
 * background, details and controls were gone and the grid showed on either side of the video.
 *
 * The hole is still cut, so the video shows through it. The window is just composited whole.
 */
@Composable
internal fun VideoSurfaceView(player: Player, modifier: Modifier = Modifier) {
    AndroidView(
        factory = ::VideoSurfaceFrame,
        modifier = modifier,
        onRelease = { it.player = null },
        update = { it.player = player }
    )
}

private class VideoSurfaceFrame(context: Context) : FrameLayout(context) {
    private var surfaceSyncGroup: SurfaceSyncGroup? = null

    private val surfaceView = object : SurfaceView(context) {
        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            if (Build.VERSION.SDK_INT == 34) {
                surfaceSyncGroup?.markSyncReady()
                surfaceSyncGroup = null
            }
        }
    }.also(::addView)

    private val listener = object : Player.Listener {
        override fun onSurfaceSizeChanged(width: Int, height: Int) {
            if (Build.VERSION.SDK_INT == 34) post(::syncResizeWithNextDraw)
        }
    }

    var player: Player? = null
        set(value) {
            if (value === field) return
            field?.run {
                removeListener(listener)
                clearVideoSurfaceView(surfaceView)
            }
            value?.run {
                addListener(listener)
                setVideoSurfaceView(surfaceView)
            }
            field = value
        }

    /** Keeps the hole out of the window's transparent region. See [VideoSurfaceView]. */
    override fun requestTransparentRegion(child: View?) = Unit

    /**
     * On Android 14 a resized SurfaceView can show a frame at the wrong size
     * (https://github.com/androidx/media/issues/1237). Like media3's `PlayerSurface`, hold the
     * window's next frame until the SurfaceView has drawn at its new size.
     */
    @RequiresApi(34)
    private fun syncResizeWithNextDraw() {
        val rootSurfaceControl = surfaceView.rootSurfaceControl ?: return
        surfaceSyncGroup = SurfaceSyncGroup("ImmichTV-video-resize").apply { add(rootSurfaceControl) {} }
        surfaceView.invalidate()
        rootSurfaceControl.applyTransactionOnDraw(SurfaceControl.Transaction())
    }
}
