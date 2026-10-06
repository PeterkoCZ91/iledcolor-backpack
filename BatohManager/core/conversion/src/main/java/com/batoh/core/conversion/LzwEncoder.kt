package com.batoh.core.conversion

import java.io.IOException
import java.io.OutputStream

/**
 * LZW encoder for GIF image data.
 * Ported from Kevin Weiner's Java implementation (public domain).
 */
internal class LzwEncoder(
    private val imgW: Int,
    private val imgH: Int,
    private val pixAry: ByteArray,
    colorDepth: Int
) {
    companion object {
        private const val MAXBITS = 12
        private const val HSIZE = 5003
    }

    private val initCodeSize = maxOf(2, colorDepth)
    private var remaining = imgW * imgH
    private var curPixel = 0

    private var nBits = 0
    private var maxCode = 0
    private val maxmaxcode = 1 shl MAXBITS
    private val htab = IntArray(HSIZE)
    private val codetab = IntArray(HSIZE)
    private var freeEnt = 0
    private var clearFlg = false
    private var curAccum = 0
    private var curBits = 0

    private val masks = intArrayOf(
        0x0000, 0x0001, 0x0003, 0x0007, 0x000F,
        0x001F, 0x003F, 0x007F, 0x00FF, 0x01FF,
        0x03FF, 0x07FF, 0x0FFF, 0x1FFF, 0x3FFF,
        0x7FFF, 0xFFFF
    )

    private var aCount = 0
    private val accum = ByteArray(256)

    private fun maxcode(nBits: Int) = (1 shl nBits) - 1

    @Throws(IOException::class)
    fun encode(os: OutputStream) {
        os.write(initCodeSize)
        remaining = imgW * imgH
        curPixel = 0
        compress(initCodeSize + 1, os)
        os.write(0)
    }

    @Throws(IOException::class)
    private fun compress(initBits: Int, outs: OutputStream) {
        nBits = initBits
        maxCode = maxcode(nBits)
        val clearCode = 1 shl (initBits - 1)
        val eofCode = clearCode + 1
        freeEnt = clearCode + 2
        clearFlg = false
        curAccum = 0
        curBits = 0
        aCount = 0
        var ent = nextPixel()
        var hshift = 0
        var fcode = HSIZE
        while (fcode < 65536) { ++hshift; fcode *= 2 }
        hshift = 8 - hshift
        val hsizeReg = HSIZE
        clHash(hsizeReg)
        output(clearCode, outs)

        var c: Int
        outer@ while (nextPixel().also { c = it } != -1) {
            fcode = (c shl MAXBITS) + ent
            var i = (c shl hshift) xor ent
            if (htab[i] == fcode) {
                ent = codetab[i]
                continue
            } else if (htab[i] >= 0) {
                var disp = hsizeReg - i
                if (i == 0) disp = 1
                do {
                    i -= disp
                    if (i < 0) i += hsizeReg
                    if (htab[i] == fcode) {
                        ent = codetab[i]
                        continue@outer
                    }
                } while (htab[i] >= 0)
            }
            output(ent, outs)
            ent = c
            if (freeEnt < maxmaxcode) {
                codetab[i] = freeEnt++
                htab[i] = fcode
            } else {
                clBlock(outs)
            }
        }
        output(ent, outs)
        output(eofCode, outs)
        // Flush remaining bits (the final partial byte of LZW data)
        if (curBits > 0) {
            charOut((curAccum and 0xff).toByte(), outs)
        }
        flushChar(outs)
    }

    private fun nextPixel(): Int {
        if (remaining == 0) return -1
        --remaining
        return pixAry[curPixel++].toInt() and 0xff
    }

    @Throws(IOException::class)
    private fun output(code: Int, outs: OutputStream) {
        curAccum = curAccum and masks[curBits]
        curAccum = if (curBits > 0) curAccum or (code shl curBits) else code
        curBits += nBits
        while (curBits >= 8) {
            charOut((curAccum and 0xff).toByte(), outs)
            curAccum = curAccum shr 8
            curBits -= 8
        }
        // The decoder adds dictionary entries one emitted code behind the encoder.
        // Write this code at the old width, then advance before the next code.
        // This also handles the final data code before EOF and clear codes.
        if (clearFlg) {
            nBits = initCodeSize + 1
            maxCode = maxcode(nBits)
            clearFlg = false
        } else if (freeEnt > maxCode) {
            ++nBits
            maxCode = if (nBits == MAXBITS) maxmaxcode else maxcode(nBits)
        }
    }

    @Throws(IOException::class)
    private fun clBlock(outs: OutputStream) {
        clHash(HSIZE)
        freeEnt = (1 shl initCodeSize) + 2
        clearFlg = true
        // Clear code is 1 << (initCodeSize), e.g. 256 for 8-bit color table.
        output(1 shl initCodeSize, outs)
    }

    private fun clHash(hsize: Int) {
        htab.fill(-1, 0, hsize)
    }

    @Throws(IOException::class)
    private fun charOut(c: Byte, outs: OutputStream) {
        accum[aCount++] = c
        if (aCount >= 254) flushChar(outs)
    }

    @Throws(IOException::class)
    private fun flushChar(outs: OutputStream) {
        if (aCount > 0) {
            outs.write(aCount)
            outs.write(accum, 0, aCount)
            aCount = 0
        }
    }
}
