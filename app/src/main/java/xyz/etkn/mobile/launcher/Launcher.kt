package xyz.etkn.mobile.launcher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import xyz.etkn.mobile.MainActivity
import xyz.etkn.mobile.R
import xyz.etkn.mobile.data.model.Item

/**
 * Launcher entries outside the app: the 继续观看 widget and long-press shortcuts for the most
 * recent titles (search and downloads are static, `res/xml/shortcuts.xml`). Both are refreshed
 * from the home screen's 继续观看 shelf; private accounts and signing out leave nothing behind.
 */
object LauncherEntries {
    private const val PREFS = "launcher"
    private const val MAX_SHORTCUTS = 2

    fun publishResume(context: Context, items: List<Item>, keep: Boolean) {
        val recent = if (keep) items.take(MAX_SHORTCUTS) else emptyList()
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(
                context,
                recent.map { item ->
                    ShortcutInfoCompat.Builder(context, "resume-${item.id}")
                        .setShortLabel(label(item).take(24))
                        .setLongLabel(label(item))
                        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_play))
                        .setIntent(open(context, item.id).setAction(Intent.ACTION_VIEW))
                        .build()
                },
            )
        }
        val first = recent.firstOrNull()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (first == null) clear()
            else {
                putString("id", first.id)
                putString("title", label(first))
                putString("subtitle", if (first.isEpisode) listOfNotNull(first.episodeLabel, first.name).joinToString(" ") else first.productionYear?.toString().orEmpty())
                putInt("progress", (first.progress * 1000).toInt())
            }
        }.apply()
        ResumeWidget.refresh(context)
    }

    fun clear(context: Context) = publishResume(context, emptyList(), keep = false)

    internal fun open(context: Context, itemId: String?): Intent =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (itemId != null) putExtra(MainActivity.EXTRA_ITEM, itemId) }

    internal fun snapshot(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun label(item: Item) = if (item.isEpisode) item.seriesName ?: item.name else item.name
}

/** 继续观看 widget: the last title in progress with its progress bar; tap opens its page. */
class ResumeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val prefs = LauncherEntries.snapshot(context)
        val id = prefs.getString("id", null)
        val views = RemoteViews(context.packageName, R.layout.widget_resume).apply {
            setTextViewText(R.id.widget_title, prefs.getString("title", null) ?: context.getString(R.string.widget_resume))
            setTextViewText(R.id.widget_subtitle, prefs.getString("subtitle", null).orEmpty())
            setProgressBar(R.id.widget_progress, 1000, prefs.getInt("progress", 0), false)
            setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 0, LauncherEntries.open(context, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = runCatching { manager.getAppWidgetIds(ComponentName(context, ResumeWidget::class.java)) }.getOrNull() ?: return
            if (ids.isNotEmpty()) ResumeWidget().onUpdate(context, manager, ids)
        }
    }
}
