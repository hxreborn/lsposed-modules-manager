package com.rushi.modulesmanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import java.security.MessageDigest

private const val IDENTICON_CELLS = 5
private val IdenticonBackground = Color(0xFFF0F0F0)

private data class IdenticonSpec(
    val cells: Array<BooleanArray>,
    val color: Color,
)

/**
 * GitHub-style default avatar: a mirrored 5x5 pixel pattern and a color, both
 * derived from the module's package name. Used when a repo has no avatar of
 * its own, so cards never fall back to a letter.
 */
@Composable
fun Identicon(seed: String, modifier: Modifier = Modifier) {
    val spec = remember(seed) { identiconSpec(seed) }
    Canvas(modifier.fillMaxSize()) {
        drawRect(IdenticonBackground)
        val margin = size.minDimension * 0.12f
        val cell = (size.minDimension - margin * 2f) / IDENTICON_CELLS
        for (row in 0 until IDENTICON_CELLS) {
            for (column in 0 until IDENTICON_CELLS) {
                if (!spec.cells[row][column]) continue
                drawRect(
                    color = spec.color,
                    topLeft = Offset(margin + column * cell, margin + row * cell),
                    size = Size(cell, cell),
                )
            }
        }
    }
}

private fun identiconSpec(seed: String): IdenticonSpec {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(seed.toByteArray(Charsets.UTF_8))
    val cells = Array(IDENTICON_CELLS) { BooleanArray(IDENTICON_CELLS) }
    var bit = 0
    for (row in 0 until IDENTICON_CELLS) {
        for (column in 0 until IDENTICON_CELLS / 2) {
            val filled = (digest[bit / 8].toInt() shr (bit % 8)) and 1 == 1
            cells[row][column] = filled
            cells[row][IDENTICON_CELLS - 1 - column] = filled
            bit++
        }
    }
    val hue = (((digest[16].toInt() and 0xFF) shl 8) or (digest[17].toInt() and 0xFF)) /
        65535f * 360f
    return IdenticonSpec(cells = cells, color = Color.hsl(hue, 0.52f, 0.62f))
}
