package com.example.domain.model

import kotlin.math.ceil

internal object InkTextLayout {
    private val token = Regex("\\[\\[inkspace:([0-9]{1,7})]]")
    private val line = Regex("(?m)^\\[\\[inkspace:[0-9]{1,7}]](?:[ \\t]*)$")
    private const val MAX_TOP_DP = 1_000_000

    fun encode(minimumTopDp: Int): String {
        require(minimumTopDp in 0..MAX_TOP_DP)
        return "[[inkspace:$minimumTopDp]]"
    }

    fun decode(text: String): Int? = token.matchEntire(text.trim())?.groupValues?.get(1)?.toIntOrNull()
        ?.takeIf { it in 0..MAX_TOP_DP }

    fun belowInk(bottomDp: Float): Int {
        require(bottomDp.isFinite() && bottomDp >= 0f)
        return ceil(bottomDp + 16f).toInt().coerceAtMost(MAX_TOP_DP)
    }

    fun gapBefore(minimumTopDp: Int, currentTopDp: Float): Float =
        if (currentTopDp.isFinite()) (minimumTopDp - currentTopDp).coerceAtLeast(0f) else 0f

    fun strip(content: String): String = line.replace(content, "")
}