package com.r0ybt.taleframe.data

import android.graphics.BitmapFactory

/** Stage is 3:4. A logical media frame fits inside the default 40% placement box. */
fun mediaElementSize(aspect: Float?): Pair<Float, Float> {
    if (aspect == null || !aspect.isFinite() || aspect <= 0f) return .4f to .4f
    val width = minOf(.4f, .4f * aspect / .75f)
    return width to (width * .75f / aspect)
}

/** Called from the existing repository writer, without decoding/rewriting any GIF frame. */
fun initialGifSize(path: String, type: String): Pair<Float, Float> {
    if (type != "gif") return .4f to .4f
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    return mediaElementSize(if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth.toFloat() / bounds.outHeight else null)
}
