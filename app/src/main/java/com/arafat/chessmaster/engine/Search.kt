package com.arafat.chessmaster.engine

import kotlin.math.abs

const val MATE = 30000
const val INF = 32000

class Line(val move: Int, val score: Int, val depth: Int, val pv: List<Int>)

/**
 * Own engine: alpha-beta (PVS) with iterative deepening, transposition table (Zobrist),
 * null-move pruning, late-move reductions, killer/history ordering, quiescence search.
 */
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

    private val ttSize = 1 shl 19
    private val ttMask = (1 shl 19) - 1
    private val ttKey = LongArray(1 shl 19)
    private val ttMove = IntArray(1 shl 19)
    private val ttScore = IntArray(1 shl 19)
    private val ttInfo = IntArray(1 shl 19)

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

    private fun toTT(score: Int, ply: Int): Int =
        if (score > MATE - 100) score + ply else if (score < -MATE + 100) score - ply else score

    private fun fromTT(score: Int, ply: Int): Int =
        if (score > MATE - 100) score - ply else if (score < -MATE + 100) score + ply else score

    private fun ttIndex(key: Long): Int = ((key xor (key ushr 29)).toInt()) and ttMask

    private fun ttStore(key: Long, mv: Int, score: Int, depth: Int, flag: Int, ply: Int) {
        val i = ttIndex(key)
        ttKey[i] = key
        ttMove[i] = mv
        ttScore[i] = toTT(score, ply)
        ttInfo[i] = (depth shl 2) or flag
    }

    private fun hasPieces(white: Boolean): Boolean {
        for (x in pos.b) {
            if (x == 0 || (x > 0) != white) continue
            val t = abs(x)
            if (t in 2..5) return true
        }
        return false
    }

    private fun order(ply: Int, n: Int, quiet: Boolean, ttMv: Int) {
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
                ttMv != 0 && m == ttMv -> 900_000
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
        order(ply, n, false, 0)
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

    private fun search(depth: Int, alpha0: Int, beta: Int, ply: Int, allowNull: Boolean): Int {
        pvLen[ply] = ply
        if (tick()) return 0
        if (ply > 0 && (pos.half >= 100 || pos.insufficient() || pos.repeated())) return 0
        if (ply >= 70) return evalStm()
        val inChk = pos.inCheck(pos.white)
        val d = if (inChk) depth + 1 else depth
        if (d <= 0) return quiesce(alpha0, beta, ply)

        val isPv = beta - alpha0 > 1
        val key = pos.hash
        val ti = ttIndex(key)
        var ttMv = 0
        if (ttKey[ti] == key) {
            ttMv = ttMove[ti]
            if (ply > 0 && !isPv) {
                val info = ttInfo[ti]
                val dep = info shr 2
                val flag = info and 3
                if (dep >= d) {
                    val sc = fromTT(ttScore[ti], ply)
                    if (flag == 0) return sc
                    if (flag == 1 && sc >= beta) return sc
                    if (flag == 2 && sc <= alpha0) return sc
                }
            }
        }

        // null-move pruning
        if (allowNull && !isPv && !inChk && d >= 3 && ply > 0 && hasPieces(pos.white) && evalStm() >= beta) {
            val r = if (d >= 6) 3 else 2
            pos.makeNull()
            val sc = -search(d - 1 - r, -beta, -beta + 1, ply + 1, false)
            pos.unmakeNull()
            if (stop) return 0
            if (sc >= beta) return if (sc > MATE - 100) beta else sc
        }

        var alpha = alpha0
        val buf = bufs[ply]
        val n = pos.gen(buf, false)
        order(ply, n, true, ttMv)
        var legal = 0
        var best = -INF
        var bestMv = 0
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
            var sc: Int
            if (legal == 1) {
                sc = -search(d - 1, -beta, -alpha, ply + 1, true)
            } else {
                var red = 0
                if (legal > 4 && d >= 3 && !isCap && !isPromo && !inChk && !gives) {
                    red = if (legal > 10 && d >= 5) 2 else 1
                }
                sc = -search(d - 1 - red, -alpha - 1, -alpha, ply + 1, true)
                if (sc > alpha && (red > 0 || sc < beta)) {
                    sc = -search(d - 1, -beta, -alpha, ply + 1, true)
                }
            }
            pos.unmake(m)
            if (stop) return 0
            if (sc > best) {
                best = sc
                bestMv = m
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
        if (!stop && ply > 0) {
            val flag = if (best >= beta) 1 else if (best > alpha0) 0 else 2
            ttStore(key, if (flag == 2) 0 else bestMv, best, d, flag, ply)
        }
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
            val sc = search(d, -INF, INF, 0, false)
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
        val sc = -search(depth - 1, -INF, INF, 1, true)
        pos.unmake(m)
        return if (stop) null else sc
    }
}
