package com.arafat.chessmaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arafat.chessmaster.ui.App
import com.arafat.chessmaster.ui.ChessViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: ChessViewModel = viewModel()
            App(vm)
        }
    }
}
