package de.tizian.hamsterrad

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

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

    /** Summe ab Montag 00:00 dieser Woche. */
    fun thisWeek(): Pair<Int, Int> {
        val monday = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                add(Calendar.DAY_OF_MONTH, -1)
            }
        }.timeInMillis
        val week = all.filter { it.start >= monday }
        return week.size to week.sumOf { it.distanceM }
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
