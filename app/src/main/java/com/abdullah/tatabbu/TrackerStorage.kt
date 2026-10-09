package com.abdullah.tatabbu

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/** Offline storage; version 2 preserves version 1 records during upgrade. */
class TrackerStorage(context: Context) {
    private val prefs = context.getSharedPreferences("tatabbu_store_v1", Context.MODE_PRIVATE)

    fun load(): Map<String, DayLog> = runCatching {
        parse(prefs.getString("days", "") ?: "")
    }.getOrDefault(emptyMap())

    fun save(days: Map<String, DayLog>) {
        prefs.edit().putString("days", export(days)).apply()
    }

    fun export(days: Map<String, DayLog>): String {
        val root = JSONObject().put("version", 2)
        val all = JSONObject()
        days.forEach { (date, log) ->
            val prayers = JSONObject()
            log.prayers.forEach { (key, value) -> prayers.put(key.name, prayerToJson(value)) }
            val item = JSONObject()
                .put("prayers", prayers)
                .put("fridayPrayer", prayerToJson(log.fridayPrayer))
                .put("adhkar", boolMapToJson(log.adhkar))
                .put("quranRead", log.quranRead)
                .put("extras", boolMapToJson(log.extras))
                .put("friday", boolMapToJson(log.friday))
                .put("quranStatus", log.quranStatus)
                .put("sunnah", stringMapToJson(log.sunnah))
                .put("adhkarCounts", intMapToJson(log.adhkarCounts))
                .put("parentsVisited", log.parentsVisited)
                .put("notes", log.notes)
            all.put(date, item)
        }
        return root.put("days", all).toString(2)
    }

    fun parse(json: String): Map<String, DayLog> {
        if (json.isBlank()) return emptyMap()
        val root = JSONObject(json)
        require(root.optInt("version", -1) in 1..2) { "Unsupported backup version" }
        val all = root.getJSONObject("days")
        val result = mutableMapOf<String, DayLog>()
        for (date in all.keys()) {
            LocalDate.parse(date)
            val item = all.getJSONObject(date)
            val prayers = mutableMapOf<PrayerKind, PrayerLog>()
            item.optJSONObject("prayers")?.let { obj ->
                for (key in obj.keys()) {
                    val kind = runCatching { PrayerKind.valueOf(key) }.getOrNull() ?: continue
                    prayers[kind] = prayerFromJson(obj.getJSONObject(key))
                }
            }
            result[date] = DayLog(
                prayers = prayers,
                fridayPrayer = item.optJSONObject("fridayPrayer")?.let(::prayerFromJson) ?: PrayerLog(),
                adhkar = jsonToBoolMap(item.optJSONObject("adhkar")),
                quranRead = item.optBoolean("quranRead", false),
                extras = jsonToBoolMap(item.optJSONObject("extras")),
                friday = jsonToBoolMap(item.optJSONObject("friday")),
                quranStatus = item.optString("quranStatus", ""),
                sunnah = jsonToStringMap(item.optJSONObject("sunnah")),
                adhkarCounts = jsonToIntMap(item.optJSONObject("adhkarCounts")),
                parentsVisited = item.optBoolean("parentsVisited", false),
                notes = item.optString("notes", "")
            )
        }
        return result
    }

    private fun prayerToJson(p: PrayerLog) = JSONObject()
        .put("status", p.status.name)
        .put("onTime", p.onTime)
        .put("inMosque", p.inMosque)
        .put("openingTakbir", p.openingTakbir)
        .put("inCongregation", p.inCongregation)

    private fun prayerFromJson(obj: JSONObject): PrayerLog {
        val status = runCatching {
            PrayerStatus.valueOf(obj.optString("status", "NOT_RECORDED"))
        }.getOrDefault(PrayerStatus.NOT_RECORDED)
        val mosque = obj.optBoolean("inMosque", false)
        val congregation = obj.optBoolean("inCongregation", mosque)
        return PrayerLog(
            status = status,
            onTime = obj.optBoolean("onTime", false),
            inMosque = mosque,
            inCongregation = congregation,
            openingTakbir = mosque && congregation && obj.optBoolean("openingTakbir", false)
        )
    }

    private fun boolMapToJson(map: Map<String, Boolean>): JSONObject {
        val json = JSONObject()
        map.forEach { (key, value) -> json.put(key, value) }
        return json
    }
    private fun stringMapToJson(map: Map<String, String>): JSONObject {
        val json = JSONObject()
        map.forEach { (key, value) -> json.put(key, value) }
        return json
    }
    private fun intMapToJson(map: Map<String, Int>): JSONObject {
        val json = JSONObject()
        map.forEach { (key, value) -> json.put(key, value) }
        return json
    }
    private fun jsonToBoolMap(obj: JSONObject?): Map<String, Boolean> {
        if (obj == null) return emptyMap()
        return obj.keys().asSequence().associateWith { obj.optBoolean(it, false) }
    }
    private fun jsonToStringMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        return obj.keys().asSequence().associateWith { obj.optString(it, "") }
    }
    private fun jsonToIntMap(obj: JSONObject?): Map<String, Int> {
        if (obj == null) return emptyMap()
        return obj.keys().asSequence().associateWith { obj.optInt(it, 0).coerceAtLeast(0) }
    }
}
