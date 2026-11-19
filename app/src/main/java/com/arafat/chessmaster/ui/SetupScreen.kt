package com.arafat.chessmaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arafat.chessmaster.engine.Position
import kotlin.math.abs

@Composable
fun SetupScreen(vm: ChessViewModel) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.setupBack() }) { Text("← পিছনে") }
            Spacer(Modifier.weight(1f))
            Text("🛠 মডিফাই ম্যাচ • ধাপ ${vm.setupStep}/3", color = p.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(6.dp))
        when (vm.setupStep) {
            1 -> SetupStepOne(vm)
            2 -> SetupStepTwo(vm)
            else -> SetupStepThree(vm)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SetupStepOne(vm: ChessViewModel) {
    val p = LocalPalette.current
    Panel("১) ইঞ্জিন বেছে নিন", p.info) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("🐟 Stockfish", vm.gEngine == 0, Modifier.weight(1f)) { vm.setGameEngine(0) }
            Pill("♞ আরাফাত ইঞ্জিন", vm.gEngine == 1, Modifier.weight(1f)) { vm.setGameEngine(1) }
        }
        if (!vm.stockfishPacked) {
            Text("এই বিল্ডে Stockfish নেই", color = p.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
    Panel("২) থিঙ্কিং লেভেল", p.gold) {
        LevelPills(vm.gLevel) { vm.setGameLevel(it) }
        Text("প্রতি চালে ভাবার সময়: ${LEVEL_TIMES[vm.gLevel.coerceIn(0, 3)]}", color = p.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
    Panel("৩) আপনার মোড বেছে নিন", p.primary) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SideCard("♔", "সাদা মোড", "আমি সাদা খেলছি", true, Modifier.weight(1f)) {
                vm.setupWhite = true
                vm.setupStep = 2
            }
            SideCard("♚", "কালো মোড", "আমি কালো খেলছি", false, Modifier.weight(1f)) {
                vm.setupWhite = false
                vm.setupStep = 2
            }
        }
    }
}

@Composable
private fun PieceChip(code: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        code == 0 -> p.surface2
        code > 0 -> Color(0xFF4A5470)
        else -> Color(0xFFD7DCE8)
    }
    Box(
        modifier
            .height(46.dp)
            .clip(shape)
            .background(bg)
            .border(if (selected) 3.dp else 1.dp, if (selected) p.gold else p.sub.copy(alpha = 0.3f), shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (code == 0) {
            Text("🧹", fontSize = 20.sp)
        } else {
            Text(
                glyph(abs(code)),
                fontSize = 26.sp,
                color = if (code > 0) Color.White else Color(0xFF111111)
            )
        }
    }
}

@Composable
private fun SetupStepTwo(vm: ChessViewModel) {
    val p = LocalPalette.current
    Text(
        "নিচের তালিকা থেকে ঘুঁটি বেছে বোর্ডের ঘরে ট্যাপ করুন। 🧹 বেছে ঘরে ট্যাপ করলে ঘুঁটি মুছে যাবে।",
        color = p.sub, fontSize = 13.sp, lineHeight = 19.sp
    )
    Spacer(Modifier.height(8.dp))
    val ep = remember(vm.setupBoard) {
        val pp = Position()
        System.arraycopy(vm.setupBoard, 0, pp.b, 0, 64)
        pp
    }
    BoardView(
        pos = ep,
        whiteBottom = vm.setupWhite != vm.setupFlip,
        selected = -1,
        targets = emptySet(),
        lastMove = -1,
        hint = -1,
        onTap = { vm.setupTap(it) }
    )
    Spacer(Modifier.height(8.dp))
    val whites = listOf(6, 5, 4, 3, 2, 1)
    val blacks = listOf(-6, -5, -4, -3, -2, -1)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (c in whites) PieceChip(c, vm.setupTool == c, Modifier.weight(1f)) { vm.setupTool = c }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (c in blacks) PieceChip(c, vm.setupTool == c, Modifier.weight(1f)) { vm.setupTool = c }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PieceChip(0, vm.setupTool == 0, Modifier.weight(1f)) { vm.setupTool = 0 }
        Box(Modifier.weight(5f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { vm.setupClear() }) { Text("🗑 সব মুছুন") }
        TextButton(onClick = { vm.setupReset() }) { Text("♟ শুরুর অবস্থান") }
        TextButton(onClick = { vm.setupFlip = !vm.setupFlip }) { Text("🔄 ঘোরান") }
    }
    val err = vm.setupError
    if (err != null) {
        Text("⚠ $err", color = p.bad, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
    }
    Spacer(Modifier.height(6.dp))
    Button(
        onClick = { vm.setupNext() },
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = p.primary, contentColor = p.onPrimary)
    ) { Text("পরবর্তী ➜", fontWeight = FontWeight.Bold) }
}

@Composable
private fun YesNoRow(question: String, moved: Boolean, onPick: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Text(question, color = p.text, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("হ্যাঁ — ক্যাসেল বন্ধ", moved, Modifier.weight(1f)) { onPick(true) }
        Pill("না — ক্যাসেলের সুযোগ আছে", !moved, Modifier.weight(1f)) { onPick(false) }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SetupStepThree(vm: ChessViewModel) {
    val p = LocalPalette.current
    Panel("🏰 ক্যাসেল সংক্রান্ত প্রশ্ন", p.info) {
        if (vm.setupAskW) {
            YesNoRow("সাদা পক্ষ কি আগে ক্যাসেল করেছে, অথবা রাজা/নৌকা নড়িয়েছে?", vm.setupWMoved) { vm.setupWMoved = it }
        }
        if (vm.setupAskB) {
            YesNoRow("কালো পক্ষ কি আগে ক্যাসেল করেছে, অথবা রাজা/নৌকা নড়িয়েছে?", vm.setupBMoved) { vm.setupBMoved = it }
        }
        if (!vm.setupAskW && !vm.setupAskB) {
            Text(
                "রাজা বা নৌকা নিজের শুরুর ঘরে নেই, তাই ক্যাসেল স্বয়ংক্রিয়ভাবে বন্ধ থাকবে।",
                color = p.sub, fontSize = 13.sp, lineHeight = 19.sp
            )
        } else {
            Text(
                "হ্যাঁ দিলে ইঞ্জিন ওই পক্ষের জন্য ক্যাসেল ধরবে না। না দিলে ক্যাসেলের সুযোগ খুঁজবে।",
                color = p.sub, fontSize = 12.sp, lineHeight = 17.sp
            )
        }
    }
    Panel("▶ এখন কার চাল?", p.gold) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("⚪ সাদার চাল", vm.setupToMove, Modifier.weight(1f)) { vm.setupToMove = true }
            Pill("⚫ কালোর চাল", !vm.setupToMove, Modifier.weight(1f)) { vm.setupToMove = false }
        }
    }
    Panel("📋 সারাংশ", p.sub) {
        Text(
            (if (vm.setupWhite) "আপনি: সাদা" else "আপনি: কালো") +
                " • ইঞ্জিন: " + (if (vm.gEngine == 0) "Stockfish" else "আরাফাত ইঞ্জিন") +
                " • লেভেল: " + LEVEL_LABELS[vm.gLevel.coerceIn(0, 3)],
            color = p.text, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Start
        )
    }
    val err = vm.setupError
    if (err != null) {
        Text("⚠ $err", color = p.bad, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
    }
    Spacer(Modifier.height(6.dp))
    Button(
        onClick = { vm.startCustom() },
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E), contentColor = Color.White)
    ) { Text("▶ স্টার্ট", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
}
