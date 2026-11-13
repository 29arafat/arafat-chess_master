package com.arafat.chessmaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arafat.chessmaster.data.GameRecord
import com.arafat.chessmaster.data.replayTo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun dateText(id: Long): String =
    try { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(id)) } catch (e: Exception) { "" }

@Composable
private fun resultColor(r: String): Color {
    val p = LocalPalette.current
    return when (r) { "W" -> p.good; "L" -> p.bad; else -> p.warn }
}

private fun resultLabel(r: String): String = when (r) { "W" -> "🏆 জয়"; "L" -> "❌ হার"; else -> "🤝 ড্র" }

@Composable
fun HistoryScreen(vm: ChessViewModel) {
    val p = LocalPalette.current
    val list = vm.history
    val wins = list.count { it.result == "W" }
    val losses = list.count { it.result == "L" }
    val draws = list.count { it.result == "D" }
    val rate = if (list.isEmpty()) 0 else wins * 100 / list.size

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Text("📊 আমার পরিসংখ্যান", color = p.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("মোট গেম", "${list.size}", p.primary, Modifier.weight(1f))
            StatBox("জয়", "$wins", p.good, Modifier.weight(1f))
            StatBox("হার", "$losses", p.bad, Modifier.weight(1f))
            StatBox("ড্র", "$draws", p.warn, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text("জয়ের হার: $rate%", color = p.sub, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))

        if (list.isEmpty()) {
            Panel("এখনো কোনো গেম নেই", p.sub) {
                Text(
                    "গেম শেষ হলে বা 🏁 নতুন গেম চাপলে ফলাফল সিলেক্ট করুন — এখানে সব গেম ও চাল সেভ হবে।",
                    color = p.sub, fontSize = 13.sp, lineHeight = 19.sp
                )
            }
        }
        for (g in list) {
            val col = resultColor(g.result)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(p.surface)
                    .clickable { vm.openGame(g) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(col.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(resultLabel(g.result), color = col, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (g.myWhite) "♔ সাদা" else "♚ কালো") + " • ${g.moves.size} চাল",
                        color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                    Text(dateText(g.id), color = p.sub, fontSize = 11.sp)
                    Text(
                        g.moves.take(6).joinToString(" ") { it.san },
                        color = p.sub, fontSize = 12.sp, maxLines = 1
                    )
                }
                Text("›", color = p.sub, fontSize = 22.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun ReplayScreen(vm: ChessViewModel, rec: GameRecord) {
    val p = LocalPalette.current
    val idx = vm.replayIndex.coerceIn(0, rec.moves.size)
    val state = remember(rec, idx) { replayTo(rec, idx) }
    val col = resultColor(rec.result)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.closeGame() }) { Text("← ফিরুন") }
            Spacer(Modifier.weight(1f))
            Text(resultLabel(rec.result), color = col, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Text(
            dateText(rec.id) + " • " + (if (rec.myWhite) "আমি সাদা" else "আমি কালো") + " • ${rec.moves.size} চাল",
            color = p.sub, fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))

        BoardView(
            pos = state.first,
            whiteBottom = rec.myWhite,
            selected = -1,
            targets = emptySet(),
            lastMove = state.second,
            hint = -1,
            onTap = {}
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { vm.replayIndex = 0 }) { Text("⏮") }
            TextButton(onClick = { vm.replayIndex = (idx - 1).coerceAtLeast(0) }) { Text("◀ আগের") }
            TextButton(onClick = { vm.replayIndex = (idx + 1).coerceAtMost(rec.moves.size) }) { Text("পরের ▶") }
            TextButton(onClick = { vm.replayIndex = rec.moves.size }) { Text("⏭") }
        }

        if (idx > 0) {
            val m = rec.moves[idx - 1]
            Panel(
                "চাল $idx: ${m.san} • " + if (m.mine) "আমার চাল" else "শত্রুর চাল",
                if (m.mine) p.primary else p.info
            ) {
                if (m.why.isNotBlank()) {
                    m.why.split("\n").filter { it.isNotBlank() }.forEach {
                        Text(it, color = p.text, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                } else {
                    Text("এই চালের জন্য কোনো ব্যাখ্যা সংরক্ষিত নেই।", color = p.sub, fontSize = 13.sp)
                }
            }
        }

        Panel("📜 সব চাল (ট্যাপ করে দেখুন)", p.sub) {
            var num = 1
            for ((i, r) in rec.moves.withIndex()) {
                val label = (if (r.whiteMoved) "$num." else "$num…") + " ${r.san}"
                val sel = (i + 1) == idx
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) p.primary.copy(alpha = 0.25f) else Color.Transparent)
                        .clickable { vm.replayIndex = i + 1 }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(if (r.mine) "আমি" else "শত্রু", color = if (r.mine) p.primary else p.sub, fontSize = 12.sp)
                }
                if (!r.whiteMoved) num++
            }
        }

        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { vm.deleteGame(rec) }) { Text("🗑 এই গেম মুছুন", color = p.bad) }
        Spacer(Modifier.height(16.dp))
    }
}
