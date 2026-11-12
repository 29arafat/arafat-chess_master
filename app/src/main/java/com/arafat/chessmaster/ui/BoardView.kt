package com.arafat.chessmaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arafat.chessmaster.engine.Position
import kotlin.math.abs

fun glyph(type: Int): String {
    val ch = when (type) {
        6 -> '\u265A'
        5 -> '\u265B'
        4 -> '\u265C'
        3 -> '\u265D'
        2 -> '\u265E'
        else -> '\u265F'
    }
    return "$ch\uFE0E"
}

@Composable
fun BoardView(
    pos: Position,
    whiteBottom: Boolean,
    selected: Int,
    targets: Set<Int>,
    lastMove: Int,
    hint: Int,
    onTap: (Int) -> Unit
) {
    val light = Color(0xFFE9E4D4)
    val darkSq = Color(0xFF6F8FAF)
    val lf = if (lastMove >= 0) lastMove and 63 else -1
    val lt = if (lastMove >= 0) (lastMove shr 6) and 63 else -1
    val hf = if (hint >= 0) hint and 63 else -1
    val ht = if (hint >= 0) (hint shr 6) and 63 else -1

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
    ) {
        val cell = maxWidth / 8
        val fs = with(LocalDensity.current) { (cell.toPx() * 0.72f).toSp() }
        Column(Modifier.fillMaxSize()) {
            for (row in 0 until 8) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0 until 8) {
                        val r = if (whiteBottom) row else 7 - row
                        val c = if (whiteBottom) col else 7 - col
                        val sq = r * 8 + c
                        val piece = pos.b[sq]
                        val isLight = (r + c) % 2 == 0
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(if (isLight) light else darkSq)
                                .clickable { onTap(sq) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (sq == lf || sq == lt) {
                                Box(Modifier.fillMaxSize().background(Color(0x66F2C14E)))
                            }
                            if (sq == selected) {
                                Box(Modifier.fillMaxSize().background(Color(0x99F2C14E)))
                            }
                            if (sq == hf || sq == ht) {
                                Box(Modifier.fillMaxSize().border(3.dp, Color(0xFF22C55E)))
                            }
                            if (piece != 0) {
                                val white = piece > 0
                                Text(
                                    text = glyph(abs(piece)),
                                    fontSize = fs,
                                    color = if (white) Color(0xFFFFFFFF) else Color(0xFF111111),
                                    softWrap = false,
                                    style = TextStyle(
                                        shadow = Shadow(
                                            color = if (white) Color(0xFF000000) else Color(0x88FFFFFF),
                                            offset = Offset(0f, 0f),
                                            blurRadius = 7f
                                        )
                                    )
                                )
                            }
                            if (sq in targets) {
                                if (piece == 0) {
                                    Box(
                                        Modifier
                                            .size(cell * 0.3f)
                                            .clip(CircleShape)
                                            .background(Color(0x66000000))
                                    )
                                } else {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .border(3.dp, Color(0xAAEF4444), RoundedCornerShape(50))
                                    )
                                }
                            }
                            if (col == 0) {
                                Text(
                                    "${8 - r}",
                                    fontSize = 9.sp,
                                    color = if (isLight) darkSq else light,
                                    modifier = Modifier.align(Alignment.TopStart).padding1()
                                )
                            }
                            if (row == 7) {
                                Text(
                                    "${'a' + c}",
                                    fontSize = 9.sp,
                                    color = if (isLight) darkSq else light,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding1()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.padding1(): Modifier = this.then(Modifier.padding(horizontal = 2.dp, vertical = 1.dp))
