package com.arafat.chessmaster.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun App(vm: ChessViewModel) {
    ChessTheme(vm.palette) {
        val p = LocalPalette.current
        Surface(Modifier.fillMaxSize(), color = p.bg) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val g = vm.viewGame
                    BackHandler(enabled = g != null) { vm.closeGame() }
                    when (vm.tab) {
                        0 -> if (vm.myWhite == null) StartScreen(vm) else GameScreen(vm)
                        1 -> if (g != null) ReplayScreen(vm, g) else HistoryScreen(vm)
                        else -> SettingsScreen(vm)
                    }
                }
                BottomBar(vm)
            }
        }
    }
}

@Composable
fun BottomBar(vm: ChessViewModel) {
    val p = LocalPalette.current
    val items = listOf("♞" to "খেলা", "📊" to "ইতিহাস", "⚙" to "সেটিংস")
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.surface)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEachIndexed { i, item ->
            val sel = vm.tab == i
            Column(
                Modifier
                    .clickable { vm.tab = i }
                    .padding(horizontal = 22.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(item.first, fontSize = 20.sp, color = if (sel) p.primary else p.sub)
                Text(
                    item.second,
                    fontSize = 11.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) p.primary else p.sub
                )
            }
        }
    }
}
