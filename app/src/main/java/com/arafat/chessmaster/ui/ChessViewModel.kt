package com.arafat.chessmaster.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arafat.chessmaster.engine.Analysis
import com.arafat.chessmaster.engine.Analyzer
import com.arafat.chessmaster.engine.Position
import com.arafat.chessmaster.engine.mv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class ChessViewModel : ViewModel() {
    var pos by mutableStateOf(Position.start())
    var myWhite by mutableStateOf<Boolean?>(null)
    var analysis by mutableStateOf<Analysis?>(null)
    var thinking by mutableStateOf(false)
    var selected by mutableStateOf(-1)
    var targets by mutableStateOf<Set<Int>>(emptySet())
    var lastMove by mutableStateOf(-1)
    var status by mutableStateOf<String?>(null)
    var level by mutableStateOf(1)
    var dark by mutableStateOf(true)
    var promo by mutableStateOf<IntArray?>(null)
    var fenDialog by mutableStateOf(false)
    var fenError by mutableStateOf<String?>(null)
    val sanList = mutableStateListOf<String>()

    private var startsWhite = true
    private val snaps = ArrayList<Position>()
    private val lastMoves = ArrayList<Int>()
    private val keys = HashMap<String, Int>()
    private var job: Job? = null

    val plyCount: Int get() = snaps.size

    fun start(white: Boolean) {
        myWhite = white
        reset(Position.start())
    }

    fun backToStart() {
        job?.cancel()
        thinking = false
        analysis = null
        myWhite = null
    }

    private fun reset(p: Position) {
        job?.cancel()
        startsWhite = p.white
        pos = p
        snaps.clear(); lastMoves.clear(); sanList.clear(); keys.clear()
        keys[p.key()] = 1
        lastMove = -1; selected = -1; targets = emptySet()
        analysis = null; thinking = false; promo = null
        updateStatus()
        maybeAnalyze()
    }

    fun loadFen(fen: String) {
        val p = Position.fromFen(fen)
        if (p == null) {
            fenError = "FEN সঠিক নয়"
            return
        }
        fenError = null
        fenDialog = false
        reset(p)
    }

    fun setLevelAndRefresh(l: Int) {
        level = l
        if (myWhite != null) maybeAnalyze()
    }

    private fun updateStatus() {
        val legal = pos.legalMoves()
        status = when {
            legal.isEmpty() -> {
                if (pos.inCheck(pos.white)) {
                    val winnerWhite = !pos.white
                    if (winnerWhite == myWhite) "চেকমেট! আপনি জিতেছেন 🏆" else "চেকমেট! শত্রু জিতেছে"
                } else "স্টেলমেট — ড্র"
            }
            pos.half >= 100 -> "৫০ চালের নিয়মে ড্র"
            pos.insufficient() -> "পর্যাপ্ত ঘুঁটি নেই — ড্র"
            (keys[pos.key()] ?: 0) >= 3 -> "তিনবার একই অবস্থান — ড্র"
            else -> null
        }
    }

    private fun maybeAnalyze() {
        if (status == null && myWhite != null && pos.white == myWhite) analyze() else {
            job?.cancel(); thinking = false
        }
    }

    private fun analyze() {
        job?.cancel()
        val p = pos.copy()
        val last = lastMove
        val ms = when (level) { 0 -> 1500L; 1 -> 4000L; else -> 9000L }
        val plies = snaps.size
        thinking = true
        analysis = null
        job = viewModelScope.launch {
            val a = withContext(Dispatchers.Default) {
                Analyzer.analyze(p, last, ms, plies) { !isActive }
            }
            analysis = a
            thinking = false
        }
    }

    fun applyMove(m: Int) {
        val s = Analyzer.san(pos, m)
        val np = pos.copy()
        np.make(m)
        snaps.add(pos)
        lastMoves.add(lastMove)
        sanList.add(s)
        pos = np
        lastMove = m
        selected = -1; targets = emptySet(); analysis = null; promo = null
        val k = np.key()
        keys[k] = (keys[k] ?: 0) + 1
        updateStatus()
        maybeAnalyze()
    }

    fun playCandidate(i: Int) {
        val a = analysis ?: return
        if (i in a.candidates.indices) applyMove(a.candidates[i].move)
    }

    fun undo() {
        if (snaps.isEmpty()) return
        job?.cancel()
        val k = pos.key()
        keys[k] = (keys[k] ?: 1) - 1
        pos = snaps.removeAt(snaps.lastIndex)
        lastMove = lastMoves.removeAt(lastMoves.lastIndex)
        sanList.removeAt(sanList.lastIndex)
        selected = -1; targets = emptySet(); analysis = null; promo = null; thinking = false
        updateStatus()
        maybeAnalyze()
    }

    fun tap(sq: Int) {
        if (status != null || promo != null) return
        val p = pos.b[sq]
        val own = p != 0 && (p > 0) == pos.white
        if (selected == -1) {
            if (own) select(sq)
            return
        }
        if (sq == selected) {
            selected = -1; targets = emptySet()
            return
        }
        if (sq in targets) {
            val f = selected
            val piece = pos.b[f]
            val promoRow = if (pos.white) 0 else 7
            if (abs(piece) == 1 && sq / 8 == promoRow) {
                promo = intArrayOf(f, sq)
                selected = -1; targets = emptySet()
            } else {
                applyMove(mv(f, sq))
            }
            return
        }
        if (own) select(sq) else {
            selected = -1; targets = emptySet()
        }
    }

    private fun select(sq: Int) {
        selected = sq
        targets = pos.legalMoves().filter { (it and 63) == sq }.map { (it shr 6) and 63 }.toSet()
    }

    fun choosePromo(pr: Int) {
        val p = promo ?: return
        promo = null
        applyMove(mv(p[0], p[1], pr))
    }

    fun moveText(): String {
        if (sanList.isEmpty()) return "এখনো কোনো চাল হয়নি"
        val sb = StringBuilder()
        var white = startsWhite
        var num = 1
        for ((i, s) in sanList.withIndex()) {
            if (white) sb.append("$num. ") else if (i == 0) sb.append("$num... ")
            sb.append(s).append(' ')
            if (!white) num++
            white = !white
        }
        return sb.toString().trim()
    }
}
