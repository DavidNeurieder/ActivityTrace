package com.activitytrace.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.activitytrace.demo.DemoAppCatalog
import com.activitytrace.model.CapturedItem

/**
 * Single place that turns a captured item (or a package name) into a rendered
 * icon. Real apps resolve through the installed [PackageManager]; demo records
 * resolve through the [DemoAppCatalog]'s bundled vector drawables so the demo
 * never depends on any third-party app being installed.
 */
object AppIconResolver {

    fun resolve(context: Context, item: CapturedItem): ImageBitmap? {
        if (item.appPackage == "local") return null

        if (item.demoDatasetId != null) {
            val demoApp = DemoAppCatalog.byPackage(item.appPackage)
                ?: DemoAppCatalog.byName(item.appName.orEmpty())
                ?: return null
            return resolveDrawable(context, demoApp.iconRes)
        }

        return try {
            context.packageManager.getApplicationIcon(item.appPackage)
                .toBitmap().asImageBitmap()
        } catch (_: Exception) { null }
    }

    fun resolveByPackage(context: Context, appPackage: String): ImageBitmap? {
        if (appPackage == "local") return null
        val demoApp = DemoAppCatalog.byPackage(appPackage)
        if (demoApp != null) {
            return resolveDrawable(context, demoApp.iconRes)
        }
        return try {
            context.packageManager.getApplicationIcon(appPackage)
                .toBitmap().asImageBitmap()
        } catch (_: Exception) { null }
    }

    /** Renders a vector drawable resource id to a [ImageBitmap]. */
    fun resolveDrawable(context: Context, resId: Int): ImageBitmap? {
        val drawable = ContextCompat.getDrawable(context, resId) ?: return null
        return drawable.toBitmap().asImageBitmap()
    }
}

fun Drawable.toBitmap(defaultSize: Int = 256): Bitmap {
    if (this is BitmapDrawable) return bitmap
    val w = if (intrinsicWidth > 0) intrinsicWidth else defaultSize
    val h = if (intrinsicHeight > 0) intrinsicHeight else defaultSize
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}