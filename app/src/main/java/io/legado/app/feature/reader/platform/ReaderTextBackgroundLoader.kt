package io.legado.app.feature.reader.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import splitties.init.appCtx
import java.io.File
import java.io.InputStream

/** Android resource boundary shared by background measurement and Canvas drawing. */
object ReaderTextBackgroundLoader {
    private val bitmaps = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun dimensions(source: String): Pair<Int, Int> = runCatching {
        open(source)?.use { input ->
            BitmapFactory.Options().run {
                inJustDecodeBounds = true
                BitmapFactory.decodeStream(input, null, this)
                outWidth.coerceAtLeast(0) to outHeight.coerceAtLeast(0)
            }
        }
    }.getOrNull() ?: (0 to 0)

    fun load(source: String): Bitmap? {
        if (source.isBlank()) return null
        val key = cacheKey(source)
        cached(source)?.let { return it }
        val dimensions = dimensions(source)
        if (dimensions.first <= 0 || dimensions.second <= 0) return null
        val sampleSize = if (isRawNinePatch(source)) 1 else calculateInSampleSize(
            width = dimensions.first,
            height = dimensions.second,
            requestedWidth = appCtx.resources.displayMetrics.widthPixels,
            requestedHeight = appCtx.resources.displayMetrics.heightPixels,
        )
        return runCatching {
            open(source)?.use { input ->
                BitmapFactory.decodeStream(
                    input,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize },
                )
            }
        }.getOrNull()?.takeUnless(Bitmap::isRecycled)?.also { bitmaps.put(key, it) }
    }

    fun cached(source: String): Bitmap? = source.takeIf(String::isNotBlank)
        ?.let(::cacheKey)
        ?.let(bitmaps::get)
        ?.takeUnless(Bitmap::isRecycled)

    internal fun assetCandidates(source: String): List<String> = when {
        source.startsWith("assets://") -> listOf(source.removePrefix("assets://"))
        source.startsWith("content://") || File(source).exists() -> emptyList()
        source.startsWith("bg/") -> listOf(source)
        else -> listOf("bg/$source")
    }

    private fun open(source: String): InputStream? {
        if (source.isBlank()) return null
        return when {
            source.startsWith("assets://") -> appCtx.assets.open(source.removePrefix("assets://"))
            source.startsWith("content://") -> appCtx.contentResolver.openInputStream(Uri.parse(source))
            File(source).exists() -> File(source).inputStream()
            else -> assetCandidates(source).firstNotNullOfOrNull { asset ->
                runCatching { appCtx.assets.open(asset) }.getOrNull()
            }
        }
    }

    private fun cacheKey(source: String): String {
        val file = File(source)
        return if (file.isFile) "$source:${file.length()}:${file.lastModified()}" else source
    }

    private fun isRawNinePatch(source: String): Boolean =
        source.substringBefore('?').substringBefore('#').endsWith(".9.png", ignoreCase = true)

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        requestedWidth: Int,
        requestedHeight: Int,
    ): Int {
        var sample = 1
        if (height > requestedHeight || width > requestedWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / sample >= requestedHeight && halfWidth / sample >= requestedWidth) sample *= 2
        }
        return sample
    }
}
