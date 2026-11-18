package com.arafat.chessmaster.engine

import java.util.Locale
import kotlin.math.abs

data class Candidate(
    val move: Int,
    val san: String,
    val fromTo: String,
    val cp: Int,
    val scoreText: String,
    val pvSan: String,
    val tags: List<String>,
    val pv: List<Int> = emptyList(),
    val why: List<String> = emptyList(),
    val longTerm: List<String> = emptyList()
) {
    fun note(): String {
        val sb = StringBuilder()
        if (why.isNotEmpty()) sb.append("কেন: ").append(why.joinToString(" "))
        if (longTerm.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("দীর্ঘমেয়াদে: ").append(longTerm.joinToString(" "))
        }
        return sb.toString()
    }
}

data class Analysis(
    val candidates: List<Candidate>,
    val threats: List<String>,
    val traps: List<String>,
    val attacks: List<String>,
    val king: List<String>,
    val plan: String,
    val depth: Int,
    val whiteCp: Int,
    val enemyNote: String? = null,
    val engine: String = ""
)

object Analyzer {
    val NAME = arrayOf("", "বোড়ে", "ঘোড়া", "গজ", "নৌকা", "মন্ত্রী", "রাজা")
    private const val LET = "?PNBRQK"

    // ---------- notation helpers ----------

    fun san(pos: Position, m: Int): String {
        val f = m and 63
        val t = (m shr 6) and 63
        val pr = (m shr 12) and 7
        val p = pos.b[f]
        val type = abs(p)
        var s: String
        if (type == 6 && abs(t - f) == 2) {
            s = if (t > f) "O-O" else "O-O-O"
        } else {
            val isCap = pos.b[t] != 0 || (type == 1 && t == pos.ep && f % 8 != t % 8)
            val sb = StringBuilder()
            if (type != 1) {
                sb.append(LET[type])
                val others = pos.legalMoves().filter {
                    it != m && ((it shr 6) and 63) == t && abs(pos.b[it and 63]) == type
                }
                if (others.isNotEmpty()) {
                    val sameFile = others.any { (it and 63) % 8 == f % 8 }
                    val sameRank = others.any { (it and 63) / 8 == f / 8 }
                    if (!sameFile) sb.append('a' + f % 8)
                    else if (!sameRank) sb.append(8 - f / 8)
                    else sb.append(sqName(f))
                }
            } else if (isCap) {
                sb.append('a' + f % 8)
            }
            if (isCap) sb.append('x')
            sb.append(sqName(t))
            if (pr != 0) sb.append('=').append(LET[pr])
            s = sb.toString()
        }
        pos.make(m)
        val chk = pos.inCheck(pos.white)
        val hasMove = if (chk) pos.legalMoves().isNotEmpty() else true
        pos.unmake(m)
        if (chk) s += if (hasMove) "+" else "#"
        return s
    }

    fun fromTo(m: Int): String {
        val pr = (m shr 12) and 7
        val extra = if (pr != 0) "=${LET[pr]}" else ""
        return "${sqName(m and 63)} → ${sqName((m shr 6) and 63)}$extra"
    }

    fun scoreText(cp: Int): String {
        return if (abs(cp) > MATE - 100) {
            val n = (MATE - abs(cp) + 1) / 2
            if (cp > 0) "$n চালে মেট ✔" else "$n চালে মেট হবেন ⚠"
        } else {
            String.format(Locale.US, "%+.2f", cp / 100.0)
        }
    }

    private fun pvSan(pos: Position, pv: List<Int>, maxLen: Int): String {
        val p = pos.copy()
        val sb = StringBuilder()
        for (m in pv.take(maxLen)) {
            if (!p.legalMoves().contains(m)) break
            sb.append(san(p, m)).append(' ')
            p.make(m)
        }
        return sb.toString().trim()
    }

    // ---------- position description ----------

    fun situation(pos: Position, plyCount: Int): List<String> {
        val w = IntArray(7)
        val bl = IntArray(7)
        var npm = 0
        for (x in pos.b) {
            if (x > 0) w[x]++ else if (x < 0) bl[-x]++
            val t = abs(x)
            if (t in 2..5) npm += VALS[t]
        }
        val phase = when {
            npm <= 2600 -> "এন্ডগেম"
            plyCount < 20 -> "ওপেনিং"
            else -> "মিডলগেম"
        }
        val advice = when (phase) {
            "ওপেনিং" -> "কেন্দ্র দখল করুন, ঘোড়া-গজ নামান, তাড়াতাড়ি ক্যাসেল করুন, একই ঘুঁটি বারবার চালবেন না।"
            "মিডলগেম" -> "ঘুঁটির সমন্বয় ও রাজার নিরাপত্তা দেখুন; দুর্বল ঘুঁটি/ঘর টার্গেট করুন।"
            else -> "রাজাকে কেন্দ্রে সক্রিয় করুন, পাসড বোড়ে এগিয়ে দিন, নৌকা সক্রিয় রাখুন।"
        }
        fun line(c: IntArray): String =
            "Q${c[5]} R${c[4]} B${c[3]} N${c[2]} P${c[1]}"
        var diff = 0
        for (t in 1..5) diff += VALS[t] * (w[t] - bl[t])
        val mat = when {
            diff > 50 -> "সাদা এগিয়ে (+${String.format(Locale.US, "%.1f", diff / 100.0)})"
            diff < -50 -> "কালো এগিয়ে (+${String.format(Locale.US, "%.1f", -diff / 100.0)})"
            else -> "ম্যাটেরিয়াল সমান"
        }
        return listOf("ফেজ: $phase — $advice", "সাদা: ${line(w)}", "কালো: ${line(bl)}", mat)
    }

    // ---------- tactical motif detection ----------

    private fun attackSquares(b: IntArray, s: Int): List<Int> {
        val res = ArrayList<Int>()
        val p = b[s]
        val t = abs(p)
        val r = s / 8
        val c = s % 8
        when (t) {
            1 -> {
                val nr = if (p > 0) r - 1 else r + 1
                if (nr in 0..7) {
                    if (c > 0) res.add(nr * 8 + c - 1)
                    if (c < 7) res.add(nr * 8 + c + 1)
                }
            }
            2 -> for (i in 0 until 8) {
                val nr = r + KDR[i]
                val nc = c + KDC[i]
                if (nr in 0..7 && nc in 0..7) res.add(nr * 8 + nc)
            }
            6 -> for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val nr = r + dr
                val nc = c + dc
                if (nr in 0..7 && nc in 0..7) res.add(nr * 8 + nc)
            }
            3, 4, 5 -> {
                val lo = if (t == 3) 4 else 0
                val hi = if (t == 4) 4 else 8
                for (d in lo until hi) {
                    var nr = r + DR[d]
                    var nc = c + DC[d]
                    while (nr in 0..7 && nc in 0..7) {
                        val sq = nr * 8 + nc
                        res.add(sq)
                        if (b[sq] != 0) break
                        nr += DR[d]
                        nc += DC[d]
                    }
                }
            }
        }
        return res
    }

    fun tagsFor(pos: Position, m: Int): List<String> {
        val tags = ArrayList<String>()
        val f = m and 63
        val t = (m shr 6) and 63
        val pr = (m shr 12) and 7
        val type = abs(pos.b[f])
        val me = pos.white
        val enemy = !me
        val victim = abs(pos.b[t])

        if (type == 6 && abs(t - f) == 2) tags.add("ক্যাসেল: রাজা নিরাপদ, নৌকা সক্রিয়")
        if (victim != 0) {
            val g = see(pos, m)
            tags.add(
                when {
                    g >= 100 -> "${NAME[victim]} ধরছে (নিরাপদ লাভ)"
                    g >= -50 -> "${NAME[victim]} এক্সচেঞ্জ"
                    else -> "${NAME[victim]} ধরছে (ঝুঁকিপূর্ণ)"
                }
            )
        }
        if (pr != 0) tags.add("প্রোমোশন → ${NAME[pr]}")

        val np = pos.copy()
        np.make(m)
        val chk = np.inCheck(enemy)
        if (chk) {
            if (np.legalMoves().isEmpty()) {
                tags.add("চেকমেট!")
            } else {
                val tmp = np.b[t]
                np.b[t] = 0
                val disc = np.attacked(np.kp[if (enemy) 0 else 1], me)
                np.b[t] = tmp
                tags.add(if (disc) "ডিসকভার্ড চেক (লুকানো আক্রমণ)" else "চেক")
            }
        }

        val mt = abs(np.b[t])
        // fork
        var forkCount = 0
        for (sq in attackSquares(np.b, t)) {
            val x = np.b[sq]
            if (x == 0 || (x > 0) == me) continue
            val xt = abs(x)
            if (xt == 6) { forkCount++; continue }
            if (xt < 2) continue
            val defended = np.attacked(sq, enemy)
            if (VALS[xt] > VALS[mt] || !defended) forkCount++
        }
        val safe = !np.attacked(t, enemy) || np.attacked(t, me)
        if (forkCount >= 2 && safe) tags.add("ফর্ক: একসাথে $forkCount টি ঘুঁটিতে আক্রমণ")

        // pin / skewer along a line
        if (mt in 3..5) {
            val lo = if (mt == 3) 4 else 0
            val hi = if (mt == 4) 4 else 8
            for (d in lo until hi) {
                var nr = t / 8 + DR[d]
                var nc = t % 8 + DC[d]
                var first = -1
                while (nr in 0..7 && nc in 0..7) {
                    val sq = nr * 8 + nc
                    val x = np.b[sq]
                    if (x != 0) {
                        if ((x > 0) == me) break
                        if (first == -1) {
                            first = sq
                        } else {
                            val a = abs(np.b[first])
                            val bb = abs(x)
                            if (a != 6 && (bb == 6 || VALS[bb] > VALS[a])) {
                                tags.add("পিন: ${NAME[a]} আটকে গেছে, পেছনে ${NAME[bb]}")
                            }
                            break
                        }
                    }
                    nr += DR[d]
                    nc += DC[d]
                }
            }
        }
        return tags
    }

    // ---------- enemy threats (null-move idea) ----------

    private fun enemyThreats(pos: Position): List<String> {
        val res = ArrayList<String>()
        if (pos.inCheck(pos.white)) return res
        val np = pos.copy()
        np.white = !np.white
        np.ep = -1
        val moves = np.legalMoves()
        val seen = HashSet<Int>()
        val found = ArrayList<Pair<Int, String>>()
        for (m in moves) {
            val t = (m shr 6) and 63
            val victim = abs(np.b[t])
            if (victim != 0 && seen.add(t)) {
                val g = see(np, m)
                if (g >= 100) {
                    found.add(g to "শত্রু আপনার ${NAME[victim]} (${sqName(t)}) ধরার হুমকি দিচ্ছে — ${sanSafe(np, m)}")
                }
            }
            np.make(m)
            if (np.inCheck(np.white) && np.legalMoves().isEmpty()) {
                found.add(100000 to "⚠ মেটের হুমকি! শত্রু ${sanSafe(pos.copy().also { it.white = !it.white; it.ep = -1 }, m)} খেলে চেকমেট দিতে পারে")
            }
            np.unmake(m)
        }
        found.sortByDescending { it.first }
        for (x in found.take(3)) res.add(x.second)
        return res
    }

    private fun sanSafe(p: Position, m: Int): String = try { san(p, m) } catch (e: Exception) { fromTo(m) }

    // ---------- traps ----------

    private fun findTraps(pos: Position, s: Search, bestCp: Int, lastEnemy: Int, cancelled: () -> Boolean): List<String> {
        val res = ArrayList<String>()
        val caps = pos.legalMoves()
            .filter { pos.b[(it shr 6) and 63] != 0 }
            .map { it to see(pos, it) }
            .filter { it.second >= 100 }
            .sortedByDescending { it.second }
            .take(3)
        for ((m, _) in caps) {
            if (cancelled()) break
            val sc = s.evalMove(m, 4, 1200) ?: continue
            if (sc < bestCp - 150) {
                val t = (m shr 6) and 63
                val victim = abs(pos.b[t])
                val lastTo = if (lastEnemy >= 0) (lastEnemy shr 6) and 63 else -1
                val extra = if (t == lastTo) " (শত্রুর শেষ চালটাই ফাঁদ!)" else ""
                res.add("ফাঁদ! ${sanSafe(pos, m)} দিয়ে ${NAME[victim]} ধরা লোভনীয়, কিন্তু গভীর হিসাবে ক্ষতি (${scoreText(sc)}) — ধরবেন না$extra")
            }
        }
        return res
    }

    // ---------- king safety ----------

    private fun kingAdvice(pos: Position, best: Candidate?): List<String> {
        val res = ArrayList<String>()
        val me = pos.white
        val ksq = pos.kp[if (me) 0 else 1]
        if (pos.inCheck(me)) res.add("আপনার রাজা চেকে আছে — প্রস্তাবিত চালগুলো রাজাকে বাঁচায়।")
        val legal = pos.legalMoves()
        val canCastle = legal.any { abs(pos.b[it and 63]) == 6 && abs(((it shr 6) and 63) - (it and 63)) == 2 }
        val mask = if (me) 3 else 12
        val home = if (me) 60 else 4
        if (canCastle) res.add("ক্যাসেল এখন সম্ভব — রাজাকে নিরাপদ ও নৌকাকে সক্রিয় করতে ক্যাসেল বিবেচনা করুন।")
        else if (ksq == home && (pos.castle and mask) == 0) res.add("ক্যাসেলের সুযোগ আর নেই — রাজা কেন্দ্রে, সতর্ক থাকুন।")
        else if (ksq == home && (pos.castle and mask) != 0) res.add("ক্যাসেলের অধিকার আছে, মাঝের ঘুঁটি সরিয়ে ক্যাসেলের প্রস্তুতি নিন।")
        val r = ksq / 8
        val c = ksq % 8
        var shield = 0
        val fr = if (me) r - 1 else r + 1
        if (fr in 0..7) for (dc in -1..1) {
            val nc = c + dc
            if (nc in 0..7 && pos.b[fr * 8 + nc] == (if (me) 1 else -1)) shield++
        }
        if (shield < 2 && ksq != home) res.add("রাজার সামনে বোড়ের আড়াল কম ($shield) — আক্রমণের ঝুঁকি।")
        var pressure = 0
        for (dr in -1..1) for (dc in -1..1) {
            val nr = r + dr
            val nc = c + dc
            if (nr in 0..7 && nc in 0..7 && pos.attacked(nr * 8 + nc, !me)) pressure++
        }
        if (pressure >= 3) res.add("রাজার চারপাশের $pressure টি ঘর শত্রুর আক্রমণে — রক্ষণ মজবুত করুন।")
        if (best != null && best.tags.any { it.startsWith("চেকমেট") }) res.add("এই চালেই শত্রুর রাজা চেকমেট!")
        if (res.isEmpty()) res.add("রাজা এখন নিরাপদ অবস্থানে আছে।")
        return res
    }

    // ---------- Stockfish-based helpers ----------

    private fun toLine(pos: Position, el: EngineLine): Line? {
        val p = pos.copy()
        val mvs = ArrayList<Int>()
        for (u in el.pv) {
            val m = uciToMove(p, u) ?: break
            mvs.add(m)
            p.make(m)
        }
        if (mvs.isEmpty()) return null
        return Line(mvs[0], el.cp, el.depth, mvs)
    }

    private fun findTrapsSf(pos: Position, sf: StockfishEngine, bestCp: Int, lastEnemy: Int, cancelled: () -> Boolean): List<String> {
        val res = ArrayList<String>()
        val fen = pos.toFen()
        val caps = pos.legalMoves()
            .filter { pos.b[(it shr 6) and 63] != 0 }
            .map { it to see(pos, it) }
            .filter { it.second >= 100 }
            .sortedByDescending { it.second }
            .take(3)
        for ((m, _) in caps) {
            if (cancelled()) break
            val l = sf.analyze(fen, 600, 1, moveToUci(m), cancelled, null).firstOrNull() ?: continue
            if (l.cp < bestCp - 150) {
                val t = (m shr 6) and 63
                val victim = abs(pos.b[t])
                val lastTo = if (lastEnemy >= 0) (lastEnemy shr 6) and 63 else -1
                val extra = if (t == lastTo) " (শত্রুর শেষ চালটাই ফাঁদ!)" else ""
                res.add("ফাঁদ! ${sanSafe(pos, m)} দিয়ে ${NAME[victim]} ধরা লোভনীয়, কিন্তু গভীর হিসাবে ক্ষতি (${scoreText(l.cp)}) — ধরবেন না$extra")
            }
        }
        return res
    }

    /** Does our best move set a trap for the enemy (a tempting capture that loses for him)? */
    private fun trapWeSet(pos: Position, best: Candidate, sf: StockfishEngine, cancelled: () -> Boolean): String? {
        val np = pos.copy()
        np.make(best.move)
        val caps = np.legalMoves()
            .filter { np.b[(it shr 6) and 63] != 0 }
            .map { it to see(np, it) }
            .filter { it.second >= 100 }
            .take(2)
        if (caps.isEmpty() || cancelled()) return null
        val fen = np.toFen()
        val base = sf.analyze(fen, 500, 1, null, cancelled, null).firstOrNull() ?: return null
        for ((m, _) in caps) {
            if (cancelled()) return null
            val l = sf.analyze(fen, 500, 1, moveToUci(m), cancelled, null).firstOrNull() ?: continue
            if (l.cp < base.cp - 150) {
                val victim = abs(np.b[(m shr 6) and 63])
                return "ফাঁদ বসানো: ${best.san} খেললে শত্রু লোভে ${NAME[victim]} ধরলে (${sanSafe(np, m)}) নিজেই ক্ষতিতে পড়বে।"
            }
        }
        return null
    }

    // ---------- main entry ----------

    fun analyze(
        pos: Position,
        lastEnemy: Int,
        prev: Position?,
        totalMs: Long,
        plyCount: Int,
        sf: StockfishEngine?,
        cancelled: () -> Boolean,
        onProgress: (Int, Int) -> Unit
    ): Analysis {
        var lines: List<Line> = emptyList()
        var search: Search? = null
        var engineName = "আরাফাত ইঞ্জিন"
        if (sf != null && sf.isAlive()) {
            val el = sf.analyze(pos.toFen(), totalMs, 3, null, cancelled, onProgress)
            lines = el.mapNotNull { toLine(pos, it) }
            if (lines.isNotEmpty()) engineName = sf.version
        }
        if (lines.isEmpty() && !cancelled()) {
            val s = Search(pos.copy())
            s.cancelCheck = cancelled
            search = s
            lines = s.multiPv(3, totalMs, 24)
            engineName = "আরাফাত ইঞ্জিন"
        }
        val base = lines.map { l ->
            Candidate(
                move = l.move,
                san = san(pos, l.move),
                fromTo = fromTo(l.move),
                cp = l.score,
                scoreText = scoreText(l.score),
                pvSan = pvSan(pos, l.pv, 8),
                tags = tagsFor(pos, l.move),
                pv = l.pv
            )
        }
        val cands = base.mapIndexed { i, c ->
            val why = ArrayList<String>(Explainer.reasons(pos, c.move, true))
            if (i == 0 && base.size > 1) {
                val d = c.cp - base[1].cp
                if (abs(c.cp) < MATE - 100) {
                    if (d >= 100) why.add("পরের সেরা চালের চেয়ে প্রায় ${String.format(Locale.US, "%.1f", d / 100.0)} বোড়ে ভালো — অন্য চালে অবস্থান বা ম্যাটেরিয়াল হারাতে পারেন।")
                    else if (d < 30) why.add("কাছাকাছি মানের একাধিক ভালো চাল আছে — নিচের বিকল্পগুলোও চলবে।")
                }
            }
            c.copy(why = why, longTerm = Explainer.longTerm(pos, c.pv, c.cp))
        }
        val bestCp = cands.firstOrNull()?.cp ?: 0
        val threats = enemyThreats(pos)
        val traps: List<String> = when {
            cands.isEmpty() || cancelled() -> emptyList()
            sf != null && sf.isAlive() && search == null -> findTrapsSf(pos, sf, bestCp, lastEnemy, cancelled)
            search != null -> findTraps(pos, search, bestCp, lastEnemy, cancelled)
            else -> emptyList()
        }

        val attacks = ArrayList<String>()
        for (c in cands) for (tg in c.tags) {
            if (tg.startsWith("ফর্ক") || tg.startsWith("পিন") || tg.startsWith("চেক") ||
                tg.startsWith("ডিসকভার্ড") || tg.startsWith("প্রোমোশন")
            ) attacks.add("${c.san}: $tg")
        }
        if (sf != null && sf.isAlive() && search == null && cands.isNotEmpty() && !cancelled()) {
            val tw = trapWeSet(pos, cands[0], sf, cancelled)
            if (tw != null) attacks.add(0, tw)
        }
        if (bestCp > MATE - 100) attacks.add(0, "ফোর্সড মেট পাওয়া গেছে — ${scoreText(bestCp)}")
        if (cands.isNotEmpty() && cands[0].pvSan.isNotEmpty()) attacks.add("পরিকল্পিত লাইন: ${cands[0].pvSan}")

        val plan = when {
            bestCp > MATE - 100 -> "জোরালো আক্রমণ — সরাসরি মেটের পথ আছে!"
            bestCp < -(MATE - 100) -> "মেটের বিপদ — এই প্রস্তাবিত চালই সবচেয়ে কঠিন প্রতিরোধ।"
            bestCp >= 250 -> "আপনি অনেক এগিয়ে: ঘুঁটি এক্সচেঞ্জ করে সহজ জয়ের দিকে যান, রাজার নিরাপত্তা ঠিক রাখুন।"
            bestCp >= 80 -> "সামান্য এগিয়ে: চাপ বজায় রাখুন, শত্রুর দুর্বল ঘুঁটি টার্গেট করুন।"
            bestCp > -80 -> "সমান অবস্থা: ঘুঁটি ডেভেলপ করুন, কেন্দ্র নিয়ন্ত্রণে রাখুন, শত্রুর ভুলের সুযোগ নিন।"
            bestCp > -250 -> "সামান্য পিছিয়ে: সতর্ক রক্ষণ করুন এবং শত্রুকে ভুলের দিকে ঠেলে দেওয়ার ফাঁদ খুঁজুন।"
            else -> "বড় বিপদে: সবচেয়ে কঠিন প্রতিরোধ দিন, পারপেচুয়াল চেক বা স্টেলমেটের সুযোগ খুঁজুন।"
        }
        val whiteCp = if (pos.white) bestCp else -bestCp
        val depth = lines.firstOrNull()?.depth ?: 0
        val note = if (prev != null && lastEnemy >= 0) Explainer.enemyNote(prev, lastEnemy).ifEmpty { null } else null
        return Analysis(
            cands, threats, traps, attacks.distinct().take(7),
            kingAdvice(pos, cands.firstOrNull()), plan, depth, whiteCp, note, engineName
        )
    }
}
