package com.inez.tvlauncher

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

data class AgendaEvent(val sortKey: Int, val time: String, val title: String)

/**
 * Lê o endereço secreto (iCal) do Google Calendar e devolve os eventos de hoje.
 * Suporta repetições simples (diária, semanal, mensal, anual).
 */
object IcsParser {
    private val dowCodes = arrayOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")

    fun eventsForToday(ics: String): List<AgendaEvent> {
        val today = Calendar.getInstance()
        val text = ics.replace("\r\n ", "").replace("\r\n", "\n").replace("\n ", "")
        val out = ArrayList<AgendaEvent>()
        var inEvent = false
        var summary = ""
        var dtKey = ""
        var dtVal = ""
        var rrule: String? = null
        for (line in text.split("\n")) {
            when {
                line.startsWith("BEGIN:VEVENT") -> {
                    inEvent = true; summary = ""; dtKey = ""; dtVal = ""; rrule = null
                }
                line.startsWith("END:VEVENT") -> {
                    if (inEvent && dtKey.isNotEmpty()) {
                        val parsed = parseDt(dtKey, dtVal)
                        if (parsed != null) {
                            val (start, allDay) = parsed
                            if (matchesToday(start, rrule, today)) {
                                val time = if (allDay) "Dia todo"
                                else SimpleDateFormat("HH:mm", Locale.US).format(start.time)
                                val key = if (allDay) -1 else start.get(Calendar.HOUR_OF_DAY) * 60 + start.get(Calendar.MINUTE)
                                out.add(AgendaEvent(key, time, summary.ifEmpty { "(sem título)" }))
                            }
                        }
                    }
                    inEvent = false
                }
                inEvent -> {
                    val idx = line.indexOf(':')
                    if (idx > 0) {
                        val key = line.substring(0, idx)
                        val value = line.substring(idx + 1)
                        when {
                            key.startsWith("SUMMARY") -> summary = value.replace("\\,", ",").replace("\\;", ";").replace("\\n", " ")
                            key.startsWith("DTSTART") -> { dtKey = key; dtVal = value }
                            key.startsWith("RRULE") -> rrule = value
                        }
                    }
                }
            }
        }
        return out.sortedBy { it.sortKey }
    }

    private fun parseDt(key: String, value: String): Pair<Calendar, Boolean>? {
        return try {
            if (value.length == 8) {
                val d = SimpleDateFormat("yyyyMMdd", Locale.US).parse(value)!!
                return Pair(Calendar.getInstance().apply { time = d }, true)
            }
            val tz = Regex("TZID=([^;:]+)").find(key)?.groupValues?.get(1)
            val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
            val cal = Calendar.getInstance()
            if (value.endsWith("Z")) {
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                cal.time = fmt.parse(value.dropLast(1))!!
            } else {
                if (tz != null) fmt.timeZone = TimeZone.getTimeZone(tz)
                cal.time = fmt.parse(value)!!
            }
            Pair(cal, false)
        } catch (e: Exception) {
            null
        }
    }

    private fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun midnight(c: Calendar): Calendar = (c.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    private fun matchesToday(start: Calendar, rrule: String?, today: Calendar): Boolean {
        if (sameDay(start, today)) return true
        if (rrule == null) return false
        val startDay = midnight(start)
        val todayStart = midnight(today)
        if (todayStart.before(startDay)) return false
        val parts = rrule.split(";").associate {
            val kv = it.split("=")
            kv[0] to kv.getOrElse(1) { "" }
        }
        val until = parts["UNTIL"]
        if (until != null && until.length >= 8) {
            val todayStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(today.time)
            if (todayStr > until.substring(0, 8)) return false
        }
        val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
        val days = ((todayStart.timeInMillis - startDay.timeInMillis) / 86400000.0).roundToInt()
        return when (parts["FREQ"]) {
            "DAILY" -> days % interval == 0
            "WEEKLY" -> {
                val byday = parts["BYDAY"]
                val dow = dowCodes[todayStart.get(Calendar.DAY_OF_WEEK) - 1]
                val okDay = if (byday != null) byday.split(",").any { it.takeLast(2) == dow }
                else start.get(Calendar.DAY_OF_WEEK) == todayStart.get(Calendar.DAY_OF_WEEK)
                okDay && (days / 7) % interval == 0
            }
            "MONTHLY" -> start.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)
            "YEARLY" -> start.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                start.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)
            else -> false
        }
    }
}
