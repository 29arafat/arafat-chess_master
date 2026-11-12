package com.arafat.chessmaster.engine

import kotlin.math.abs

const val MATE = 30000
const val INF = 32000

class Line(val move: Int, val score: Int, val depth: Int, val pv: List<Int>)

/** Alpha-beta search (iterative deepening, quiescence, killer/history ordering, LMR). */
class Search(val pos: Position) {
    private val ev = Evaluator()
    var nodes = 0L
    private var deadline = 0L
    private var stop = false
    var cancelCheck: () -> Boolean = { false }

    private val bufs = Array(80) { IntArray(256) }
    private val scrs = Array(80) { IntArray(256) }
    private val killers = Array(80) { IntArray(2) }
    private val history = Array(2) { Array(64) { IntArray(64) } }
    private val pv = Array(82) { IntArray(82) }
    private val pvLen = IntArray(82)
    private var excluded = IntArray(0)
    private var rootBest = -1

    private fun evalStm(): Int {
        val e = ev.evaluate(pos)
        return if (pos.white) e else -e
    }

    private fun tick(): Boolean {
        nodes++
        if ((nodes and 1023L) == 0L) {
            if (System.currentTimeMillis() > deadline || cancelCheck()) stop = true
        }
        return stop
    }

    private fun order(ply: Int, n: Int, quiet: Boolean) {
        val buf = bufs[ply]
        val scr = scrs[ply]
        val side = if (pos.white) 0 else 1
        for (i in 0 until n) {
            val m = buf[i]
            val f = m and 63
            val t = (m shr 6) and 63
            val pr = (m shr 12) and 7
            val victim = abs(pos.b[t])
            val att = abs(pos.b[f])
            scr[i] = when {
                ply == 0 && m == rootBest -> 1_000_000
                victim != 0 -> 100_000 + victim * 100 - att
                pr != 0 -> 90_000 + pr
                !quiet -> 0
                m == killers[ply][0] -> 80_000
                m == killers[ply][1] -> 70_000
                else -> minOf(history[side][f][t], 60_000)
            }
        }
    }

    private fun pick(ply: Int, i: Int, n: Int) {
        val buf = bufs[ply]
        val scr = scrs[ply]
        var bi = i
        for (j in i + 1 until n) if (scr[j] > scr[bi]) bi = j
        if (bi != i) {
            val tm = buf[i]; buf[i] = buf[bi]; buf[bi] = tm
            val ts = scr[i]; scr[i] = scr[bi]; scr[bi] = ts
        }
    }

    private fun quiesce(alpha0: Int, beta: Int, ply: Int): Int {
        if (tick()) return 0
        if (ply >= 70) return evalStm()
        val inChk = pos.inCheck(pos.white)
        var alpha = alpha0
        var stand = -INF
        if (!inChk) {
            stand = evalStm()
            if (stand >= beta) return stand
            if (stand > alpha) alpha = stand
        }
        val buf = bufs[ply]
        val n = pos.gen(buf, !inChk)
        order(ply, n, false)
        var legal = 0
        for (i in 0 until n) {
            pick(ply, i, n)
            val m = buf[i]
            if (!inChk) {
                val victim = abs(pos.b[(m shr 6) and 63])
                if (((m shr 12) and 7) == 0 && stand + VALS[victim] + 200 < alpha) continue
            }
            pos.make(m)
            if (pos.moverInCheck()) { pos.unmake(m); continue }
            legal++
            val sc = -quiesce(-beta, -alpha, ply + 1)
            pos.unmake(m)
            if (stop) return 0
            if (sc >= beta) return sc
            if (sc > alpha) alpha = sc
        }
        if (inChk && legal == 0) return -MATE + ply
        return alpha
    }

    private fun search(depth: Int, alpha0: Int, beta: Int, ply: Int): Int {
        pvLen[ply] = ply
        if (tick()) return 0
        if (ply > 0 && (pos.half >= 100 || pos.insufficient())) return 0
        if (ply >= 70) return evalStm()
        val inChk = pos.inCheck(pos.white)
        val d = if (inChk) depth + 1 else depth
        if (d <= 0) return quiesce(alpha0, beta, ply)

        var alpha = alpha0
        val buf = bufs[ply]
        val n = pos.gen(buf, false)
        order(ply, n, true)
        var legal = 0
        var best = -INF
        val side = if (pos.white) 0 else 1
        for (i in 0 until n) {
            pick(ply, i, n)
            val m = buf[i]
            if (ply == 0 && excluded.contains(m)) continue
            val isCap = pos.b[(m shr 6) and 63] != 0
            val isPromo = ((m shr 12) and 7) != 0
            pos.make(m)
            if (pos.moverInCheck()) { pos.unmake(m); continue }
            legal++
            val gives = pos.inCheck(pos.white)
            val sc: Int
            if (legal > 4 && d >= 3 && !isCap && !isPromo && !inChk && !gives) {
                var s1 = -search(d - 2, -alpha - 1, -alpha, ply + 1)
                if (s1 > alpha && !stop) s1 = -search(d - 1, -beta, -alpha, ply + 1)
                sc = s1
            } else {
                sc = -search(d - 1, -beta, -alpha, ply + 1)
            }
            pos.unmake(m)
            if (stop) return 0
            if (sc > best) {
                best = sc
                if (sc > alpha) {
                    alpha = sc
                    pv[ply][ply] = m
                    for (j in ply + 1 until pvLen[ply + 1]) pv[ply][j] = pv[ply + 1][j]
                    pvLen[ply] = maxOf(pvLen[ply + 1], ply + 1)
                    if (alpha >= beta) {
                        if (!isCap && !isPromo) {
                            if (killers[ply][0] != m) {
                                killers[ply][1] = killers[ply][0]
                                killers[ply][0] = m
                            }
                            val f = m and 63
                            val t = (m shr 6) and 63
                            history[side][f][t] += d * d
                        }
                        break
                    }
                }
            }
        }
        if (legal == 0) return if (inChk) -MATE + ply else 0
        return best
    }

    fun searchBest(maxDepth: Int, timeMs: Long, excl: IntArray): Line? {
        excluded = excl
        stop = false
        nodes = 0
        deadline = System.currentTimeMillis() + timeMs
        rootBest = -1
        var best: Line? = null
        for (d in 1..maxDepth) {
            val sc = search(d, -INF, INF, 0)
            if (stop) break
            if (pvLen[0] > 0) {
                val moves = ArrayList<Int>()
                for (i in 0 until pvLen[0]) moves.add(pv[0][i])
                best = Line(pv[0][0], sc, d, moves)
                rootBest = pv[0][0]
            } else {
                break
            }
            if (abs(sc) > MATE - 100) break
        }
        return best
    }

    /** Best `count` distinct root moves (MultiPV by exclusion). */
    fun multiPv(count: Int, totalMs: Long, maxDepth: Int): List<Line> {
        val res = ArrayList<Line>()
        var excl = IntArray(0)
        for (i in 0 until count) {
            val ms = if (i == 0) totalMs * 55 / 100 else totalMs * 22 / 100
            val l = searchBest(maxDepth, ms, excl) ?: break
            res.add(l)
            excl += l.move
            if (cancelCheck()) break
        }
        return res
    }

    /** Score (from the mover's view) of playing move m then searching depth-1. Null if aborted/illegal. */
    fun evalMove(m: Int, depth: Int, ms: Long): Int? {
        stop = false
        excluded = IntArray(0)
        deadline = System.currentTimeMillis() + ms
        pos.make(m)
        if (pos.moverInCheck()) { pos.unmake(m); return null }
        val sc = -search(depth - 1, -INF, INF, 1)
        pos.unmake(m)
        return if (stop) null else sc
    }
}
