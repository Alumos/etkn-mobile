package xyz.etkn.mobile.player.mpv

import android.content.Context
import android.util.AttributeSet
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPVLib

/**
 * libmpv's SurfaceView. It attaches the surface to mpv when it appears and parks the video
 * output while it is gone; options and observed properties come from [MpvPlayer].
 */
class MpvView(context: Context, attrs: AttributeSet) : BaseMPVView(context, attrs) {
    internal var options: () -> Unit = {}
    internal var observed: List<Pair<String, Int>> = emptyList()

    override fun initOptions() {
        setVo("gpu-next")
        options()
    }

    override fun postInitOptions() = Unit

    override fun observeProperties() {
        for ((name, format) in observed) MPVLib.observeProperty(name, format)
    }
}
