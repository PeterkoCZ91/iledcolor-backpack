package com.batoh.core.data.repository

import com.batoh.core.domain.model.AspectRatio
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.GifFilter
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

private const val SQUARE_MIN = 0.90
private const val SQUARE_MAX = 1.10
private const val WIDE_MIN = 1.25
private const val TALL_MAX = 0.80

internal fun List<Gif>.smartFilterAndRank(filter: GifFilter): List<Gif> {
    val ratioFiltered = filterByAspectRatio(filter.aspectRatio)
    val withDimensions = ratioFiltered.filter { it.width > 0 && it.height > 0 }
    val base = if (withDimensions.isNotEmpty()) withDimensions else ratioFiltered

    return base
        .distinctBy { "${it.source}|${it.id}|${it.originalUrl}" }
        .sortedByDescending { it.qualityScore(filter) }
}

private fun List<Gif>.filterByAspectRatio(ratio: AspectRatio?): List<Gif> {
    if (ratio == null) return this
    return when (ratio) {
        AspectRatio.SQUARE -> filter {
            it.width > 0 && it.height > 0 &&
                (it.width.toDouble() / it.height.toDouble()) in SQUARE_MIN..SQUARE_MAX
        }
        AspectRatio.WIDE -> filter {
            it.width > 0 && it.height > 0 &&
                (it.width.toDouble() / it.height.toDouble()) >= WIDE_MIN
        }
        AspectRatio.TALL -> filter {
            it.width > 0 && it.height > 0 &&
                (it.width.toDouble() / it.height.toDouble()) <= TALL_MAX
        }
    }
}

private fun Gif.qualityScore(filter: GifFilter): Double {
    val safeWidth = width.coerceAtLeast(1).toDouble()
    val safeHeight = height.coerceAtLeast(1).toDouble()
    val ratio = safeWidth / safeHeight

    val targetRatio = when (filter.aspectRatio) {
        AspectRatio.SQUARE -> 1.0
        AspectRatio.WIDE -> 1.6
        AspectRatio.TALL -> 0.62
        null -> 1.0
    }

    val ratioDistance = abs(ln(ratio / targetRatio))
    val ratioScore = (1.0 - (ratioDistance / 1.2)).coerceIn(0.0, 1.0)

    val minDim = min(safeWidth, safeHeight)
    val maxDim = max(safeWidth, safeHeight)
    val minDimScore = (minDim / 96.0).coerceIn(0.0, 1.0)
    val oversizePenalty = ((maxDim - 640.0) / 1280.0).coerceIn(0.0, 0.35)
    val sizeScore = (minDimScore - oversizePenalty).coerceIn(0.0, 1.0)

    val motionScore = if (mp4Url.isNotBlank()) 1.0 else 0.7
    return ratioScore * 0.65 + sizeScore * 0.30 + motionScore * 0.05
}
