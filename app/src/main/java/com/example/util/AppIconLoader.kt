package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ultra-lightweight in-memory icon loader and cache.
 * Designed strictly for 1GB RAM devices:
 * - Bitmaps scaled to 96x96 px max (only ~36KB per icon)
 * - Maximum cache capacity 80 items (~2.8MB maximum total RAM)
 * - Never causes Garbage Collection stutter during fast scroll
 */
object AppIconLoader {
    private val iconCache = LruCache<String, ImageBitmap>(80)

    fun getCached(packageName: String): ImageBitmap? = iconCache.get(packageName)

    suspend fun loadIcon(context: Context, packageName: String): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(packageName)
                val bitmap = drawableToBitmap(drawable, 96, 96)
                val imageBitmap = bitmap.asImageBitmap()
                iconCache.put(packageName, imageBitmap)
                imageBitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val src = drawable.bitmap
            if (src.width in 1..width && src.height in 1..height) {
                return src
            }
            return Bitmap.createScaledBitmap(src, width, height, true)
        }
        val intrinsicWidth = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else width
        val intrinsicHeight = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else height
        val targetWidth = if (intrinsicWidth > width) width else intrinsicWidth
        val targetHeight = if (intrinsicHeight > height) height else intrinsicHeight

        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}

@Composable
fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf(AppIconLoader.getCached(packageName)) }

    LaunchedEffect(packageName) {
        if (bitmap == null) {
            bitmap = AppIconLoader.loadIcon(context, packageName)
        }
    }

    return bitmap
}
