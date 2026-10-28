package com.batoh.feature.backpack

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import com.batoh.core.conversion.GifEncoder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Turns a picked JPEG or PNG into a single frame for the shared panel send path. */
internal object StaticImageGif {
    fun isSupported(bytes: ByteArray): Boolean =
        bytes.size >= 8 && (
            (bytes[0].toInt() and 0xff) == 0xff && (bytes[1].toInt() and 0xff) == 0xd8 &&
                (bytes[2].toInt() and 0xff) == 0xff ||
                bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
        )

    fun convert(bytes: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image size" }
        var sample = 1
        while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })) {
            "Image could not be decoded"
        }
        val orientation = if ((bytes[0].toInt() and 0xff) == 0xff) runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL) else ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> preScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> preRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { preRotate(90f); preScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> preRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { preRotate(-90f); preScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> preRotate(-90f)
            }
        }
        val oriented = if (matrix.isIdentity) decoded else Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height, matrix, true
        ).also { decoded.recycle() }
        // GIF encoding ignores alpha. Flatten transparent PNG pixels onto black so hidden RGB
        // values in transparent areas cannot appear as stray colours on a panel.
        val image = Bitmap.createBitmap(oriented.width, oriented.height, Bitmap.Config.ARGB_8888)
        Canvas(image).apply {
            drawColor(Color.BLACK)
            drawBitmap(oriented, 0f, 0f, null)
        }
        oriented.recycle()
        try {
            val output = ByteArrayOutputStream()
            val encoder = GifEncoder().apply {
                setSize(image.width, image.height)
                setDelay(1000)
            }
            check(encoder.start(output) && encoder.addFrame(image) && encoder.finish()) { "GIF encoding failed" }
            return output.toByteArray()
        } finally {
            image.recycle()
        }
    }
}
