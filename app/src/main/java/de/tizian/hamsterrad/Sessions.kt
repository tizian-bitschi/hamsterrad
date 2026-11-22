package de.tizian.hamsterrad

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Sessions {

    private const val FILE = "sessions.json"

    val all = mutableStateListOf<Session>()

    fun load(context: Context) {
        if (all.isNotEmpty()) return
        val f = file(context)
        if (!f.exists()) return
        runCatching {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                all.add(
                    Session(
                        start = o.getLong("start"),
                        end = o.getLong("end"),
                        seconds = o.getInt("seconds"),
                        distanceM = o.getInt("distanceM"),
                        kcal = o.getDouble("kcal"),
                        hrAvg = o.getInt("hrAvg"),
                        hrMax = o.getInt("hrMax"),
                    ),
                )
            }
        }
    }

    fun append(context: Context, s: Session) {
        all.add(0, s)
        persist(context)
    }

    /** Ersetzt eine gespeicherte Session, wenn die Session eine Trennung überlebt hat. */
    fun replace(context: Context, old: Session, new: Session) {
        val index = all.indexOf(old)
        if (index < 0) {
            append(context, new)
            return
        }
        all[index] = new
        persist(context)
    }

    fun remove(context: Context, s: Session) {
        if (all.remove(s)) persist(context)
    }

    /** Legt eine versehentlich gelöschte Session an ihren alten Platz zurück. */
    fun insert(context: Context, index: Int, s: Session) {
        if (all.any { it.start == s.start }) return
        all.add(index.coerceIn(0, all.size), s)
        persist(context)
    }

    private fun persist(context: Context) {
        val arr = JSONArray()
        for (x in all) {
            arr.put(
                JSONObject()
                    .put("start", x.start)
                    .put("end", x.end)
                    .put("seconds", x.seconds)
                    .put("distanceM", x.distanceM)
                    .put("kcal", x.kcal)
                    .put("hrAvg", x.hrAvg)
                    .put("hrMax", x.hrMax),
            )
        }
        runCatching { file(context).writeText(arr.toString()) }
    }

    private fun file(context: Context): File = File(context.filesDir, FILE)
}
