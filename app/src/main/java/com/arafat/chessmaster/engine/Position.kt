package com.arafat.chessmaster.engine

import kotlin.math.abs

// Board: index = row * 8 + col, row 0 = rank 8 (top), col 0 = file a.
// Pieces: 1 pawn, 2 knight, 3 bishop, 4 rook, 5 queen, 6 king. White > 0, Black < 0.

val DR = intArrayOf(-1, 1, 0, 0, -1, -1, 1, 1)
val DC = intArrayOf(0, 0, -1, 1, -1, 1, -1, 1)
val KDR = intArrayOf(-2, -2, -1, -1, 1, 1, 2, 2)
val KDC = intArrayOf(-1, 1, -2, 2, -2, 2, -1, 1)
val VALS = intArrayOf(0, 100, 320, 330, 500, 900, 20000)

private val CM: IntArray = IntArray(64) { 15 }.also {
    it[60] = 12; it[63] = 14; it[56] = 13
    it[4] = 3; it[7] = 11; it[0] = 7
}

fun mv(f: Int, t: Int, pr: Int = 0): Int = f or (t shl 6) or (pr shl 12)
fun sqName(s: Int): String = "${'a' + (s % 8)}${8 - (s / 8)}"
fun sqFromName(n: String): Int = (n[0] - 'a') + (8 - (n[1] - '0')) * 8

class Position {
    val b = IntArray(64)
    var white = true
    var castle = 0 // 1 = white O-O, 2 = white O-O-O, 4 = black O-O, 8 = black O-O-O
    var ep = -1
    var half = 0
    var plies = 0
    val kp = IntArray(2) // king squares: [0] white, [1] black

    private val capH = IntArray(2048)
    private val castleH = IntArray(2048)
    private val epH = IntArray(2048)
    private val halfH = IntArray(2048)
    private var n = 0

    fun copy(): Position {
        val p = Position()
        System.arraycopy(b, 0, p.b, 0, 64)
        p.white = white; p.castle = castle; p.ep = ep; p.half = half; p.plies = plies
        p.kp[0] = kp[0]; p.kp[1] = kp[1]
        return p
    }

    fun inCheck(w: Boolean): Boolean = attacked(kp[if (w) 0 else 1], !w)

    /** After make(): was the side that just moved left in check (illegal)? */
    fun moverInCheck(): Boolean = attacked(kp[if (white) 1 else 0], white)

    fun attacked(sq: Int, byWhite: Boolean): Boolean {
        val r = sq / 8
        val c = sq % 8
        val pr = if (byWhite) r + 1 else r - 1
        if (pr in 0..7) {
            val pawn = if (byWhite) 1 else -1
            if (c > 0 && b[pr * 8 + c - 1] == pawn) return true
            if (c < 7 && b[pr * 8 + c + 1] == pawn) return true
        }
        val knight = if (byWhite) 2 else -2
        for (i in 0 until 8) {
            val nr = r + KDR[i]
            val nc = c + KDC[i]
            if (nr in 0..7 && nc in 0..7 && b[nr * 8 + nc] == knight) return true
        }
        val king = if (byWhite) 6 else -6
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = r + dr
            val nc = c + dc
            if (nr in 0..7 && nc in 0..7 && b[nr * 8 + nc] == king) return true
        }
        val rook = if (byWhite) 4 else -4
        val bishop = if (byWhite) 3 else -3
        val queen = if (byWhite) 5 else -5
        for (d in 0 until 8) {
            val diag = d >= 4
            var nr = r + DR[d]
            var nc = c + DC[d]
            while (nr in 0..7 && nc in 0..7) {
                val x = b[nr * 8 + nc]
                if (x != 0) {
                    if (x == queen || (diag && x == bishop) || (!diag && x == rook)) return true
                    break
                }
                nr += DR[d]
                nc += DC[d]
            }
        }
        return false
    }

    private fun addPromo(out: IntArray, n0: Int, f: Int, t: Int): Int {
        var k = n0
        for (pr in 5 downTo 2) out[k++] = mv(f, t, pr)
        return k
    }

    /** Pseudo-legal move generation. Returns the number of moves written to out. */
    fun gen(out: IntArray, capsOnly: Boolean): Int {
        var n = 0
        val us = if (white) 1 else -1
        for (s in 0 until 64) {
            val p = b[s]
            if (p * us <= 0) continue
            val t = if (p > 0) p else -p
            val r = s shr 3
            val c = s and 7
            when (t) {
                1 -> {
                    val dir = if (white) -1 else 1
                    val promoRow = if (white) 0 else 7
                    val startRow = if (white) 6 else 1
                    val nr = r + dir
                    if (nr < 0 || nr > 7) continue
                    val fwd = nr * 8 + c
                    if (b[fwd] == 0) {
                        if (nr == promoRow) {
                            n = addPromo(out, n, s, fwd)
                        } else if (!capsOnly) {
                            out[n++] = mv(s, fwd)
                            if (r == startRow && b[fwd + dir * 8] == 0) out[n++] = mv(s, fwd + dir * 8)
                        }
                    }
                    for (dc in -1..1 step 2) {
                        val nc = c + dc
                        if (nc < 0 || nc > 7) continue
                        val to = nr * 8 + nc
                        val tp = b[to]
                        if (tp * us < 0 || (tp == 0 && to == ep)) {
                            if (nr == promoRow) n = addPromo(out, n, s, to) else out[n++] = mv(s, to)
                        }
                    }
                }
                2 -> {
                    for (i in 0 until 8) {
                        val nr = r + KDR[i]
                        val nc = c + KDC[i]
                        if (nr < 0 || nr > 7 || nc < 0 || nc > 7) continue
                        val to = nr * 8 + nc
                        val tp = b[to]
                        if (tp * us > 0) continue
                        if (capsOnly && tp == 0) continue
                        out[n++] = mv(s, to)
                    }
                }
                3, 4, 5 -> {
                    val lo = if (t == 4) 0 else if (t == 3) 4 else 0
                    val hi = if (t == 4) 4 else 8
                    for (d in lo until hi) {
                        var nr = r + DR[d]
                        var nc = c + DC[d]
                        while (nr in 0..7 && nc in 0..7) {
                            val to = nr * 8 + nc
                            val tp = b[to]
                            if (tp == 0) {
                                if (!capsOnly) out[n++] = mv(s, to)
                            } else {
                                if (tp * us < 0) out[n++] = mv(s, to)
                                break
                            }
                            nr += DR[d]
                            nc += DC[d]
                        }
                    }
                }
                6 -> {
                    for (dr in -1..1) for (dc in -1..1) {
                        if (dr == 0 && dc == 0) continue
                        val nr = r + dr
                        val nc = c + dc
                        if (nr < 0 || nr > 7 || nc < 0 || nc > 7) continue
                        val to = nr * 8 + nc
                        val tp = b[to]
                        if (tp * us > 0) continue
                        if (capsOnly && tp == 0) continue
                        out[n++] = mv(s, to)
                    }
                    if (!capsOnly) {
                        if (white && s == 60) {
                            if ((castle and 1) != 0 && b[61] == 0 && b[62] == 0 && b[63] == 4 &&
                                !attacked(60, false) && !attacked(61, false) && !attacked(62, false)
                            ) out[n++] = mv(60, 62)
                            if ((castle and 2) != 0 && b[59] == 0 && b[58] == 0 && b[57] == 0 && b[56] == 4 &&
                                !attacked(60, false) && !attacked(59, false) && !attacked(58, false)
                            ) out[n++] = mv(60, 58)
                        } else if (!white && s == 4) {
                            if ((castle and 4) != 0 && b[5] == 0 && b[6] == 0 && b[7] == -4 &&
                                !attacked(4, true) && !attacked(5, true) && !attacked(6, true)
                            ) out[n++] = mv(4, 6)
                            if ((castle and 8) != 0 && b[3] == 0 && b[2] == 0 && b[1] == 0 && b[0] == -4 &&
                                !attacked(4, true) && !attacked(3, true) && !attacked(2, true)
                            ) out[n++] = mv(4, 2)
                        }
                    }
                }
            }
        }
        return n
    }

    fun make(m: Int) {
        val f = m and 63
        val t = (m shr 6) and 63
        val pr = (m shr 12) and 7
        val p = b[f]
        val type = abs(p)
        val us = if (p > 0) 1 else -1
        capH[n] = b[t]; castleH[n] = castle; epH[n] = ep; halfH[n] = half; n++
        var resetHalf = b[t] != 0 || type == 1
        if (type == 1 && t == ep && b[t] == 0 && f % 8 != t % 8) {
            val cs = if (us == 1) t + 8 else t - 8
            capH[n - 1] = b[cs]
            b[cs] = 0
            resetHalf = true
        }
        b[t] = if (pr != 0) us * pr else p
        b[f] = 0
        if (type == 6) {
            kp[if (us == 1) 0 else 1] = t
            if (t - f == 2) {
                b[t - 1] = b[t + 1]; b[t + 1] = 0
            } else if (f - t == 2) {
                b[t + 1] = b[t - 2]; b[t - 2] = 0
            }
        }
        castle = castle and CM[f] and CM[t]
        ep = if (type == 1 && abs(t - f) == 16) (f + t) / 2 else -1
        half = if (resetHalf) 0 else half + 1
        white = !white
        plies++
    }

    fun unmake(m: Int) {
        n--
        val f = m and 63
        val t = (m shr 6) and 63
        val pr = (m shr 12) and 7
        white = !white
        plies--
        val us = if (white) 1 else -1
        val moved = b[t]
        val p = if (pr != 0) us else moved
        castle = castleH[n]; half = halfH[n]
        val oldEp = epH[n]
        ep = oldEp
        b[f] = p
        val type = abs(p)
        if (type == 1 && t == oldEp && f % 8 != t % 8) {
            b[t] = 0
            val cs = if (us == 1) t + 8 else t - 8
            b[cs] = capH[n]
        } else {
            b[t] = capH[n]
        }
        if (type == 6) {
            kp[if (us == 1) 0 else 1] = f
            if (t - f == 2) {
                b[t + 1] = b[t - 1]; b[t - 1] = 0
            } else if (f - t == 2) {
                b[t - 2] = b[t + 1]; b[t + 1] = 0
            }
        }
    }

    fun legalMoves(): List<Int> {
        val buf = IntArray(256)
        val cnt = gen(buf, false)
        val res = ArrayList<Int>()
        for (i in 0 until cnt) {
            val m = buf[i]
            make(m)
            val ok = !moverInCheck()
            unmake(m)
            if (ok) res.add(m)
        }
        return res
    }

    fun insufficient(): Boolean {
        var minors = 0
        for (x in b) {
            val t = abs(x)
            if (t == 1 || t == 4 || t == 5) return false
            if (t == 2 || t == 3) minors++
        }
        return minors <= 1
    }

    fun key(): String {
        val sb = StringBuilder(80)
        for (i in 0 until 64) sb.append((b[i] + 77).toChar())
        sb.append(if (white) 'w' else 'b').append(castle)
        return sb.toString()
    }

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        fun start(): Position = fromFen(START_FEN)!!

        fun fromFen(fen: String): Position? {
            return try {
                val parts = fen.trim().split(Regex("\\s+"))
                val p = Position()
                var r = 0
                var c = 0
                for (ch in parts[0]) {
                    when {
                        ch == '/' -> { r++; c = 0 }
                        ch.isDigit() -> c += ch - '0'
                        else -> {
                            val t = when (ch.lowercaseChar()) {
                                'p' -> 1; 'n' -> 2; 'b' -> 3; 'r' -> 4; 'q' -> 5; 'k' -> 6
                                else -> return null
                            }
                            p.b[r * 8 + c] = if (ch.isUpperCase()) t else -t
                            c++
                        }
                    }
                }
                p.white = parts.getOrElse(1) { "w" } == "w"
                val cs = parts.getOrElse(2) { "-" }
                var cr = 0
                if ('K' in cs) cr = cr or 1
                if ('Q' in cs) cr = cr or 2
                if ('k' in cs) cr = cr or 4
                if ('q' in cs) cr = cr or 8
                if (p.b[60] != 6) cr = cr and 3.inv()
                if (p.b[63] != 4) cr = cr and 1.inv()
                if (p.b[56] != 4) cr = cr and 2.inv()
                if (p.b[4] != -6) cr = cr and 12.inv()
                if (p.b[7] != -4) cr = cr and 4.inv()
                if (p.b[0] != -4) cr = cr and 8.inv()
                p.castle = cr
                val e = parts.getOrElse(3) { "-" }
                p.ep = if (e.length == 2) sqFromName(e) else -1
                p.half = parts.getOrElse(4) { "0" }.toIntOrNull() ?: 0
                var wk = 0
                var bk = 0
                for (i in 0 until 64) {
                    if (p.b[i] == 6) { p.kp[0] = i; wk++ }
                    if (p.b[i] == -6) { p.kp[1] = i; bk++ }
                }
                if (wk != 1 || bk != 1) return null
                p
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** Least valuable attacker square of `white` side on square `to`, or -1. */
fun leastAttacker(b: IntArray, to: Int, white: Boolean): Int {
    val r = to / 8
    val c = to % 8
    val sgn = if (white) 1 else -1
    val pr = if (white) r + 1 else r - 1
    if (pr in 0..7) {
        if (c > 0 && b[pr * 8 + c - 1] == sgn) return pr * 8 + c - 1
        if (c < 7 && b[pr * 8 + c + 1] == sgn) return pr * 8 + c + 1
    }
    for (i in 0 until 8) {
        val nr = r + KDR[i]
        val nc = c + KDC[i]
        if (nr in 0..7 && nc in 0..7 && b[nr * 8 + nc] == 2 * sgn) return nr * 8 + nc
    }
    var bestSq = -1
    var bestType = 9
    for (d in 0 until 8) {
        val diag = d >= 4
        var nr = r + DR[d]
        var nc = c + DC[d]
        while (nr in 0..7 && nc in 0..7) {
            val x = b[nr * 8 + nc]
            if (x != 0) {
                if (x * sgn > 0) {
                    val t = abs(x)
                    val ok = t == 5 || (diag && t == 3) || (!diag && t == 4)
                    if (ok && t < bestType) { bestType = t; bestSq = nr * 8 + nc }
                }
                break
            }
            nr += DR[d]
            nc += DC[d]
        }
    }
    if (bestSq >= 0) return bestSq
    for (dr in -1..1) for (dc in -1..1) {
        if (dr == 0 && dc == 0) continue
        val nr = r + dr
        val nc = c + dc
        if (nr in 0..7 && nc in 0..7 && b[nr * 8 + nc] == 6 * sgn) return nr * 8 + nc
    }
    return -1
}

/** Static exchange evaluation of a capture (centipawns, from the mover's view). */
fun see(pos: Position, m: Int): Int {
    val b = pos.b.copyOf()
    val f = m and 63
    val t = (m shr 6) and 63
    var captured = abs(b[t])
    val mover = pos.white
    val piece = abs(b[f])
    if (piece == 1 && captured == 0 && f % 8 != t % 8) {
        captured = 1
        b[if (mover) t + 8 else t - 8] = 0
    }
    val g = IntArray(34)
    var d = 0
    g[0] = VALS[captured]
    var attVal = VALS[piece]
    b[f] = 0
    b[t] = if (mover) piece else -piece
    var side = !mover
    while (d < 32) {
        val a = leastAttacker(b, t, side)
        if (a < 0) break
        d++
        g[d] = attVal - g[d - 1]
        attVal = VALS[abs(b[a])]
        b[t] = b[a]
        b[a] = 0
        side = !side
    }
    while (d > 0) {
        g[d - 1] = -maxOf(-g[d - 1], g[d])
        d--
    }
    return g[0]
}
