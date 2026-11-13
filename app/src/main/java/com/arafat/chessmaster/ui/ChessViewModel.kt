package com.arafat.chessmaster.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arafat.chessmaster.data.GameRecord
import com.arafat.chessmaster.data.MoveRec
import com.arafat.chessmaster.data.SavedGame
import com.arafat.chessmaster.data.Store
import com.arafat.chessmaster.engine.Analysis
import com.arafat.chessmaster.engine.Analyzer
import com.arafat.chessmaster.engine.Explainer
import com.arafat.chessmaster.engine.Position
import com.arafat.chessmaster.engine.StockfishEngine
import com.arafat.chessmaster.engine.moveToUci
import com.arafat.chessmaster.engine.mv
import com.arafat.chessmaster.engine.uciToMove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class ChessViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val appCtx = app.applicationContext
    private val sfFile = File(app.applicationInfo.nativeLibraryDir, "libstockfish.so")
    val stockfishPacked: Boolean = sfFile.exists()

    // navigation / settings
    var tab by mutableStateOf(0)
    var palette by mutableStateOf(store.palette)
    var level by mutableStateOf(store.level)
    var engineName by mutableStateOf(if (sfFile.exists()) "Stockfish" else "ইনবিল্ট ইঞ্জিন")

    // game state
    var pos by mutableStateOf(Position.start())
    var myWhite by mutableStateOf<Boolean?>(null)
    var analysis by mutableStateOf<Analysis?>(null)
    var thinking by mutableStateOf(false)
    var liveDepth by mutableStateOf(0)
    var selected by mutableStateOf(-1)
    var targets by mutableStateOf<Set<Int>>(emptySet())
    var lastMove by mutableStateOf(-1)
    var status by mutableStateOf<String?>(null)
    var promo by mutableStateOf<IntArray?>(null)
    var fenDialog by mutableStateOf(false)
    var fenError by mutableStateOf<String?>(null)
    var newGameDialog by mutableStateOf(false)
    val moves = mutableStateListOf<MoveRec>()

    // history
    var history by mutableStateOf<List<GameRecord>>(store.loadHistory())
    var viewGame by mutableStateOf<GameRecord?>(null)
    var replayIndex by mutableStateOf(0)

    private var resultCode: String? = null
    private var startFen = Position.START_FEN
    private var gameId = 0L
    private var recorded = false
    private val snaps = ArrayList<Position>()
    private val lastMoves = ArrayList<Int>()
    private val keys = HashMap<String, Int>()
    private var job: Job? = null
    private var sf: StockfishEngine? = null
    private var sfTried = false

    val plyCount: Int get() = snaps.size
    val canUndo: Boolean get() = snaps.isNotEmpty() && !recorded

    init {
        restore()
    }

    // ---------- persistence ----------

    private fun persist() {
        val w = myWhite
        if (w == null) {
            store.saveCurrent(null)
            return
        }
        store.saveCurrent(SavedGame(gameId, startFen, w, moves.toList(), recorded))
    }

    private fun restore() {
        val g = store.loadCurrent() ?: return
        val start = Position.fromFen(g.startFen) ?: return
        var p = start
        keys.clear()
        keys[p.key()] = 1
        moves.clear()
        snaps.clear()
        lastMoves.clear()
        var lm = -1
        for (r in g.moves) {
            val m = uciToMove(p, r.uci) ?: break
            snaps.add(p)
            lastMoves.add(lm)
            val np = p.copy()
            np.make(m)
            p = np
            lm = m
            moves.add(r)
            val k = p.key()
            keys[k] = (keys[k] ?: 0) + 1
        }
        startFen = g.startFen
        gameId = g.id
        recorded = g.recorded
        myWhite = g.myWhite
        pos = p
        lastMove = lm
        updateStatus()
        maybeAnalyze()
    }

    // ---------- settings ----------

    fun changePalette(i: Int) {
        palette = i
        store.palette = i
    }

    fun setLevelAndRefresh(l: Int) {
        level = l
        store.level = l
        if (myWhite != null) maybeAnalyze()
    }

    // ---------- game lifecycle ----------

    fun start(white: Boolean) {
        myWhite = white
        startFen = Position.START_FEN
        gameId = System.currentTimeMillis()
        reset(Position.start())
    }

    private fun reset(p: Position) {
        job?.cancel()
        pos = p
        snaps.clear(); lastMoves.clear(); moves.clear(); keys.clear()
        keys[p.key()] = 1
        recorded = false
        lastMove = -1; selected = -1; targets = emptySet()
        analysis = null; thinking = false; promo = null
        updateStatus()
        persist()
        maybeAnalyze()
    }

    fun loadFen(fen: String) {
        val clean = fen.trim()
        val p = Position.fromFen(clean)
        if (p == null) {
            fenError = "FEN সঠিক নয়"
            return
        }
        fenError = null
        fenDialog = false
        startFen = clean
        gameId = System.currentTimeMillis()
        reset(p)
    }

    fun newGameClicked() {
        if (myWhite == null) return
        if (moves.isEmpty() || recorded) clearGame() else newGameDialog = true
    }

    /** result: "W", "L", "D" to save the game in history, or null to discard it. */
    fun finishGame(result: String?) {
        if (result != null) recordGame(result)
        newGameDialog = false
        clearGame()
    }

    private fun clearGame() {
        job?.cancel()
        thinking = false
        analysis = null
        myWhite = null
        moves.clear(); snaps.clear(); lastMoves.clear(); keys.clear()
        recorded = false
        selected = -1; targets = emptySet(); lastMove = -1; promo = null
        status = null
        persist()
    }

    private fun recordGame(result: String) {
        val w = myWhite ?: return
        if (moves.isEmpty() || recorded) return
        val rec = GameRecord(gameId, startFen, w, result, moves.toList())
        val list = listOf(rec) + history
        history = list
        store.saveHistory(list)
        recorded = true
        persist()
    }

    private fun updateStatus() {
        val legal = pos.legalMoves()
        resultCode = null
        status = when {
            legal.isEmpty() -> {
                if (pos.inCheck(pos.white)) {
                    val winnerWhite = !pos.white
                    if (winnerWhite == myWhite) { resultCode = "W"; "চেকমেট! আপনি জিতেছেন 🏆" }
                    else { resultCode = "L"; "চেকমেট! শত্রু জিতেছে" }
                } else { resultCode = "D"; "স্টেলমেট — ড্র" }
            }
            pos.half >= 100 -> { resultCode = "D"; "৫০ চালের নিয়মে ড্র" }
            pos.insufficient() -> { resultCode = "D"; "পর্যাপ্ত ঘুঁটি নেই — ড্র" }
            (keys[pos.key()] ?: 0) >= 3 -> { resultCode = "D"; "তিনবার একই অবস্থান — ড্র" }
            else -> null
        }
    }

    // ---------- engine ----------

    private fun ensureSf(): StockfishEngine? {
        if (sf != null) return sf
        if (sfTried) return null
        sfTried = true
        if (!sfFile.exists()) return null
        val e = StockfishEngine(sfFile.absolutePath)
        val cores = Runtime.getRuntime().availableProcessors()
        val threads = maxOf(1, minOf(4, cores / 2))
        val ok = e.start(threads, if (level >= 3) 128 else 64)
        if (ok) {
            sf = e
            engineName = e.version
        } else {
            engineName = "ইনবিল্ট ইঞ্জিন (Stockfish চালু হয়নি)"
        }
        return sf
    }

    private fun maybeAnalyze() {
        if (status == null && myWhite != null && pos.white == myWhite) {
            analyze()
        } else {
            job?.cancel()
            thinking = false
        }
    }

    private fun analyze() {
        job?.cancel()
        val p = pos.copy()
        val last = lastMove
        val prev = if (snaps.isNotEmpty()) snaps[snaps.size - 1] else null
        val ms = when (level) { 0 -> 1500L; 1 -> 4000L; 2 -> 9000L; else -> 20000L }
        val plies = snaps.size
        thinking = true
        analysis = null
        liveDepth = 0
        job = viewModelScope.launch {
            val a = withContext(Dispatchers.Default) {
                Analyzer.analyze(p, last, prev, ms, plies, ensureSf(), { !isActive }, { d, _ -> liveDepth = d })
            }
            analysis = a
            thinking = false
        }
    }

    // ---------- moves ----------

    fun applyMove(m: Int) {
        val s = Analyzer.san(pos, m)
        val white = pos.white
        val mine = white == myWhite
        val candNote = analysis?.candidates?.firstOrNull { it.move == m }?.note()
        val note = when {
            mine && candNote != null -> candNote
            !mine -> Explainer.enemyNote(pos, m)
            else -> ""
        }
        moves.add(MoveRec(moveToUci(m), s, white, mine, note))
        val np = pos.copy()
        np.make(m)
        snaps.add(pos)
        lastMoves.add(lastMove)
        pos = np
        lastMove = m
        selected = -1; targets = emptySet(); analysis = null; promo = null
        val k = np.key()
        keys[k] = (keys[k] ?: 0) + 1
        updateStatus()
        val rc = resultCode
        if (status != null && rc != null) recordGame(rc) else persist()
        maybeAnalyze()
    }

    fun playCandidate(i: Int) {
        val a = analysis ?: return
        if (i in a.candidates.indices) applyMove(a.candidates[i].move)
    }

    fun undo() {
        if (!canUndo) return
        job?.cancel()
        val k = pos.key()
        keys[k] = (keys[k] ?: 1) - 1
        pos = snaps.removeAt(snaps.lastIndex)
        lastMove = lastMoves.removeAt(lastMoves.lastIndex)
        moves.removeAt(moves.lastIndex)
        selected = -1; targets = emptySet(); analysis = null; promo = null; thinking = false
        updateStatus()
        persist()
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
        if (moves.isEmpty()) return "এখনো কোনো চাল হয়নি"
        val sb = StringBuilder()
        var num = 1
        for ((i, r) in moves.withIndex()) {
            if (r.whiteMoved) sb.append("$num. ") else if (i == 0) sb.append("$num... ")
            sb.append(r.san).append(' ')
            if (!r.whiteMoved) num++
        }
        return sb.toString().trim()
    }

    // ---------- history ----------

    fun openGame(g: GameRecord) {
        viewGame = g
        replayIndex = g.moves.size
    }

    fun closeGame() {
        viewGame = null
    }

    fun deleteGame(g: GameRecord) {
        val list = history.filter { it.id != g.id }
        history = list
        store.saveHistory(list)
        viewGame = null
    }

    override fun onCleared() {
        super.onCleared()
        sf?.quit()
    }
}
