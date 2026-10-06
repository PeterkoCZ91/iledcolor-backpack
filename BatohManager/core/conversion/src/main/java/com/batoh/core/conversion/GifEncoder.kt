package com.batoh.core.conversion

import android.graphics.Bitmap
import java.io.IOException
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Animated GIF encoder — writes GIF89a with multiple frames.
 * Ported from Kevin Weiner's Java implementation (public domain).
 */
class GifEncoder {

    private var width = 0
    private var height = 0
    private var transparent: Int? = null
    private var transIndex = 0
    private var repeat = -1
    private var delay = 0
    private var started = false
    private var out: OutputStream? = null
    private var image: Bitmap? = null
    private var pixels = byteArrayOf()
    private var indexedPixels = byteArrayOf()
    // iledeyes format: 128-color Global Color Table (GCT size field = 6, packed
    // flags 0xE6). The LZW minimum code size is colorDepth = 7 (log2(128)).
    private val paletteColors = 128
    private var colorDepth = 7        // log2(128); LZW min code size
    private var colorTab = byteArrayOf()
    private var usedEntry = BooleanArray(paletteColors)
    private var palSize = 6           // log2(128) - 1
    private var firstFrame = true
    private var sizeSet = false
    private var sample = 10           // NeuQuant quality: 1 best, 10 default

    /** Frame delay in hundredths of a second. */
    fun setDelay(ms: Int) {
        delay = (ms / 10.0).roundToInt()
    }

    /** Number of times to loop animation (0 = forever). */
    fun setRepeat(iter: Int) {
        if (iter >= 0) repeat = iter
    }

    /** Output frame size; must be called before start() or first addFrame(). */
    fun setSize(w: Int, h: Int) {
        width = w
        height = h
        sizeSet = true
    }

    /** Open output stream and write GIF header. Returns true on success. */
    fun start(os: OutputStream): Boolean {
        started = false
        out = os
        return try {
            writeString("GIF89a")
            started = true
            true
        } catch (e: IOException) {
            false
        }
    }

    /** Add a frame to the GIF. Returns true on success. */
    fun addFrame(im: Bitmap): Boolean {
        if (!started) return false
        return try {
            if (!sizeSet) setSize(im.width, im.height)
            image = im
            getImagePixels()
            analyzePixels()
            if (firstFrame) {
                writeLSD()
                writePalette()
                if (repeat >= 0) writeNetscapeExt()
            }
            writeGraphicCtrlExt()
            writeImageDesc()
            if (!firstFrame) writePalette()
            writePixels()
            firstFrame = false
            true
        } catch (e: IOException) {
            false
        }
    }

    /** Flush and close the GIF. Returns true on success. */
    fun finish(): Boolean {
        if (!started) return false
        started = false
        return try {
            out!!.write(0x3B) // GIF trailer
            out!!.flush()
            true
        } catch (e: IOException) {
            false
        } finally {
            out = null
            image = null
            pixels = byteArrayOf()
            indexedPixels = byteArrayOf()
            colorTab = byteArrayOf()
            firstFrame = true
        }
    }

    // ---- private helpers ----

    private fun analyzePixels() {
        val nPix = pixels.size / 3
        indexedPixels = ByteArray(nPix)
        // 128-color quantizer to match iledeyes' 128-entry GCT (device needs ≤128 colors).
        val nq = NeuQuant(pixels, pixels.size, sample, paletteColors)
        colorTab = nq.process()
        var k = 0
        while (k < colorTab.size) {
            usedEntry[k / 3] = false
            k += 3
        }
        var idx = 0
        for (i in 0 until nPix) {
            val mapped = nq.map(
                pixels[idx++].toInt() and 0xff,
                pixels[idx++].toInt() and 0xff,
                pixels[idx++].toInt() and 0xff
            )
            usedEntry[mapped] = true
            indexedPixels[i] = mapped.toByte()
        }
        if (transparent != null) transIndex = findClosest(transparent!!)
    }

    private fun findClosest(c: Int): Int {
        if (colorTab.isEmpty()) return -1
        val r = (c shr 16) and 0xff
        val g = (c shr 8) and 0xff
        val b = c and 0xff
        var minpos = 0
        var dmin = Int.MAX_VALUE
        var i = 0
        while (i < colorTab.size) {
            val dr = r - (colorTab[i].toInt() and 0xff)
            val dg = g - (colorTab[i + 1].toInt() and 0xff)
            val db = b - (colorTab[i + 2].toInt() and 0xff)
            val d = dr * dr + dg * dg + db * db
            val index = i / 3
            if (usedEntry[index] && d < dmin) {
                dmin = d
                minpos = index
            }
            i += 3
        }
        return minpos
    }

    private fun getImagePixels() {
        val w = image!!.width
        val h = image!!.height
        val argb = IntArray(w * h)
        image!!.getPixels(argb, 0, w, 0, 0, w, h)
        pixels = ByteArray(w * h * 3)
        var src = 0
        var dst = 0
        while (src < argb.size) {
            val c = argb[src++]
            pixels[dst++] = ((c shr 16) and 0xff).toByte()
            pixels[dst++] = ((c shr 8) and 0xff).toByte()
            pixels[dst++] = (c and 0xff).toByte()
        }
    }

    @Throws(IOException::class)
    private fun writeLSD() {
        writeShort(width)
        writeShort(height)
        // iledeyes packed flags = 0xE6: GCT present (0x80) | color resolution 7 (0x60)
        // | sorted 0 | GCT size = palSize (6 → 128 entries).
        out!!.write(0x80 or 0x60 or palSize)
        out!!.write(0)   // background color index
        out!!.write(0)   // pixel aspect ratio
    }

    @Throws(IOException::class)
    private fun writeNetscapeExt() {
        out!!.write(0x21)           // extension introducer
        out!!.write(0xFF)           // app extension label
        out!!.write(11)             // block size
        writeString("NETSCAPE2.0")
        out!!.write(3)              // sub-block size
        out!!.write(1)              // loop sub-block id
        writeShort(repeat)
        out!!.write(0)              // block terminator
    }

    @Throws(IOException::class)
    private fun writeGraphicCtrlExt() {
        out!!.write(0x21)
        out!!.write(0xF9)
        out!!.write(4)
        val transp = if (transparent == null) 0 else 1
        // iledeyes uses disposal method = 1 ("do not dispose") on every frame.
        val disp = 1
        out!!.write((disp shl 2) or transp)
        writeShort(delay)
        out!!.write(transIndex)
        out!!.write(0)
    }

    @Throws(IOException::class)
    private fun writeImageDesc() {
        out!!.write(0x2C)           // image separator
        writeShort(0); writeShort(0) // position x, y
        writeShort(width)
        writeShort(height)
        // first frame uses global color table; subsequent frames use local
        out!!.write(if (firstFrame) 0 else 0x80 or palSize)
    }

    @Throws(IOException::class)
    private fun writePalette() {
        out!!.write(colorTab)
        // Pad (or truncate is impossible since NeuQuant emits exactly paletteColors)
        // to 128*3 bytes so the GCT size field (6) matches the written table.
        val pad = 3 * paletteColors - colorTab.size
        repeat(maxOf(0, pad)) { out!!.write(0) }
    }

    @Throws(IOException::class)
    private fun writePixels() {
        LzwEncoder(width, height, indexedPixels, colorDepth).encode(out!!)
    }

    @Throws(IOException::class)
    private fun writeShort(value: Int) {
        out!!.write(value and 0xff)
        out!!.write((value shr 8) and 0xff)
    }

    @Throws(IOException::class)
    private fun writeString(s: String) {
        s.forEach { out!!.write(it.code) }
    }
}
