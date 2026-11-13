package com.arafat.chessmaster.data

import android.content.Context
import com.arafat.chessmaster.engine.Position
import com.arafat.chessmaster.engine.uciToMove
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class MoveRec(
    val uci: String,
    val san: String,
    val whiteMoved: Boolean,
    val mine: Boolean,
    val why: String
)

data class SavedGame(
    val id: Long,
    val startFen: String,
    val myWhite: Boolean,
    val moves: List<MoveRec>,
    val recorded: Boolean
)

/** result: "W" (জয়), "L" (হার), "D" (ড্র) */
data class GameRecord(
    val id: Long,
    val startFen: String,
    val myWhite: Boolean,
    val result: String,
    val moves: List<MoveRec>
)

/** Position after the first n moves of a recorded game, plus the last move (or -1). */
fun replayTo(rec: GameRecord, n: Int): Pair<Position, Int> {
    var p = Position.fromFen(rec.startFen) ?: Position.start()
    var last = -1
    for (i in 0 until minOf(n, rec.moves.size)) {
        val m = uciToMove(p, rec.moves[i].uci) ?: break
        val np = p.copy()
        np.make(m)
        p = np
        last = m
    }
    return Pair(p, last)
}

class Store(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("chess_master", Context.MODE_PRIVATE)
    private val histFile = File(ctx.filesDir, "history.json")

    var level: Int
        get() = prefs.getInt("level", 1)
        set(v) { prefs.edit().putInt("level", v).apply() }

    var palette: Int
        get() = prefs.getInt("palette", 0)
        set(v) { prefs.edit().putInt("palette", v).apply() }

    private fun movesToJson(moves: List<MoveRec>): JSONArray {
        val arr = JSONArray()
        for (m in moves) {
            val o = JSONObject()
            o.put("u", m.uci)
            o.put("s", m.san)
            o.put("w", m.whiteMoved)
            o.put("m", m.mine)
            o.put("y", m.why)
            arr.put(o)
        }
        return arr
    }

    private fun jsonToMoves(arr: JSONArray): List<MoveRec> {
        val res = ArrayList<MoveRec>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            res.add(MoveRec(o.getString("u"), o.getString("s"), o.getBoolean("w"), o.getBoolean("m"), o.optString("y", "")))
        }
        return res
    }

    fun saveCurrent(g: SavedGame?) {
        if (g == null) {
            prefs.edit().remove("current").apply()
            return
        }
        val o = JSONObject()
        o.put("id", g.id)
        o.put("fen", g.startFen)
        o.put("white", g.myWhite)
        o.put("rec", g.recorded)
        o.put("moves", movesToJson(g.moves))
        prefs.edit().putString("current", o.toString()).apply()
    }

    fun loadCurrent(): SavedGame? {
        val s = prefs.getString("current", null) ?: return null
        return try {
            val o = JSONObject(s)
            SavedGame(o.getLong("id"), o.getString("fen"), o.getBoolean("white"), jsonToMoves(o.getJSONArray("moves")), o.optBoolean("rec", false))
        } catch (e: Exception) {
            null
        }
    }

    fun loadHistory(): List<GameRecord> {
        if (!histFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(histFile.readText())
            val res = ArrayList<GameRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                res.add(GameRecord(o.getLong("id"), o.getString("fen"), o.getBoolean("white"), o.getString("res"), jsonToMoves(o.getJSONArray("moves"))))
            }
            res
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveHistory(list: List<GameRecord>) {
        try {
            val arr = JSONArray()
            for (g in list) {
                val o = JSONObject()
                o.put("id", g.id)
                o.put("fen", g.startFen)
                o.put("white", g.myWhite)
                o.put("res", g.result)
                o.put("moves", movesToJson(g.moves))
                arr.put(o)
            }
            histFile.writeText(arr.toString())
        } catch (e: Exception) {
        }
    }
}
