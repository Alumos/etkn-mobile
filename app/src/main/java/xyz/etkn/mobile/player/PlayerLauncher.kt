package xyz.etkn.mobile.player

import android.content.Context
import android.content.Intent
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.graph

object PlayerLauncher {
    const val EXTRA_ITEM = "item_id"
    const val EXTRA_TYPE = "item_type"
    const val EXTRA_SERIES = "series_id"
    const val EXTRA_SEASON = "season_id"
    const val EXTRA_RESUME = "resume"
    const val EXTRA_START_MS = "start_ms"
    const val EXTRA_SOURCE = "source_id"

    fun play(context: Context, item: Item, resume: Boolean = true, sourceId: String? = null) {
        PlaybackPrefetch.start(context.graph, item)
        val intent = Intent(context, PlayerActivity::class.java)
            .putExtra(EXTRA_ITEM, item.id)
            .putExtra(EXTRA_TYPE, item.type)
            .putExtra(EXTRA_SERIES, item.seriesId ?: if (item.isSeries) item.id else null)
            .putExtra(EXTRA_SEASON, item.seasonId ?: if (item.isSeason) item.id else null)
            .putExtra(EXTRA_RESUME, resume)
            .putExtra(EXTRA_START_MS, if (resume && !item.userData.played) item.positionMs else 0L)
            .putExtra(EXTRA_SOURCE, sourceId)
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
