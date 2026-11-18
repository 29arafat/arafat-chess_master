package com.arafat.chessmaster.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arafat.chessmaster.R

fun openLink(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
    }
}

private fun sendMail(ctx: Context, mail: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$mail")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
    }
}

@Composable
private fun EngineRow(title: String, sub: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(if (selected) p.primary.copy(alpha = 0.18f) else p.surface2)
            .border(1.dp, if (selected) p.primary else p.surface2, shape)
            .clickable(enabled = enabled) { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) p.text else p.sub, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(sub, color = p.sub, fontSize = 12.sp, lineHeight = 17.sp)
        }
        if (selected) Text("  ✔", color = p.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsScreen(vm: ChessViewModel) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Text("⚙ সেটিংস", color = p.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))

        Panel("🤖 ইঞ্জিন নির্বাচন", p.info) {
            EngineRow(
                "🐟 Stockfish (সর্বশেষ, সবচেয়ে শক্তিশালী)",
                if (vm.stockfishPacked) {
                    if (vm.sfFailed) "এই ফোনে চালু করা যায়নি" else "সংস্করণ: " + (if (vm.sfVersion.isNotEmpty()) vm.sfVersion else "লোড হচ্ছে…")
                } else "এই বিল্ডে Stockfish নেই",
                vm.engineChoice == 0,
                vm.stockfishPacked
            ) { vm.chooseEngine(0) }
            EngineRow(
                "♞ আরাফাত ইঞ্জিন (নিজস্ব)",
                "আমাদের তৈরি ইঞ্জিন — ট্রান্সপজিশন টেবিল, নাল-মুভ, মোবিলিটি মূল্যায়ন সহ",
                vm.engineChoice == 1,
                true
            ) { vm.chooseEngine(1) }
            Spacer(Modifier.height(4.dp))
            Text("এখন চলছে: ${vm.engineName}", color = p.sub, fontSize = 12.sp)
        }

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

        Panel("⬆ আপডেট", p.good) {
            Text("বর্তমান ভার্সন: ${vm.versionText.ifEmpty { "—" }}", color = p.text, fontSize = 13.sp)
            Text(
                "নতুন ভার্সনে সর্বশেষ Stockfish থাকে। নতুন ভার্সন এলে অ্যাপ খোলার সময় জানানো হয়।",
                color = p.sub, fontSize = 12.sp, lineHeight = 17.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.checkUpdate(true) }, enabled = !vm.updateChecking) { Text("🔄 আপডেট আছে কিনা দেখুন") }
            }
            if (vm.updateMsg.isNotEmpty()) Text(vm.updateMsg, color = p.sub, fontSize = 12.sp)
        }

        Panel("ℹ অ্যাপ সম্পর্কে", p.sub) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.developer),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .border(3.dp, Color(0xFF00E5FF), CircleShape)
                )
                Column(Modifier.padding(start = 14.dp)) {
                    Text("ডেভলপার", color = p.sub, fontSize = 12.sp)
                    Text("Yasin Arafat", color = p.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Email: ", color = p.sub, fontSize = 13.sp)
                Text(
                    "29.arafat@gmail.com",
                    color = p.primary, fontSize = 13.sp, textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { sendMail(ctx, "29.arafat@gmail.com") }
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Website: ", color = p.sub, fontSize = 13.sp)
                Text(
                    "yasin-arafat.pages.dev",
                    color = p.primary, fontSize = 13.sp, textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { openLink(ctx, "https://yasin-arafat.pages.dev") }
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Arafat Chess Master হল, Yasin Arafat এর তৈরি একটি দাবা খেলার অ্যাসিস্ট্যান্ট টুলস, এটা ইউজ করে আপনি সহজেই আপনার প্রতিপক্ষকে হারাতে পারবেন, এবং প্রত্যেকটা মুভের লজিক সহজ বাংলা ভাষায় জানতে পারবেন, ফলে আপনার দাবা খেলা ও শেখার জার্নি আরো সহজ হবে।",
                color = p.text, fontSize = 13.sp, lineHeight = 20.sp
            )
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.bad.copy(alpha = 0.14f))
                    .border(1.dp, p.bad.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text(
                    "বিশেষ দ্রষ্টব্য: এই অ্যাপস অপব্যবহার করা সম্পূর্ণ নিষিদ্ধ, আপনি যদি এই অ্যাপসটির অপব্যবহার করে, অনলাইন প্লাটফর্মে খেলেন, তাহলে আপনার অ্যাকাউন্ট ব্যান হতে পারে।",
                    color = p.bad, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(48.dp))
                Column(Modifier.padding(start = 10.dp)) {
                    Text("Arafat Chess Master", color = p.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("ভার্সন ${vm.versionText.ifEmpty { "—" }} • Your Chess Assistant", color = p.sub, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Swatch(c: Color) {
    Spacer(
        Modifier
            .padding(horizontal = 2.dp)
            .size(18.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(c)
    )
}
