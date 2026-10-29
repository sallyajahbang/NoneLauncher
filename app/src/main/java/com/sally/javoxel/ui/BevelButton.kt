package com.sally.javoxel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Tombol gaya bevel 3D (bukan Material 3).
 * Sisi atas-kiri terang, sisi kanan-bawah gelap, dan muka tengah di antaranya.
 * Saat ditekan, warna sisi dibalik supaya terlihat tenggelam.
 */
@Composable
fun BevelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val lip = if (enabled) Color(0xFF0084FF) else Color(0xFF8A9BB0)
    val face = if (enabled) Color(0xFF0066C2) else Color(0xFF6B7A8C)
    val shade = if (enabled) Color(0xFF005CB0) else Color(0xFF55636F)
    val textColor = if (enabled) Color.White else Color(0xFFB0B8C2)

    val topLeft = if (pressed) shade else lip
    val bottomRight = if (pressed) lip else shade

    Box(
        modifier = modifier
            .height(56.dp)
            .defaultMinSize(minWidth = 120.dp)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .drawBehind {
                val w = size.width
                val h = size.height
                val b = h * 0.18f

                // Sisi gelap sebagai dasar, lalu lip terang di atas-kiri, lalu muka tengah.
                drawRect(bottomRight)

                val lipPath = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w - b, b)
                    lineTo(b, b)
                    lineTo(b, h - b)
                    lineTo(0f, h)
                    close()
                }
                drawPath(lipPath, topLeft)

                drawRect(
                    color = face,
                    topLeft = Offset(b, b),
                    size = Size(w - 2 * b, h - 2 * b),
                )
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, fontWeight = FontWeight.Bold)
    }
}
