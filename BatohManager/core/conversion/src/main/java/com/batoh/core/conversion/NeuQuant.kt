package com.batoh.core.conversion

/**
 * NeuQuant Neural-Net color quantizer.
 * Original algorithm: Copyright (c) 1994 Anthony Dekker.
 * Java port: Kevin Weiner (public domain).
 * Kotlin port: public domain.
 *
 * The network size ([netsize], number of output palette colors) is configurable.
 * iledcolor / iledeyes uses a 128-entry Global Color Table, so GifEncoder passes
 * netsize = 128. The algorithm scales cleanly with netsize: `network`, `freq`,
 * `bias`, `radpower` are all sized to NETSIZE, and the learn/map/colorMap math
 * uses NETSIZE consistently. `netindex` stays 256 because it is indexed by the
 * 8-bit green channel VALUE (0..255), not by a net position.
 */
internal class NeuQuant(
    private val thepicture: ByteArray,
    private val lengthcount: Int,
    private val samplefac: Int,
    private val netsize: Int = 256
) {
    // Instance-scoped size constants (were compile-time consts; now depend on netsize).
    private val NETSIZE = netsize
    private val MAXNETPOS = NETSIZE - 1
    private val INITRAD = NETSIZE shr 3
    private val INITRADIUS = INITRAD * RADIUSBIAS

    companion object {
        private const val PRIME1 = 499
        private const val PRIME2 = 491
        private const val PRIME3 = 487
        private const val PRIME4 = 503
        private const val MINPICBYTES = 3 * PRIME4
        private const val NETBIASSHIFT = 4
        private const val NCYCLES = 100
        private const val INTBIASSHIFT = 16
        private const val INTBIAS = 1 shl INTBIASSHIFT
        private const val GAMMASHIFT = 10
        private const val GAMMA = 1 shl GAMMASHIFT
        private const val BETASHIFT = 10
        private const val BETA = INTBIAS shr BETASHIFT
        private const val BETAGAMMA = INTBIAS shl (GAMMASHIFT - BETASHIFT)
        private const val RADIUSBIASSHIFT = 6
        private const val RADIUSBIAS = 1 shl RADIUSBIASSHIFT
        private const val RADIUSDEC = 30
        private const val ALPHABIASSHIFT = 10
        private const val INITALPHA = 1 shl ALPHABIASSHIFT
        private const val RADBIASSHIFT = 8
        private const val RADBIAS = 1 shl RADBIASSHIFT
        private const val ALPHARADBSHIFT = ALPHABIASSHIFT + RADBIASSHIFT
        private const val ALPHARADBIAS = 1 shl ALPHARADBSHIFT
    }

    // network: [netsize][4] — channels [0..2] follow the INPUT BUFFER byte order
    // (GifEncoder.getImagePixels stores R,G,B → [0]=R, [1]=G, [2]=B), [3] = index.
    // The original Weiner port used a BGR buffer, hence old "b,g,r" naming — the code
    // is order-agnostic as long as map() args match the buffer order. NO R/B swap here.
    private val network = Array(NETSIZE) { IntArray(4) }
    private val netindex = IntArray(256)
    private val bias = IntArray(NETSIZE)
    private val freq = IntArray(NETSIZE)
    private val radpower = IntArray(INITRAD)

    init {
        for (i in 0 until NETSIZE) {
            val v = (i shl (NETBIASSHIFT + 8)) / NETSIZE
            network[i][0] = v
            network[i][1] = v
            network[i][2] = v
            freq[i] = INTBIAS / NETSIZE
            bias[i] = 0
        }
    }

    fun process(): ByteArray {
        learn()
        unbiasnet()
        inxbuild()
        return colorMap()
    }

    /**
     * Map a pixel to the best palette entry index. Channel params are named by their
     * position in the input buffer: c0/c1/c2 = 1st/2nd/3rd byte of the pixel (here RGB,
     * see GifEncoder.getImagePixels). c1 must be the middle (green) channel — netindex
     * is built over network[][1].
     *
     * Never returns -1: max summed distance is 3*255=765 < initial bestd of 1000,
     * so the first candidate always sets `best`.
     */
    fun map(c0: Int, c1: Int, c2: Int): Int {
        var bestd = 1000
        var best = -1
        var i = netindex[c1]
        var j = i - 1
        while (i < NETSIZE || j >= 0) {
            if (i < NETSIZE) {
                val p = network[i]
                var dist = p[1] - c1
                if (dist >= bestd) {
                    i = NETSIZE
                } else {
                    i++
                    if (dist < 0) dist = -dist
                    var a = p[0] - c0
                    if (a < 0) a = -a
                    dist += a
                    if (dist < bestd) {
                        a = p[2] - c2
                        if (a < 0) a = -a
                        dist += a
                        if (dist < bestd) {
                            bestd = dist
                            best = p[3]
                        }
                    }
                }
            }
            if (j >= 0) {
                val p = network[j]
                var dist = c1 - p[1]
                if (dist >= bestd) {
                    j = -1
                } else {
                    j--
                    if (dist < 0) dist = -dist
                    var a = p[0] - c0
                    if (a < 0) a = -a
                    dist += a
                    if (dist < bestd) {
                        a = p[2] - c2
                        if (a < 0) a = -a
                        dist += a
                        if (dist < bestd) {
                            bestd = dist
                            best = p[3]
                        }
                    }
                }
            }
        }
        return best
    }

    private fun colorMap(): ByteArray {
        val map = ByteArray(3 * NETSIZE)
        val index = IntArray(NETSIZE)
        for (i in 0 until NETSIZE) index[network[i][3]] = i
        var k = 0
        for (i in 0 until NETSIZE) {
            val j = index[i]
            map[k++] = network[j][0].toByte()
            map[k++] = network[j][1].toByte()
            map[k++] = network[j][2].toByte()
        }
        return map
    }

    private fun inxbuild() {
        var previouscol = 0
        var startpos = 0
        for (i in 0 until NETSIZE) {
            val p = network[i]
            var smallpos = i
            var smallval = p[1]
            for (j in i + 1 until NETSIZE) {
                val q = network[j]
                if (q[1] < smallval) {
                    smallpos = j
                    smallval = q[1]
                }
            }
            val q = network[smallpos]
            if (i != smallpos) {
                var tmp = q[0]; q[0] = p[0]; p[0] = tmp
                tmp = q[1]; q[1] = p[1]; p[1] = tmp
                tmp = q[2]; q[2] = p[2]; p[2] = tmp
                tmp = q[3]; q[3] = p[3]; p[3] = tmp
            }
            if (smallval != previouscol) {
                netindex[previouscol] = (startpos + i) shr 1
                for (j in previouscol + 1 until smallval) netindex[j] = i
                previouscol = smallval
                startpos = i
            }
        }
        netindex[previouscol] = (startpos + MAXNETPOS) shr 1
        for (j in previouscol + 1..255) netindex[j] = MAXNETPOS
    }

    private fun learn() {
        val alphadec = 30 + (samplefac - 1) / 3
        val samplepixels = lengthcount / (3 * samplefac)
        var delta = samplepixels / NCYCLES
        if (delta == 0) delta = 1
        var alpha = INITALPHA
        var radius = INITRADIUS
        var rad = radius shr RADIUSBIASSHIFT
        if (rad <= 1) rad = 0
        for (i in 0 until rad) {
            radpower[i] = alpha * ((rad * rad - i * i) * RADBIAS / (rad * rad))
        }
        val step = when {
            lengthcount < MINPICBYTES -> 3
            lengthcount % PRIME1 != 0 -> 3 * PRIME1
            lengthcount % PRIME2 != 0 -> 3 * PRIME2
            lengthcount % PRIME3 != 0 -> 3 * PRIME3
            else -> 3 * PRIME4
        }
        var pix = 0
        var i = 0
        while (i < samplepixels) {
            // NOTE: variable names bb/gg/rr are inherited from the BGR-buffer original —
            // with our RGB buffer bb is actually R and rr is B. Harmless: the same
            // positional order is used consistently in learn/map/colorMap (see `network`).
            val bb = (thepicture[pix].toInt() and 0xff) shl NETBIASSHIFT
            val gg = (thepicture[pix + 1].toInt() and 0xff) shl NETBIASSHIFT
            val rr = (thepicture[pix + 2].toInt() and 0xff) shl NETBIASSHIFT
            val j = contest(bb, gg, rr)
            altersingle(alpha, j, bb, gg, rr)
            if (rad != 0) alterneigh(rad, j, bb, gg, rr)
            pix += step
            if (pix >= lengthcount) pix -= lengthcount
            i++
            if (i % delta == 0) {
                alpha -= alpha / alphadec
                radius -= radius / RADIUSDEC
                rad = radius shr RADIUSBIASSHIFT
                if (rad <= 1) rad = 0
                for (jj in 0 until rad) {
                    radpower[jj] = alpha * ((rad * rad - jj * jj) * RADBIAS / (rad * rad))
                }
            }
        }
    }

    private fun alterneigh(rad: Int, i: Int, b: Int, g: Int, r: Int) {
        val lo = maxOf(i - rad, -1)
        val hi = minOf(i + rad, NETSIZE)
        var j = i + 1
        var k = i - 1
        var m = 1
        while (j < hi || k > lo) {
            val a = radpower[m++]
            if (j < hi) {
                val p = network[j++]
                p[0] -= a * (p[0] - b) / ALPHARADBIAS
                p[1] -= a * (p[1] - g) / ALPHARADBIAS
                p[2] -= a * (p[2] - r) / ALPHARADBIAS
            }
            if (k > lo) {
                val p = network[k--]
                p[0] -= a * (p[0] - b) / ALPHARADBIAS
                p[1] -= a * (p[1] - g) / ALPHARADBIAS
                p[2] -= a * (p[2] - r) / ALPHARADBIAS
            }
        }
    }

    private fun altersingle(alpha: Int, i: Int, b: Int, g: Int, r: Int) {
        val n = network[i]
        n[0] -= alpha * (n[0] - b) / INITALPHA
        n[1] -= alpha * (n[1] - g) / INITALPHA
        n[2] -= alpha * (n[2] - r) / INITALPHA
    }

    private fun contest(b: Int, g: Int, r: Int): Int {
        var bestd = Int.MAX_VALUE
        var bestbiasd = bestd
        var bestpos = -1
        var bestbiaspos = -1
        for (i in 0 until NETSIZE) {
            val n = network[i]
            var dist = n[0] - b
            if (dist < 0) dist = -dist
            var a = n[1] - g
            if (a < 0) a = -a
            dist += a
            a = n[2] - r
            if (a < 0) a = -a
            dist += a
            if (dist < bestd) {
                bestd = dist
                bestpos = i
            }
            val biasdist = dist - (bias[i] shr (INTBIASSHIFT - NETBIASSHIFT))
            if (biasdist < bestbiasd) {
                bestbiasd = biasdist
                bestbiaspos = i
            }
            val betafreq = freq[i] shr BETASHIFT
            freq[i] -= betafreq
            bias[i] += betafreq shl GAMMASHIFT
        }
        freq[bestpos] += BETA
        bias[bestpos] -= BETAGAMMA
        return bestbiaspos
    }

    private fun unbiasnet() {
        for (i in 0 until NETSIZE) {
            network[i][0] = network[i][0] shr NETBIASSHIFT
            network[i][1] = network[i][1] shr NETBIASSHIFT
            network[i][2] = network[i][2] shr NETBIASSHIFT
            network[i][3] = i
        }
    }
}
