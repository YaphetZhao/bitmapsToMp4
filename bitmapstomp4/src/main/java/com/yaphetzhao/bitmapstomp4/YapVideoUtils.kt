package com.yaphetzhao.bitmapstomp4

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View

@Suppress("DEPRECATION")
object YapVideoUtils {

    fun convertViewToBitmap(view: View): Bitmap {
        var bitmap: Bitmap?
        view.destroyDrawingCache()
        view.buildDrawingCache()
        bitmap = view.drawingCache
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(
                view.measuredWidth,
                view.measuredHeight, Bitmap.Config.RGB_565
            )
            val bitmapHolder = Canvas(bitmap)
            view.draw(bitmapHolder)
        }
        return bitmap!!
    }

}