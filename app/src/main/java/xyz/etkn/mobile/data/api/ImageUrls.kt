package xyz.etkn.mobile.data.api

import xyz.etkn.mobile.data.model.Chapter
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.data.model.PersonRef
import xyz.etkn.mobile.data.net.enc
import xyz.etkn.mobile.data.net.int
import xyz.etkn.mobile.data.net.str

/**
 * Artwork URL rules taken from the TV client. Widths are requested in physical pixels, rounded
 * up to a small set of buckets so Coil's memory/disk cache can share bitmaps across screens.
 */
class ImageUrls(private val server: String, private val userId: String) {

    fun poster(item: Item, widthPx: Int): String? {
        if (item.isMusicGroup || item.isAudio) return music(item, widthPx)
        val own = item.imageTags["Primary"]
        return when {
            item.isEpisode && item.seriesId != null && item.seriesPrimaryTag != null ->
                image(item.seriesId, "Primary", widthPx, item.seriesPrimaryTag)
            own != null -> image(item.id, "Primary", widthPx, own)
            item.isSeason && item.seriesId != null && item.seriesPrimaryTag != null ->
                image(item.seriesId, "Primary", widthPx, item.seriesPrimaryTag)
            else -> null
        }
    }

    /** Own primary only (episode stills, season posters, library covers). */
    fun primary(item: Item, widthPx: Int): String? =
        if (item.isMusicGroup || item.isAudio) music(item, widthPx)
        else item.imageTags["Primary"]?.let { image(item.id, "Primary", widthPx, it) }

    /** 16:9 artwork for continue-watching / episode cards. */
    fun landscape(item: Item, widthPx: Int): String? {
        item.imageTags["Thumb"]?.let { return image(item.id, "Thumb", widthPx, it) }
        if (item.isEpisode) item.imageTags["Primary"]?.let { return image(item.id, "Primary", widthPx, it) }
        if (item.parentThumbItemId != null && item.parentThumbTag != null) {
            return image(item.parentThumbItemId, "Thumb", widthPx, item.parentThumbTag)
        }
        return backdrop(item, widthPx) ?: primary(item, widthPx)
    }

    fun backdrop(item: Item, widthPx: Int): String? {
        item.backdropTags.firstOrNull()?.let { return image(item.id, "Backdrop", widthPx, it) }
        val parent = item.parentBackdropItemId
        val tag = item.parentBackdropTags.firstOrNull()
        if (parent != null && tag != null) return image(parent, "Backdrop", widthPx, tag)
        return null
    }

    fun logo(item: Item, widthPx: Int): String? {
        item.imageTags["Logo"]?.let { return image(item.id, "Logo", widthPx, it) }
        if (item.parentLogoItemId != null && item.parentLogoTag != null) {
            return image(item.parentLogoItemId, "Logo", widthPx, item.parentLogoTag)
        }
        return null
    }

    fun person(person: PersonRef, widthPx: Int): String? =
        person.imageTag?.let { image(person.id, "Primary", widthPx, it) }

    fun user(userId: String = this.userId, tag: String?, widthPx: Int = 160): String =
        "$server/api/tv/Users/${enc(userId)}/Images/Primary?maxWidth=$widthPx" + (tag?.let { "&tag=${enc(it)}" } ?: "")

    fun chapter(item: Item, chapter: Chapter, widthPx: Int): String {
        val raw = chapter.imagePath
        val path = when {
            raw.isNullOrEmpty() -> "/api/tv/Items/${enc(item.id)}/Images/Chapter/${chapter.index}"
            raw.startsWith("/api/") -> raw
            raw.startsWith("/") -> "/api/tv$raw"
            else -> "/api/tv/$raw"
        }
        return "$server$path?maxWidth=${bucket(widthPx)}&quality=85&tag=${enc(chapter.imageTag ?: "")}"
    }

    fun music(item: Item, widthPx: Int): String? {
        val w = bucket(widthPx)
        item.artworkHash?.let {
            return "$server/api/image_proxy?cache=${enc(it)}&maxWidth=$w" +
                if (item.kind == "artists") "&variantGroup=avatar" else ""
        }
        item.artistImage?.let { a ->
            return "$server/api/tv/Items/${enc(a.str("ItemId"))}/Images/Backdrop/${a.int("Index")}?maxWidth=$w" +
                "&tag=${enc(a.str("Tag"))}&variantGroup=avatar"
        }
        return item.imageTags["Primary"]?.let { image(item.id, "Primary", widthPx, it) }
            ?: item.albumKey?.let { null }
    }

    fun tmdb(path: String?, size: String = "w342"): String? =
        path?.takeIf { it.isNotEmpty() }?.let { "https://image.tmdb.org/t/p/$size$it" }

    fun absolute(path: String): String = if (path.startsWith("http")) path else server + path

    private fun image(id: String, type: String, widthPx: Int, tag: String): String =
        "$server/api/tv/Items/${enc(id)}/Images/$type?maxWidth=${bucket(widthPx)}&quality=90&tag=${enc(tag)}"

    companion object {
        private val BUCKETS = intArrayOf(160, 240, 320, 480, 640, 800, 1080, 1280, 1600, 1920)

        fun bucket(px: Int): Int = BUCKETS.firstOrNull { it >= px } ?: BUCKETS.last()
    }
}
