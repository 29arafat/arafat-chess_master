package com.arafat.chessmaster.engine

import java.io.BufferedWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

data class EngineLine(val multipv: Int, val depth: Int, val cp: Int, val pv: List<String>)

/** Talks UCI to the Stockfish binary that is packed inside the APK (libstockfish.so). */
class StockfishEngine(private val path: String) {
    private var proc: Process? = null
    private var writer: BufferedWriter? = null
    private val lines = LinkedBlockingQueue<String>()
    @Volatile private var alive = false
    private val lock = Any()
    var version: String = "Stockfish"
        private set

    fun start(threads: Int, hashMb: Int): Boolean {
        return try {
            val pb = ProcessBuilder(path)
            pb.redirectErrorStream(true)
            val p = pb.start()
            proc = p
            writer = p.outputStream.bufferedWriter()
            alive = true
            val t = Thread {
                try {
                    p.inputStream.bufferedReader().forEachLine { lines.put(it) }
                } catch (e: Exception) {
                }
                alive = false
            }
            t.isDaemon = true
            t.start()
            send("uci")
            if (!waitFor("uciok", 8000)) { quit(); return false }
            send("setoption name Threads value $threads")
            send("setoption name Hash value $hashMb")
            send("setoption name MultiPV value 1")
            send("isready")
            if (!waitFor("readyok", 8000)) { quit(); return false }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun send(s: String) {
        try {
            writer?.write(s)
            writer?.write("\n")
            writer?.flush()
        } catch (e: Exception) {
            alive = false
        }
    }

    private fun waitFor(token: String, ms: Long): Boolean {
        val end = System.currentTimeMillis() + ms
        while (true) {
            val rem = end - System.currentTimeMillis()
            if (rem <= 0) return false
            val l = lines.poll(rem, TimeUnit.MILLISECONDS) ?: return false
            if (l.startsWith("id name ")) version = l.substring(8).trim()
            if (l.startsWith(token)) return true
        }
    }

    fun isAlive(): Boolean = alive

    /** Runs a search and returns the best lines (sorted by multipv index). */
    fun analyze(
        fen: String,
        moveTimeMs: Long,
        multiPv: Int,
        searchMove: String?,
        cancelled: () -> Boolean,
        onProgress: ((Int, Int) -> Unit)?
    ): List<EngineLine> {
        return synchronized(lock) {
            if (!alive) return@synchronized emptyList<EngineLine>()
            lines.clear()
            send("setoption name MultiPV value $multiPv")
            send("position fen $fen")
            send("go movetime $moveTimeMs" + (if (searchMove != null) " searchmoves $searchMove" else ""))
            val best = HashMap<Int, EngineLine>()
            var stopped = false
            val hardEnd = System.currentTimeMillis() + moveTimeMs + 6000
            while (true) {
                if (!stopped && (cancelled() || System.currentTimeMillis() > hardEnd)) {
                    send("stop")
                    stopped = true
                }
                val l = lines.poll(100, TimeUnit.MILLISECONDS)
                if (l == null) {
                    if (!alive) break
                    if (stopped && System.currentTimeMillis() > hardEnd + 5000) break
                    continue
                }
                if (l.startsWith("bestmove")) break
                if (l.startsWith("info ") && l.contains(" pv ")) {
                    val t = l.split(' ')
                    var depth = 0
                    var mpv = 1
                    var cp = 0
                    var hasScore = false
                    var pvStart = -1
                    var i = 1
                    while (i < t.size) {
                        when (t[i]) {
                            "depth" -> { depth = t.getOrNull(i + 1)?.toIntOrNull() ?: depth; i += 2 }
                            "multipv" -> { mpv = t.getOrNull(i + 1)?.toIntOrNull() ?: mpv; i += 2 }
                            "score" -> {
                                val kind = t.getOrNull(i + 1)
                                val v = t.getOrNull(i + 2)?.toIntOrNull() ?: 0
                                if (kind == "cp") cp = v
                                else if (kind == "mate") cp = if (v > 0) MATE - (2 * v - 1) else -(MATE - 2 * (-v))
                                hasScore = true
                                i += 3
                            }
                            "pv" -> { pvStart = i + 1; i = t.size }
                            else -> i++
                        }
                    }
                    if (hasScore && pvStart >= 0 && pvStart < t.size) {
                        val old = best[mpv]
                        if (old == null || depth >= old.depth) {
                            best[mpv] = EngineLine(mpv, depth, cp, t.subList(pvStart, t.size))
                        }
                        if (mpv == 1 && onProgress != null) onProgress(depth, cp)
                    }
                }
            }
            best.values.sortedBy { it.multipv }
        }
    }

    fun quit() {
        try {
            send("quit")
            proc?.destroy()
        } catch (e: Exception) {
        }
        alive = false
    }
}
