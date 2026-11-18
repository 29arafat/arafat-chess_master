package com.arafat.chessmaster.engine

import java.util.Arrays

private val PAWN = intArrayOf(
    0, 0, 0, 0, 0, 0, 0, 0,
    50, 50, 50, 50, 50, 50, 50, 50,
    10, 10, 20, 30, 30, 20, 10, 10,
    5, 5, 10, 25, 25, 10, 5, 5,
    0, 0, 0, 20, 20, 0, 0, 0,
    5, -5, -10, 0, 0, -10, -5, 5,
    5, 10, 10, -20, -20, 10, 10, 5,
    0, 0, 0, 0, 0, 0, 0, 0
)
private val KN = intArrayOf(
    -50, -40, -30, -30, -30, -30, -40, -50,
    -40, -20, 0, 0, 0, 0, -20, -40,
    -30, 0, 10, 15, 15, 10, 0, -30,
    -30, 5, 15, 20, 20, 15, 5, -30,
    -30, 0, 15, 20, 20, 15, 0, -30,
    -30, 5, 10, 15, 15, 10, 5, -30,
    -40, -20, 0, 5, 5, 0, -20, -40,
    -50, -40, -30, -30, -30, -30, -40, -50
)
private val BI = intArrayOf(
    -20, -10, -10, -10, -10, -10, -10, -20,
    -10, 0, 0, 0, 0, 0, 0, -10,
    -10, 0, 5, 10, 10, 5, 0, -10,
    -10, 5, 5, 10, 10, 5, 5, -10,
    -10, 0, 10, 10, 10, 10, 0, -10,
    -10, 10, 10, 10, 10, 10, 10, -10,
    -10, 5, 0, 0, 0, 0, 5, -10,
    -20, -10, -10, -10, -10, -10, -10, -20
)
private val RO = intArrayOf(
    0, 0, 0, 0, 0, 0, 0, 0,
    5, 10, 10, 10, 10, 10, 10, 5,
    -5, 0, 0, 0, 0, 0, 0, -5,
    -5, 0, 0, 0, 0, 0, 0, -5,
    -5, 0, 0, 0, 0, 0, 0, -5,
    -5, 0, 0, 0, 0, 0, 0, -5,
    -5, 0, 0, 0, 0, 0, 0, -5,
    0, 0, 0, 5, 5, 0, 0, 0
)
private val QU = intArrayOf(
    -20, -10, -10, -5, -5, -10, -10, -20,
    -10, 0, 0, 0, 0, 0, 0, -10,
    -10, 0, 5, 5, 5, 5, 0, -10,
    -5, 0, 5, 5, 5, 5, 0, -5,
    0, 0, 5, 5, 5, 5, 0, -5,
    -10, 5, 5, 5, 5, 5, 0, -10,
    -10, 0, 5, 0, 0, 0, 0, -10,
    -20, -10, -10, -5, -5, -10, -10, -20
)
private val KMG = intArrayOf(
    -30, -40, -40, -50, -50, -40, -40, -30,
    -30, -40, -40, -50, -50, -40, -40, -30,
    -30, -40, -40, -50, -50, -40, -40, -30,
    -30, -40, -40, -50, -50, -40, -40, -30,
    -20, -30, -30, -40, -40, -30, -30, -20,
    -10, -20, -20, -20, -20, -20, -20, -10,
    20, 20, 0, 0, 0, 0, 20, 20,
    20, 30, 10, 0, 0, 10, 30, 20
)
private val KEG = intArrayOf(
    -50, -40, -30, -20, -20, -30, -40, -50,
    -30, -20, -10, 0, 0, -10, -20, -30,
    -30, -10, 20, 30, 30, 20, -10, -30,
    -30, -10, 30, 40, 40, 30, -10, -30,
    -30, -10, 30, 40, 40, 30, -10, -30,
    -30, -10, 20, 30, 30, 20, -10, -30,
    -30, -30, 0, 0, 0, 0, -30, -30,
    -50, -30, -30, -30, -30, -30, -30, -50
)
private val PASS = intArrayOf(0, 10, 20, 35, 60, 100, 150, 200)

/** Static evaluation, centipawns from WHITE's point of view. */
class Evaluator {
    private val wCnt = IntArray(8)
    private val bCnt = IntArray(8)
    private val wMax = IntArray(8)
    private val bMin = IntArray(8)

    private fun mobility(b: IntArray, s: Int, t: Int, white: Boolean): Int {
        val r = s / 8
        val c = s % 8
        var cnt = 0
        if (t == 2) {
            for (i in 0 until 8) {
                val nr = r + KDR[i]
                val nc = c + KDC[i]
                if (nr in 0..7 && nc in 0..7) {
                    val x = b[nr * 8 + nc]
                    if (x == 0 || (x > 0) != white) cnt++
                }
            }
        } else {
            val lo = if (t == 3) 4 else 0
            val hi = if (t == 4) 4 else 8
            for (d in lo until hi) {
                var nr = r + DR[d]
                var nc = c + DC[d]
                while (nr in 0..7 && nc in 0..7) {
                    val x = b[nr * 8 + nc]
                    if (x == 0) {
                        cnt++
                    } else {
                        if ((x > 0) != white) cnt++
                        break
                    }
                    nr += DR[d]
                    nc += DC[d]
                }
            }
        }
        return cnt
    }

    fun evaluate(p: Position): Int {
        val b = p.b
        Arrays.fill(wCnt, 0)
        Arrays.fill(bCnt, 0)
        Arrays.fill(wMax, -1)
        Arrays.fill(bMin, 8)
        var score = 0
        var npm = 0
        var wBis = 0
        var bBis = 0
        var wk = -1
        var bk = -1
        var queens = 0
        for (s in 0 until 64) {
            val x = b[s]
            if (x == 0) continue
            val c = s % 8
            val r = s / 8
            if (x > 0) {
                when (x) {
                    1 -> { score += 100 + PAWN[s]; wCnt[c]++; if (r > wMax[c]) wMax[c] = r }
                    2 -> { score += 320 + KN[s] + 4 * mobility(b, s, 2, true); npm += 320 }
                    3 -> { score += 330 + BI[s] + 4 * mobility(b, s, 3, true); npm += 330; wBis++ }
                    4 -> { score += 500 + RO[s] + 2 * mobility(b, s, 4, true); npm += 500 }
                    5 -> { score += 900 + QU[s] + mobility(b, s, 5, true); npm += 900; queens++ }
                    6 -> wk = s
                }
            } else {
                val m = s xor 56
                when (-x) {
                    1 -> { score -= 100 + PAWN[m]; bCnt[c]++; if (r < bMin[c]) bMin[c] = r }
                    2 -> { score -= 320 + KN[m] + 4 * mobility(b, s, 2, false); npm += 320 }
                    3 -> { score -= 330 + BI[m] + 4 * mobility(b, s, 3, false); npm += 330; bBis++ }
                    4 -> { score -= 500 + RO[m] + 2 * mobility(b, s, 4, false); npm += 500 }
                    5 -> { score -= 900 + QU[m] + mobility(b, s, 5, false); npm += 900; queens++ }
                    6 -> bk = s
                }
            }
        }
        if (wBis >= 2) score += 30
        if (bBis >= 2) score -= 30

        for (c in 0 until 8) {
            if (wCnt[c] > 1) score -= 12 * (wCnt[c] - 1)
            if (bCnt[c] > 1) score += 12 * (bCnt[c] - 1)
            if (wCnt[c] > 0 && (c == 0 || wCnt[c - 1] == 0) && (c == 7 || wCnt[c + 1] == 0)) score -= 10 * wCnt[c]
            if (bCnt[c] > 0 && (c == 0 || bCnt[c - 1] == 0) && (c == 7 || bCnt[c + 1] == 0)) score += 10 * bCnt[c]
        }

        for (s in 0 until 64) {
            val x = b[s]
            if (x != 1 && x != -1 && x != 4 && x != -4) continue
            val c = s % 8
            val r = s / 8
            when (x) {
                1 -> {
                    var passed = true
                    for (f in maxOf(0, c - 1)..minOf(7, c + 1)) if (bMin[f] < r) passed = false
                    if (passed) score += PASS[6 - r]
                }
                -1 -> {
                    var passed = true
                    for (f in maxOf(0, c - 1)..minOf(7, c + 1)) if (wMax[f] > r) passed = false
                    if (passed) score -= PASS[r - 1]
                }
                4 -> if (wCnt[c] == 0) score += if (bCnt[c] == 0) 20 else 10
                -4 -> if (bCnt[c] == 0) score -= if (wCnt[c] == 0) 20 else 10
            }
        }

        val w = minOf(npm, 4000)
        if (wk >= 0) {
            var k = (KMG[wk] * w + KEG[wk] * (4000 - w)) / 4000
            var sh = 0
            val r = wk / 8
            val c = wk % 8
            if (r > 0) for (dc in -1..1) {
                val nc = c + dc
                if (nc in 0..7 && b[(r - 1) * 8 + nc] == 1) sh += 10
            }
            k += sh * w / 4000
            if (wk == 60 && (p.castle and 3) == 0 && queens > 0) k -= 25 * w / 4000
            score += k
        }
        if (bk >= 0) {
            val m = bk xor 56
            var k = (KMG[m] * w + KEG[m] * (4000 - w)) / 4000
            var sh = 0
            val r = bk / 8
            val c = bk % 8
            if (r < 7) for (dc in -1..1) {
                val nc = c + dc
                if (nc in 0..7 && b[(r + 1) * 8 + nc] == -1) sh += 10
            }
            k += sh * w / 4000
            if (bk == 4 && (p.castle and 12) == 0 && queens > 0) k -= 25 * w / 4000
            score -= k
        }
        return score
    }
}
