package com.arafat.chessmaster.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arafat.chessmaster.R
import com.arafat.chessmaster.engine.Analyzer

val LEVEL_LABELS = listOf("⚡ দ্রুত", "⚖ ব্যালান্স", "🧠 ডিপ", "🔥 ডিপার")
val LEVEL_TIMES = listOf("১.৫ সেকেন্ড", "৪ সেকেন্ড", "৯ সেকেন্ড", "২০ সেকেন্ড")

@Composable
fun LevelPills(level: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until 4) {
            Pill(LEVEL_LABELS[i], level == i, Modifier.weight(1f)) { onPick(i) }
        }
    }
}

@Composable
fun SideCard(glyphText: String, title: String, sub: String, light: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val brush = if (light) Brush.verticalGradient(listOf(Color(0xFFFAFAF5), Color(0xFFD5D9E6)))
    else Brush.verticalGradient(listOf(Color(0xFF2C3450), Color(0xFF0B0F1A)))
    val fg = if (light) Color(0xFF101524) else Color.White
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(brush)
            .clickable { onClick() }
            .padding(vertical = 20.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(glyphText, fontSize = 46.sp, color = fg)
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = fg)
        Text(sub, fontSize = 12.sp, color = fg.copy(alpha = 0.75f), textAlign = TextAlign.Center)
    }
}

@Composable
fun StartScreen(vm: ChessViewModel) {
    val p = LocalPalette.current
    val wins = vm.history.count { it.result == "W" }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(230.dp))
        Text("আপনার ব্যক্তিগত দাবা সহকারী", color = p.sub, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        Text("নতুন গেম — আপনার মোড বেছে নিন", color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SideCard("♔", "সাদা মোড", "আমি প্রথম চাল দেব", true, Modifier.weight(1f)) { vm.start(true) }
            SideCard("♚", "কালো মোড", "শত্রু আগে চাল দেবে", false, Modifier.weight(1f)) { vm.start(false) }
        }
        Spacer(Modifier.height(22.dp))
        Text("চিন্তার স্তর", color = p.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        LevelPills(vm.level) { vm.setLevelAndRefresh(it) }
        Spacer(Modifier.height(6.dp))
        Text(
            "প্রতি চালে ভাবার সময়: ${LEVEL_TIMES[vm.level.coerceIn(0, 3)]}",
            color = p.sub, fontSize = 12.sp
        )
        Spacer(Modifier.height(18.dp))
        Panel("⚙ ইঞ্জিন", p.primary) {
            Text(vm.engineName, color = p.text, fontWeight = FontWeight.SemiBold)
            Text("সম্পূর্ণ অফলাইন — ইন্টারনেট লাগে না", color = p.sub, fontSize = 12.sp)
        }
        if (vm.history.isNotEmpty()) {
            Text(
                "মোট গেম ${vm.history.size} • জয় $wins",
                color = p.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
fun GameScreen(vm: ChessViewModel) {
    val p = LocalPalette.current
    val my = vm.myWhite ?: true
    val pos = vm.pos
    val myTurn = pos.white == my
    val an = vm.analysis
    val best = if (myTurn && an != null) an.candidates.firstOrNull() else null
    val hint = best?.move ?: -1
    val situation = remember(pos) { Analyzer.situation(pos, vm.plyCount) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.logo), null, Modifier.size(44.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Arafat Chess Master", color = p.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "⚙ ${vm.engineName}" + if (vm.thinking && vm.liveDepth > 0) " • গভীরতা ${vm.liveDepth}" else "",
                    color = p.sub, fontSize = 11.sp
                )
            }
            Pill(if (my) "♔ আমি: সাদা" else "♚ আমি: কালো", true) {}
        }
        Spacer(Modifier.height(8.dp))
        EvalBar(an?.whiteCp ?: 0)
        if (an != null && an.candidates.isNotEmpty()) {
            Text(
                "মূল্যায়ন (সাদার দিক থেকে): " + Analyzer.scoreText(an.whiteCp),
                color = p.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.height(8.dp))

        if (vm.status == null && myTurn && best != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(p.surface2)
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "বেস্ট মুভ  " + best.fromTo.replace("→", "➡️"),
                    color = p.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF22C55E))
                        .clickable { vm.playCandidate(0) }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text("▶ এক্সিকিউট", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        } else {
            val banner = vm.status
                ?: if (myTurn) {
                    if (vm.thinking) "⏳ ইঞ্জিন গভীর হিসাব করছে…" else "✅ আপনার চাল"
                } else {
                    "⏳ শত্রুর চাল — শত্রু যে ঘুঁটি চালালো সেটায় ট্যাপ করে গন্তব্য ঘরে ট্যাপ করুন"
                }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (vm.status != null) p.primary else p.surface2)
                    .padding(10.dp)
            ) {
                Text(
                    banner,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (vm.status != null) p.onPrimary else p.text
                )
            }
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
        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { vm.undo() }, enabled = vm.canUndo, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("↩ আনডু") }
            TextButton(onClick = { vm.fenDialog = true }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("📋 FEN") }
            TextButton(onClick = { vm.newGameClicked() }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("🏁 নতুন গেম") }
        }
        LevelPills(vm.level) { vm.setLevelAndRefresh(it) }
        Spacer(Modifier.height(8.dp))

        if (vm.status == null && myTurn) {
            if (vm.thinking) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("ইঞ্জিন গভীর হিসাব করছে…", fontSize = 14.sp, color = p.text)
                }
            }
            if (an != null && an.candidates.isNotEmpty() && best != null) {
                if (an.enemyNote != null) {
                    Panel("🔍 শত্রুর শেষ চালের উদ্দেশ্য", p.info) {
                        Text(an.enemyNote, color = p.text, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
                Panel("🎯 সেরা চাল • গভীরতা ${an.depth}", p.primary) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(best.fromTo, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = p.text)
                        Spacer(Modifier.width(10.dp))
                        Text(best.san, fontSize = 18.sp, color = p.sub)
                    }
                    Text("মূল্যায়ন ${best.scoreText}", color = p.sub, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    best.tags.forEach { Text("✦ $it", color = p.gold, fontSize = 13.sp) }
                    Spacer(Modifier.height(8.dp))
                    Text("❓ কেন এই চাল?", color = p.gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Bullets(best.why)
                    Spacer(Modifier.height(6.dp))
                    Text("🔭 দীর্ঘমেয়াদে কী হবে?", color = p.gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Bullets(best.longTerm)
                    if (best.pvSan.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("পরিকল্পিত লাইন: ${best.pvSan}", color = p.sub, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { vm.playCandidate(0) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.primary, contentColor = p.onPrimary)
                    ) { Text("✔ এই চাল খেলুন", fontWeight = FontWeight.Bold) }
                }
                if (an.candidates.size > 1) {
                    Panel("বিকল্প চাল", p.info) {
                        for (i in 1 until an.candidates.size) {
                            val c = an.candidates[i]
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${c.fromTo}  (${c.san})  ${c.scoreText}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                                    c.why.take(2).forEach { Text("• $it", fontSize = 12.sp, color = p.sub, lineHeight = 17.sp) }
                                }
                                TextButton(onClick = { vm.playCandidate(i) }) { Text("খেলুন") }
                            }
                        }
                    }
                }
                if (an.threats.isNotEmpty()) Panel("⚠ শত্রুর হুমকি", p.bad) { Bullets(an.threats) }
                if (an.traps.isNotEmpty()) Panel("🪤 ফাঁদ শনাক্ত", p.warn) { Bullets(an.traps) }
                if (an.attacks.isNotEmpty()) Panel("⚔ আক্রমণ পরিকল্পনা", p.good) { Bullets(an.attacks) }
                Panel("🛡 রাজার নিরাপত্তা", p.info) { Bullets(an.king) }
                Panel("🧭 কৌশল", p.gold) { Bullets(listOf(an.plan)) }
            }
        }

        Panel("♟ ফিল্ডের অবস্থা", p.sub) { Bullets(situation) }
        Panel("📜 চালের তালিকা", p.sub) {
            Text(vm.moveText(), color = p.text, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Spacer(Modifier.height(16.dp))
    }

    if (vm.promo != null) {
        AlertDialog(
            onDismissRequest = { vm.promo = null },
            confirmButton = {},
            title = { Text("প্রোমোশন: কোন ঘুঁটি হবে?") },
            text = {
                Column {
                    listOf(5, 4, 3, 2).forEach { t ->
                        TextButton(onClick = { vm.choosePromo(t) }) {
                            Text("${glyph(t)} ${Analyzer.NAME[t]}", fontSize = 20.sp)
                        }
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
                    val err = vm.fenError
                    if (err != null) Text(err, color = Color(0xFFEF4444), fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { vm.loadFen(text) }) { Text("লোড") } },
            dismissButton = { TextButton(onClick = { vm.fenDialog = false }) { Text("বাতিল") } }
        )
    }

    if (vm.newGameDialog) {
        AlertDialog(
            onDismissRequest = { vm.newGameDialog = false },
            title = { Text("গেম শেষ করুন") },
            text = { Text("এই গেমের ফলাফল কী? ফলাফল ইতিহাসে সেভ হবে।") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { vm.finishGame("W") }) { Text("🏆 জিতেছি") }
                    TextButton(onClick = { vm.finishGame("L") }) { Text("❌ হেরেছি") }
                    TextButton(onClick = { vm.finishGame("D") }) { Text("🤝 ড্র") }
                    TextButton(onClick = { vm.finishGame(null) }) { Text("🗑 সেভ না করে বাতিল") }
                }
            },
            dismissButton = { TextButton(onClick = { vm.newGameDialog = false }) { Text("ফিরে যান") } }
        )
    }
}
