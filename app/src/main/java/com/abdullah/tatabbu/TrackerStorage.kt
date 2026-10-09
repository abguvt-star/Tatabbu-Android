package com.abdullah.tatabbu

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/** Offline, on-device storage. Manual JSON export/import is available in Settings. */
class TrackerStorage(context: Context) {
    private val prefs = context.getSharedPreferences("tatabbu_store_v1", Context.MODE_PRIVATE)

    fun load(): Map<String, DayLog> = runCatching {
        parse(prefs.getString("days", "") ?: "")
    }.getOrDefault(emptyMap())

    fun save(days: Map<String, DayLog>) {
        prefs.edit().putString("days", export(days)).apply()
    }

    fun export(days: Map<String, DayLog>): String {
        val root = JSONObject().put("version", 1)
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
            all.put(date, item)
        }
        return root.put("days", all).toString(2)
    }

    fun parse(json: String): Map<String, DayLog> {
        if (json.isBlank()) return emptyMap()
        val root = JSONObject(json)
        require(root.optInt("version", -1) == 1) { "Unsupported backup version" }
        val all = root.getJSONObject("days")
        val result = mutableMapOf<String, DayLog>()
        for (date in all.keys()) {
            LocalDate.parse(date) // reject invalid dates
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
                friday = jsonToBoolMap(item.optJSONObject("friday"))
            )
        }
        return result
    }

    private fun prayerToJson(p: PrayerLog) = JSONObject()
        .put("status", p.status.name)
        .put("onTime", p.onTime)
        .put("inMosque", p.inMosque)
        .put("openingTakbir", p.openingTakbir)

    private fun prayerFromJson(obj: JSONObject): PrayerLog {
        val status = runCatching {
            PrayerStatus.valueOf(obj.optString("status", "NOT_RECORDED"))
        }.getOrDefault(PrayerStatus.NOT_RECORDED)
        val mosque = obj.optBoolean("inMosque", false)
        return PrayerLog(
            status = status,
            onTime = obj.optBoolean("onTime", false),
            inMosque = mosque,
            openingTakbir = mosque && obj.optBoolean("openingTakbir", false)
        )
    }

    private fun boolMapToJson(map: Map<String, Boolean>): JSONObject {
        val json = JSONObject()
        map.forEach { (key, value) -> json.put(key, value) }
        return json
    }

    private fun jsonToBoolMap(obj: JSONObject?): Map<String, Boolean> {
        if (obj == null) return emptyMap()
        return obj.keys().asSequence().associateWith { obj.optBoolean(it, false) }
    }
}
