package com.arafat.chessmaster.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arafat.chessmaster.R

@Composable
fun SettingsScreen(vm: ChessViewModel) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Text("⚙ সেটিংস", color = p.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))

        Panel("🎨 থিম", p.primary) {
            PALETTES.forEachIndexed { i, pal ->
                val sel = vm.palette == i
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (sel) p.primary.copy(alpha = 0.18f) else p.surface2)
                        .border(1.dp, if (sel) p.primary else p.surface2, RoundedCornerShape(14.dp))
                        .clickable { vm.changePalette(i) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(pal.name, color = p.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Row {
                        Swatch(pal.bg); Swatch(pal.primary); Swatch(pal.lightSq); Swatch(pal.darkSq)
                    }
                    if (sel) Text("  ✔", color = p.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        Panel("🧠 ডিফল্ট চিন্তার স্তর", p.gold) {
            LevelPills(vm.level) { vm.setLevelAndRefresh(it) }
            Spacer(Modifier.height(6.dp))
            Text("প্রতি চালে ভাবার সময়: ${LEVEL_TIMES[vm.level.coerceIn(0, 3)]}", color = p.sub, fontSize = 12.sp)
            Text("স্তর যত বেশি, বিশ্লেষণ তত গভীর (ব্যাটারি ও সময়ও বেশি লাগে)।", color = p.sub, fontSize = 12.sp)
        }

        Panel("🤖 ইঞ্জিন", p.info) {
            Text(vm.engineName, color = p.text, fontWeight = FontWeight.SemiBold)
            Text(
                if (vm.stockfishPacked) "Stockfish এই APK-র ভেতরে প্যাক করা আছে — অফলাইনে চলে।"
                else "এই বিল্ডে Stockfish নেই; ইনবিল্ট ইঞ্জিন ব্যবহার হচ্ছে।",
                color = p.sub, fontSize = 12.sp
            )
        }

        Panel("ℹ অ্যাপ সম্পর্কে", p.sub) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(64.dp))
                Column(Modifier.padding(start = 10.dp)) {
                    Text("Arafat Chess Master", color = p.text, fontWeight = FontWeight.Bold)
                    Text("ভার্সন 2.0 • Your Chess Assistant", color = p.sub, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun Swatch(c: androidx.compose.ui.graphics.Color) {
    Spacer(
        Modifier
            .padding(horizontal = 2.dp)
            .size(18.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(c)
    )
}
