package com.arafat.chessmaster.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arafat.chessmaster.R
import com.arafat.chessmaster.engine.Analyzer

@Composable
fun App(vm: ChessViewModel) {
    ChessTheme(vm.dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (vm.myWhite == null) StartScreen(vm) else GameScreen(vm)
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) cs.primary else cs.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            color = if (selected) cs.onPrimary else cs.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun StartScreen(vm: ChessViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F1B33))
                .border(3.dp, Color(0xFFF2C14E), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(170.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Arafat Chess Master",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "আপনার ব্যক্তিগত দাবা ইঞ্জিন — প্রতিটি চালের গাণিতিক বিশ্লেষণ, ফাঁদ শনাক্তকরণ ও আক্রমণ পরিকল্পনা",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(28.dp))
        Text("আপনার মোড বেছে নিন", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { vm.start(true) },
                modifier = Modifier.weight(1f).height(96.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5F5F0), contentColor = Color(0xFF111111))
            ) {
                Text("♔ সাদা\nWhite মোড\nআমি আগে চাল দেব", textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Button(
                onClick = { vm.start(false) },
                modifier = Modifier.weight(1f).height(96.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B1F2A), contentColor = Color.White)
            ) {
                Text("♚ কালো\nBlack মোড\nশত্রু আগে চাল দেবে", textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("ইঞ্জিনের শক্তি", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("⚡ দ্রুত", vm.level == 0) { vm.level = 0 }
            Pill("⚖ ব্যালান্সড", vm.level == 1) { vm.level = 1 }
            Pill("🧠 ডিপ", vm.level == 2) { vm.level = 2 }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { vm.dark = !vm.dark }) {
            Text(if (vm.dark) "☀ লাইট থিম" else "🌙 ডার্ক থিম")
        }
    }
}

@Composable
fun InfoCard(title: String, lines: List<String>, accent: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
            Column(Modifier.padding(12.dp)) {
                Text(title, fontWeight = FontWeight.Bold, color = accent, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                lines.forEach { Text("• $it", fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }
    }
}

@Composable
fun EvalBar(whiteCp: Int) {
    val frac = ((whiteCp.coerceIn(-600, 600) + 600) / 1200f).coerceIn(0.03f, 0.97f)
    Row(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        Box(Modifier.weight(frac).fillMaxHeight().background(Color.White))
        Box(Modifier.weight(1f - frac).fillMaxHeight().background(Color(0xFF222222)))
    }
}

@Composable
fun GameScreen(vm: ChessViewModel) {
    val my = vm.myWhite ?: true
    val pos = vm.pos
    val myTurn = pos.white == my
    val an = vm.analysis
    val hint = if (myTurn && an != null && an.candidates.isNotEmpty()) an.candidates[0].move else -1
    val situation = remember(pos) { Analyzer.situation(pos, vm.plyCount) }
    val cs = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "♚ Arafat Chess Master",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = cs.primary,
                modifier = Modifier.weight(1f)
            )
            Text(if (my) "আপনি: সাদা" else "আপনি: কালো", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { vm.undo() }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("↩ আনডু") }
            TextButton(onClick = { vm.fenDialog = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("FEN") }
            TextButton(onClick = { vm.dark = !vm.dark }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("🌗 থিম") }
            TextButton(onClick = { vm.backToStart() }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("🔄 নতুন") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("⚡", vm.level == 0) { vm.setLevelAndRefresh(0) }
            Pill("⚖", vm.level == 1) { vm.setLevelAndRefresh(1) }
            Pill("🧠", vm.level == 2) { vm.setLevelAndRefresh(2) }
        }
        Spacer(Modifier.height(8.dp))
        EvalBar(an?.whiteCp ?: 0)
        Spacer(Modifier.height(8.dp))

        val banner = vm.status ?: if (myTurn) "✅ আপনার চাল — ইঞ্জিনের সাজেশন নিচে" else "⏳ শত্রুর চাল — শত্রু যে ঘুঁটি চালালো সেটায় ট্যাপ করে গন্তব্য ঘরে ট্যাপ করুন"
        Card(
            colors = CardDefaults.cardColors(containerColor = if (vm.status != null) cs.primary else cs.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                banner,
                modifier = Modifier.padding(10.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (vm.status != null) cs.onPrimary else cs.onSurface
            )
        }
        Spacer(Modifier.height(8.dp))

        BoardView(
            pos = pos,
            whiteBottom = my,
            selected = vm.selected,
            targets = vm.targets,
            lastMove = vm.lastMove,
            hint = hint,
            onTap = { vm.tap(it) }
        )
        Spacer(Modifier.height(8.dp))

        if (vm.status == null && myTurn) {
            if (vm.thinking) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("ইঞ্জিন জটিল হিসাব করছে…", fontSize = 14.sp)
                }
            }
            if (an != null && an.candidates.isNotEmpty()) {
                val best = an.candidates[0]
                Card(
                    colors = CardDefaults.cardColors(containerColor = cs.surface),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("🎯 সেরা চাল (গভীরতা ${an.depth})", color = cs.primary, fontWeight = FontWeight.Bold)
                        Text(best.fromTo, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("${best.san}  •  মূল্যায়ন ${best.scoreText}", fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        best.tags.forEach { Text("✦ $it", fontSize = 13.sp, modifier = Modifier.padding(vertical = 1.dp)) }
                        if (best.pvSan.isNotEmpty()) Text("লাইন: ${best.pvSan}", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.playCandidate(0) }) { Text("✔ এই চাল খেলুন") }
                    }
                }
                if (an.candidates.size > 1) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cs.surface),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("বিকল্প চাল", fontWeight = FontWeight.Bold, color = cs.secondary)
                            for (i in 1 until an.candidates.size) {
                                val c = an.candidates[i]
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${c.fromTo}  (${c.san})  ${c.scoreText}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        if (c.tags.isNotEmpty()) Text(c.tags.first(), fontSize = 12.sp)
                                    }
                                    TextButton(onClick = { vm.playCandidate(i) }) { Text("খেলুন") }
                                }
                            }
                        }
                    }
                }
                if (an.threats.isNotEmpty()) InfoCard("⚠ শত্রুর হুমকি", an.threats, Color(0xFFEF4444))
                if (an.traps.isNotEmpty()) InfoCard("🪤 ফাঁদ শনাক্ত", an.traps, Color(0xFFF59E0B))
                if (an.attacks.isNotEmpty()) InfoCard("⚔ আক্রমণ পরিকল্পনা", an.attacks, Color(0xFF22C55E))
                InfoCard("🛡 রাজার নিরাপত্তা", an.king, Color(0xFF3B82F6))
                InfoCard("🧭 কৌশল", listOf(an.plan), cs.primary)
            }
        }

        InfoCard("♟ ফিল্ডের অবস্থা", situation, cs.secondary)
        InfoCard("📜 চালের তালিকা", listOf(vm.moveText()), cs.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(24.dp))
    }

    val pr = vm.promo
    if (pr != null) {
        AlertDialog(
            onDismissRequest = { vm.promo = null },
            confirmButton = {},
            title = { Text("প্রোমোশন: কোন ঘুঁটি হবে?") },
            text = {
                Column {
                    listOf(5, 4, 3, 2).forEach { t ->
                        TextButton(onClick = { vm.choosePromo(t) }) { Text("${glyph(t)} ${Analyzer.NAME[t]}", fontSize = 20.sp) }
                    }
                }
            }
        )
    }

    if (vm.fenDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { vm.fenDialog = false },
            title = { Text("FEN থেকে পজিশন লোড") },
            text = {
                Column {
                    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("FEN লিখুন") })
                    vm.fenError?.let { Text(it, color = Color(0xFFEF4444), fontSize = 12.sp) }
                }
            },
            confirmButton = { TextButton(onClick = { vm.loadFen(text) }) { Text("লোড") } },
            dismissButton = { TextButton(onClick = { vm.fenDialog = false }) { Text("বাতিল") } }
        )
    }
}
