package com.arafat.chessmaster.engine

import java.util.Locale
import kotlin.math.abs

/** Produces human-readable (Bengali) reasons and long-term consequences of a move. */
object Explainer {
    private val CENTER = setOf(27, 28, 35, 36)

    private fun nm(t: Int) = Analyzer.NAME[t]

    private fun npm(p: Position): Int {
        var s = 0
        for (x in p.b) {
            val t = abs(x)
            if (t in 2..5) s += VALS[t]
        }
        return s
    }

    private fun material(p: Position, white: Boolean): Int {
        var s = 0
        for (x in p.b) {
            if (x == 0 || (x > 0) != white) continue
            val t = abs(x)
            if (t in 1..5) s += VALS[t]
        }
        return s
    }

    private fun queens(p: Position): Int = p.b.count { abs(it) == 5 }

    fun hanging(p: Position, white: Boolean): List<Int> {
        val res = ArrayList<Int>()
        for (sq in 0 until 64) {
            val x = p.b[sq]
            if (x == 0 || (x > 0) != white) continue
            val t = abs(x)
            if (t == 6) continue
            val a = leastAttacker(p.b, sq, !white)
            if (a < 0) continue
            val av = VALS[abs(p.b[a])]
            val defended = leastAttacker(p.b, sq, white) >= 0
            if (!defended || av < VALS[t]) res.add(sq)
        }
        return res
    }

    private fun passed(p: Position, white: Boolean): Int {
        var cnt = 0
        val mine = if (white) 1 else -1
        for (s in 0 until 64) {
            if (p.b[s] != mine) continue
            val r = s / 8
            val c = s % 8
            var ok = true
            for (s2 in 0 until 64) {
                if (p.b[s2] != -mine) continue
                val r2 = s2 / 8
                val c2 = s2 % 8
                if (abs(c2 - c) <= 1 && (if (white) r2 < r else r2 > r)) { ok = false; break }
            }
            if (ok) cnt++
        }
        return cnt
    }

    private fun shield(p: Position, white: Boolean): Int {
        val k = p.kp[if (white) 0 else 1]
        val r = k / 8
        val c = k % 8
        val fr = if (white) r - 1 else r + 1
        if (fr !in 0..7) return 0
        var n = 0
        for (dc in -1..1) {
            val nc = c + dc
            if (nc in 0..7 && p.b[fr * 8 + nc] == (if (white) 1 else -1)) n++
        }
        return n
    }

    private fun openRooks(p: Position, white: Boolean): Int {
        var n = 0
        val rook = if (white) 4 else -4
        val pawn = if (white) 1 else -1
        for (s in 0 until 64) {
            if (p.b[s] != rook) continue
            val c = s % 8
            var own = false
            for (r in 0 until 8) if (p.b[r * 8 + c] == pawn) own = true
            if (!own) n++
        }
        return n
    }

    private fun castled(p: Position, white: Boolean): Boolean {
        val k = p.kp[if (white) 0 else 1]
        return if (white) (k == 62 || k == 58) else (k == 6 || k == 2)
    }

    /** Why is this move played? `mine` = true when the move is for the user's side. */
    fun reasons(pos: Position, m: Int, mine: Boolean): List<String> {
        val res = ArrayList<String>()
        val own = if (mine) "আপনার" else "শত্রুর"
        val opp = if (mine) "শত্রুর" else "আপনার"
        val f = m and 63
        val t = (m shr 6) and 63
        val pr = (m shr 12) and 7
        val me = pos.white
        val type = abs(pos.b[f])
        val isEp = type == 1 && pos.b[t] == 0 && f % 8 != t % 8
        val victim = if (isEp) 1 else abs(pos.b[t])
        val np = pos.copy()
        np.make(m)

        if (np.inCheck(!me) && np.legalMoves().isEmpty()) {
            res.add("এই চালেই সরাসরি চেকমেট — খেলা এখানেই শেষ।")
            return res
        }
        if (type == 6 && abs(t - f) == 2) {
            res.add("ক্যাসেল: রাজা কোণে নিরাপদ হলো আর নৌকা কেন্দ্রের দিকে সক্রিয় হলো। মিডলগেমে রাজা কেন্দ্রে থাকা ঝুঁকিপূর্ণ।")
        }
        if (victim != 0) {
            val g = see(pos, m)
            when {
                g >= 100 -> res.add("${nm(victim)} জেতা হলো — পাল্টা ধরার সুযোগ নেই বা কম, তাই নিট ম্যাটেরিয়াল লাভ।")
                g >= -50 -> res.add("${nm(victim)} ধরে সমমূল্যের বিনিময় — অবস্থান সরল হয় এবং $opp আক্রমণের ঘুঁটি কমে।")
                else -> res.add("${nm(victim)} ধরা হলো — কিছু ম্যাটেরিয়াল ছেড়ে অবস্থানগত বা আক্রমণাত্মক সুবিধা নেওয়ার চাল।")
            }
        }
        if (pr != 0) res.add("বোড়ে প্রোমোশন: ${nm(pr)} পাওয়া গেল — ম্যাটেরিয়ালে বিশাল লাভ।")

        val tags = Analyzer.tagsFor(pos, m)
        if (tags.any { it == "চেক" }) res.add("চেক দিয়ে $opp রাজাকে জবাব দিতে বাধ্য করা হলো — এতে আক্রমণের গতি $own হাতে থাকে।")
        if (tags.any { it.startsWith("ডিসকভার্ড") }) res.add("ঘুঁটি সরতেই পেছনের ঘুঁটির লুকানো আক্রমণ খুলে গেল (ডিসকভার্ড চেক)।")
        if (tags.any { it.startsWith("ফর্ক") }) res.add("ফর্ক: একটি ঘুঁটি একসাথে একাধিক ঘুঁটিকে আক্রমণ করছে — $opp একটাই বাঁচাতে পারবে, অন্যটি হারাবে।")
        if (tags.any { it.startsWith("পিন") }) res.add("পিন: আটকে থাকা ঘুঁটি নড়লে পেছনের দামি ঘুঁটি বিপদে পড়ে — তাই $opp ঘুঁটিটি অচল।")

        val hb = hanging(pos, me)
        val ha = hanging(np, me)
        if (victim == 0 && f in hb && t !in ha) {
            res.add("$own ${nm(type)} (${sqName(f)}) আক্রমণের মুখে ছিল — নিরাপদ ঘরে সরানো হলো।")
        } else if (hb.isNotEmpty() && ha.size < hb.size) {
            res.add("$opp হুমকি সামলানো হলো — আক্রান্ত ঘুঁটিকে রক্ষা করা হলো।")
        }
        val before = hanging(pos, !me).toSet()
        val created = hanging(np, !me).filter { it !in before }
        if (created.isNotEmpty()) {
            val sq = created[0]
            res.add("$opp ${nm(abs(np.b[sq]))} (${sqName(sq)}) কে আক্রমণ করা হলো — $opp কে জবাব দিতে বাধ্য করে।")
        }
        if ((type == 2 || type == 3) && (if (me) f / 8 == 7 else f / 8 == 0) && pos.plies < 30) {
            res.add("ডেভেলপমেন্ট: ${nm(type)} পেছনের সারি থেকে সক্রিয় ঘরে এল — ওপেনিংয়ে দ্রুত ঘুঁটি নামানো জরুরি।")
        }
        if (type == 1 && t in CENTER) res.add("বোড়ে দিয়ে কেন্দ্র দখল — কেন্দ্র দখলে থাকলে ঘুঁটিরা বেশি ঘরে প্রভাব ফেলতে পারে।")
        else if ((type == 2 || type == 3) && t in CENTER) res.add("${nm(type)} কেন্দ্রীয় ঘরে — সেখান থেকে বেশি ঘরে নিয়ন্ত্রণ থাকে।")
        if (type == 4) {
            val c = t % 8
            var ownPawn = false
            for (r in 0 until 8) if (np.b[r * 8 + c] == (if (me) 1 else -1)) ownPawn = true
            if (!ownPawn) res.add("নৌকা খোলা ফাইলে — নিজের বোড়ের বাধা নেই, তাই দূর পর্যন্ত চাপ দেওয়া যায়।")
        }
        if (type == 1 && passed(np, me) > passed(pos, me)) res.add("পাসড (বাধাহীন) বোড়ে তৈরি হলো — এন্ডগেমে এটি প্রমোশনের বড় হুমকি।")
        if (type == 6 && abs(t - f) != 2 && npm(pos) <= 2600) res.add("এন্ডগেমে রাজা সক্রিয় হওয়া জরুরি — রাজা এখন যুদ্ধের ঘুঁটি।")
        if (res.isEmpty()) res.add("ঘুঁটির সমন্বয় ও অবস্থান উন্নত করার চাল — গভীর হিসাবে এটিই সবচেয়ে ভালো ভারসাম্য দেয়।")
        return res
    }

    private fun fmt(v: Int): String = String.format(Locale.US, "%.1f", v / 100.0)

    /** What does the move lead to in the long run (follows the engine's principal variation). */
    fun longTerm(pos: Position, pv: List<Int>, cp: Int): List<String> {
        val res = ArrayList<String>()
        val me = pos.white
        val end = pos.copy()
        var applied = 0
        for (m in pv.take(10)) {
            if (!end.legalMoves().contains(m)) break
            end.make(m)
            applied++
        }
        if (applied >= 2) {
            val dm = (material(end, me) - material(end, !me)) - (material(pos, me) - material(pos, !me))
            if (dm >= 100) res.add("লাইনটি শেষ হলে আপনি প্রায় ${fmt(dm)} বোড়ের সমান ম্যাটেরিয়াল এগিয়ে থাকবেন।")
            else if (dm <= -100) res.add("এই লাইনে কিছু ম্যাটেরিয়াল ছাড়তে হয়, বিনিময়ে অবস্থান বা আক্রমণের সুবিধা মেলে — ইঞ্জিনের হিসাবে এটি লাভজনক।")
            if (passed(end, me) > passed(pos, me)) res.add("আপনার পাসড বোড়ে তৈরি হবে — এন্ডগেমে এটি প্রমোশনের বড় অস্ত্র।")
            if (passed(end, !me) < passed(pos, !me)) res.add("শত্রুর পাসড বোড়ের হুমকি দূর হবে।")
            if (!castled(pos, me) && castled(end, me)) res.add("লাইনে আপনার রাজা ক্যাসেল করে নিরাপদ হবে।")
            if (shield(end, !me) < shield(pos, !me)) res.add("শত্রুর রাজার সামনের বোড়ের আড়াল ভাঙবে — পরে আক্রমণের সুযোগ তৈরি হবে।")
            if (shield(end, me) < shield(pos, me)) res.add("সতর্কতা: আপনার রাজার সামনের আড়াল কিছুটা দুর্বল হতে পারে — রক্ষণে নজর রাখুন।")
            if (openRooks(end, me) > openRooks(pos, me)) res.add("আপনার নৌকা খোলা ফাইলে সক্রিয় হবে — শত্রুর পেছনের সারিতে চাপ বাড়বে।")
            if (queens(pos) > 0 && queens(end) == 0) res.add("মন্ত্রী বিনিময় হবে — খেলা সরল হয়ে এন্ডগেমের দিকে যাবে।")
            if (npm(pos) > 2600 && npm(end) <= 2600) res.add("খেলা এন্ডগেমে ঢুকবে — রাজা সক্রিয় করা ও পাসড বোড়ে এগোনোই তখন মূল কৌশল।")
        }
        when {
            cp > MATE - 100 -> res.add("ফোর্সড মেটের পথ আছে — খেলা আপনার পূর্ণ নিয়ন্ত্রণে।")
            cp < -(MATE - 100) -> res.add("মেটের বিপদ আছে — এই চালই টিকে থাকার সবচেয়ে কঠিন প্রতিরোধ।")
            cp >= 250 -> res.add("দীর্ঘমেয়াদে আপনি জয়ের পথে — অকারণ ঝুঁকি না নিয়ে এক্সচেঞ্জ করে সহজ করুন।")
            cp >= 80 -> res.add("অবস্থান আপনার পক্ষে সামান্য ভালো — এই সুবিধা ধীরে ধীরে বাড়ানোই লক্ষ্য।")
            cp > -80 -> res.add("অবস্থান সমান — এই চাল ভারসাম্য ধরে রেখে পরের সুযোগের অপেক্ষা করে।")
            cp > -250 -> res.add("অবস্থান কিছুটা কঠিন — এই চাল ক্ষতি সীমিত রাখে; শত্রুর ভুলের অপেক্ষা করুন।")
            else -> res.add("কঠিন অবস্থা — এই চালই টিকে থাকার সেরা চেষ্টা; পারপেচুয়াল চেক বা স্টেলমেটের সুযোগ খুঁজুন।")
        }
        return res
    }

    /** Short note about what the enemy's move was aiming at (pos = position BEFORE the move). */
    fun enemyNote(pos: Position, m: Int): String {
        return try {
            reasons(pos, m, false).take(2).joinToString(" ")
        } catch (e: Exception) {
            ""
        }
    }
}
