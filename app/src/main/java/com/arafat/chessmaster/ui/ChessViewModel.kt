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
import com.arafat.chessmaster.data.UpdateChecker
import com.arafat.chessmaster.data.UpdateInfo
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
    private val nativeDir = File(app.applicationInfo.nativeLibraryDir)
    private val sfStd = File(nativeDir, "libstockfish.so")
    private val sfDot = File(nativeDir, "libstockfish_dp.so")
    val stockfishPacked: Boolean = sfStd.exists() || sfDot.exists()

    // navigation / appearance
    var tab by mutableStateOf(0)
    var palette by mutableStateOf(store.palette)

    // defaults (Settings) : 0 = Stockfish, 1 = Arafat engine
    var defLevel by mutableStateOf(store.level)
    var defEngine by mutableStateOf(if (store.engine == 1 || !stockfishPacked) 1 else 0)

    // current match (can be changed during the game)
    var gLevel by mutableStateOf(store.level)
    var gEngine by mutableStateOf(if (store.engine == 1 || !stockfishPacked) 1 else 0)

    var sfVersion by mutableStateOf("")
    var sfFailed by mutableStateOf(false)
    val engineName: String
        get() {
            val choice = if (myWhite != null) gEngine else defEngine
            return when {
                choice == 1 -> "আরাফাত ইঞ্জিন"
                sfFailed -> "আরাফাত ইঞ্জিন (Stockfish চালু হয়নি)"
                sfVersion.isNotEmpty() -> sfVersion
                else -> "Stockfish"
            }
        }

    // updates
    private val verName: String = try {
        appCtx.packageManager.getPackageInfo(appCtx.packageName, 0).versionName ?: ""
    } catch (e: Exception) { "" }
    val versionText: String get() = verName
    private val appBuild: Int = verName.substringAfterLast('.').toIntOrNull() ?: 0
    var updateInfo by mutableStateOf<UpdateInfo?>(null)
    var updateMsg by mutableStateOf("")
    var updateChecking by mutableStateOf(false)

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

    // modify-match setup
    var setupStep by mutableStateOf(0)
    var setupWhite by mutableStateOf(true)
    var setupBoard by mutableStateOf(IntArray(64))
    var setupTool by mutableStateOf(1)
    var setupFlip by mutableStateOf(false)
    var setupToMove by mutableStateOf(true)
    var setupError by mutableStateOf<String?>(null)
    var setupAskW by mutableStateOf(false)
    var setupAskB by mutableStateOf(false)
    var setupWMoved by mutableStateOf(true)
    var setupBMoved by mutableStateOf(true)

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
        if (stockfishPacked && (defEngine == 0 || gEngine == 0)) warmUp()
        checkUpdate(false)
    }

    private fun warmUp() {
        viewModelScope.launch(Dispatchers.Default) { ensureSf() }
    }

    // ---------- persistence ----------

    private fun persist() {
        val w = myWhite
        if (w == null) {
            store.saveCurrent(null)
            return
        }
        store.saveCurrent(SavedGame(gameId, startFen, w, moves.toList(), recorded, gEngine, gLevel))
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
        gEngine = if (g.engine == 1 || !stockfishPacked) 1 else 0
        gLevel = g.level.coerceIn(0, 3)
        myWhite = g.myWhite
        pos = p
        lastMove = lm
        updateStatus()
        maybeAnalyze()
    }

    // ---------- settings (defaults) ----------

    fun changePalette(i: Int) {
        palette = i
        store.palette = i
    }

    fun setDefaultEngine(i: Int) {
        if (i == 0 && !stockfishPacked) return
        defEngine = i
        store.engine = i
        if (i == 0) warmUp()
    }

    fun setDefaultLevel(l: Int) {
        defLevel = l
        store.level = l
    }

    // ---------- change engine / thinking level during a match ----------

    fun setGameEngine(i: Int) {
        if (i == 0 && !stockfishPacked) return
        gEngine = i
        if (i == 0) warmUp()
        persist()
        if (myWhite != null) maybeAnalyze()
    }

    fun setGameLevel(l: Int) {
        gLevel = l
        persist()
        if (myWhite != null) maybeAnalyze()
    }

    // ---------- updates ----------

    fun checkUpdate(manual: Boolean) {
        if (updateChecking) return
        updateChecking = true
        if (manual) updateMsg = "চেক করা হচ্ছে…"
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest() }
            updateChecking = false
            if (info == null) {
                if (manual) updateMsg = "ইন্টারনেট বা সার্ভারে সমস্যা — পরে চেষ্টা করুন"
            } else if (appBuild > 0 && info.build > appBuild) {
                updateInfo = info
                if (manual) updateMsg = "নতুন ভার্সন পাওয়া গেছে: ${info.tag}"
            } else if (manual) {
                updateMsg = "আপনি সর্বশেষ ভার্সনে আছেন ✅"
            }
        }
    }

    fun dismissUpdate() {
        updateInfo = null
    }

    // ---------- game lifecycle ----------

    fun start(white: Boolean) {
        gEngine = defEngine
        gLevel = defLevel
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

    // ---------- modify match (custom board) ----------

    fun openSetup() {
        gEngine = defEngine
        gLevel = defLevel
        setupBoard = Position.start().b.copyOf()
        setupWhite = true
        setupToMove = true
        setupTool = 1
        setupFlip = false
        setupError = null
        setupStep = 1
        tab = 0
    }

    fun closeSetup() {
        setupStep = 0
        setupError = null
    }

    fun setupBack() {
        setupError = null
        if (setupStep > 1) setupStep -= 1 else closeSetup()
    }

    fun setupTap(sq: Int) {
        val arr = setupBoard.copyOf()
        val t = setupTool
        if (t == 0) {
            arr[sq] = 0
        } else {
            if (abs(t) == 6) {
                for (i in 0 until 64) if (arr[i] == t) arr[i] = 0
            }
            arr[sq] = t
        }
        setupBoard = arr
    }

    fun setupClear() {
        setupBoard = IntArray(64)
    }

    fun setupReset() {
        setupBoard = Position.start().b.copyOf()
    }

    private fun validateBoard(b: IntArray): String? {
        var wk = -1
        var bk = -1
        var wkCount = 0
        var bkCount = 0
        var wp = 0
        var bp = 0
        for (i in 0 until 64) {
            val x = b[i]
            val row = i / 8
            if (x == 6) { wk = i; wkCount++ }
            if (x == -6) { bk = i; bkCount++ }
            if (x == 1) {
                wp++
                if (row == 0 || row == 7) return "সাদা বোড়ে প্রথম বা শেষ সারিতে থাকতে পারে না"
            }
            if (x == -1) {
                bp++
                if (row == 0 || row == 7) return "কালো বোড়ে প্রথম বা শেষ সারিতে থাকতে পারে না"
            }
        }
        if (wkCount != 1) return "সাদা রাজা ঠিক একটি থাকতে হবে"
        if (bkCount != 1) return "কালো রাজা ঠিক একটি থাকতে হবে"
        if (wp > 8 || bp > 8) return "এক পক্ষের বোড়ে ৮টির বেশি হতে পারে না"
        if (abs(wk / 8 - bk / 8) <= 1 && abs(wk % 8 - bk % 8) <= 1) return "দুই রাজা পাশাপাশি থাকতে পারে না"
        return null
    }

    /** Step 2 -> 3 : validate the arranged board and prepare the castling questions. */
    fun setupNext() {
        val err = validateBoard(setupBoard)
        if (err != null) {
            setupError = err
            return
        }
        setupError = null
        val b = setupBoard
        setupAskW = b[60] == 6 && (b[63] == 4 || b[56] == 4)
        setupAskB = b[4] == -6 && (b[7] == -4 || b[0] == -4)
        setupWMoved = true
        setupBMoved = true
        setupStep = 3
    }

    fun startCustom() {
        val b = setupBoard
        var cr = 0
        if (setupAskW && !setupWMoved) {
            if (b[63] == 4) cr = cr or 1
            if (b[56] == 4) cr = cr or 2
        }
        if (setupAskB && !setupBMoved) {
            if (b[7] == -4) cr = cr or 4
            if (b[0] == -4) cr = cr or 8
        }
        val p = Position.fromBoard(b, setupToMove, cr)
        if (p == null) {
            setupError = "বোর্ড সঠিক নয়"
            return
        }
        if (p.inCheck(!setupToMove)) {
            setupError = if (setupToMove) "কালো রাজা চেকে আছে অথচ এখন সাদার চাল — অবস্থান অবৈধ"
            else "সাদা রাজা চেকে আছে অথচ এখন কালোর চাল — অবস্থান অবৈধ"
            return
        }
        setupError = null
        myWhite = setupWhite
        startFen = p.toFen()
        gameId = System.currentTimeMillis()
        setupStep = 0
        reset(p)
    }

    // ---------- engine ----------

    private fun cpuHasDotprod(): Boolean = try {
        File("/proc/cpuinfo").readText().contains("asimddp")
    } catch (e: Exception) { false }

    @Synchronized
    private fun ensureSf(): StockfishEngine? {
        val cur = sf
        if (cur != null) {
            if (cur.isAlive()) return cur
            sf = null
            sfTried = false
        }
        if (sfTried) return null
        sfTried = true
        val bins = ArrayList<File>()
        if (sfDot.exists() && cpuHasDotprod()) bins.add(sfDot)
        if (sfStd.exists()) bins.add(sfStd)
        if (bins.isEmpty()) return null
        val cores = Runtime.getRuntime().availableProcessors()
        val threads = maxOf(1, minOf(4, cores / 2))
        for (bin in bins) {
            val e = StockfishEngine(bin.absolutePath)
            if (e.start(threads, 128)) {
                sf = e
                sfVersion = e.version
                sfFailed = false
                return e
            }
        }
        sfFailed = true
        return null
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
        val ms = when (gLevel) { 0 -> 1500L; 1 -> 4000L; 2 -> 9000L; else -> 20000L }
        val plies = snaps.size
        val useSf = gEngine == 0
        thinking = true
        analysis = null
        liveDepth = 0
        job = viewModelScope.launch {
            val a = withContext(Dispatchers.Default) {
                Analyzer.analyze(p, last, prev, ms, plies, if (useSf) ensureSf() else null, { !isActive }, { d, _ -> liveDepth = d })
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
