package com.abdullah.tatabbu

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/** Personal organisational indicators; not a measure of religious merit or acceptance. */
enum class PrayerKind(val label: String) {
    FAJR("الفجر"), DHUHR("الظهر"), ASR("العصر"), MAGHRIB("المغرب"), ISHA("العشاء")
}
enum class PrayerStatus { NOT_RECORDED, PRAYED, MISSED }

data class PrayerLog(
    val status: PrayerStatus = PrayerStatus.NOT_RECORDED,
    val onTime: Boolean = false,
    val inMosque: Boolean = false,
    val openingTakbir: Boolean = false,
    val inCongregation: Boolean = false
)

data class DayLog(
    val prayers: Map<PrayerKind, PrayerLog> = emptyMap(),
    val fridayPrayer: PrayerLog = PrayerLog(),
    val adhkar: Map<String, Boolean> = emptyMap(),
    val quranRead: Boolean = false,
    val extras: Map<String, Boolean> = emptyMap(),
    val friday: Map<String, Boolean> = emptyMap(),
    val quranStatus: String = "",
    val sunnah: Map<String, String> = emptyMap(),
    val adhkarCounts: Map<String, Int> = emptyMap(),
    val parentsVisited: Boolean = false,
    val notes: String = ""
)

/** A personal repetition target chosen by the user; not a religious ruling on required counts. */
data class DhikrTarget(val title: String, val target: Int)

object Catalog {
    // Keep the 100-count items first, followed by the 33-count items, as in the user's screenshots.
    // Existing IDs for the three original 100-count counters are unchanged to retain saved progress.
    val adhkarCounts = linkedMapOf(
        "hawqala100" to DhikrTarget("لا حول ولا قوة إلا بالله العلي العظيم", 100),
        "istighfar" to DhikrTarget("أستغفر الله وأتوب إليه", 100),
        "tasbih100" to DhikrTarget("سبحان الله وبحمده", 100),
        "tahlil100" to DhikrTarget("لا إله إلا الله وحده لا شريك له، له الملك وله الحمد، وهو على كل شيء قدير", 100),
        "tasbih_double" to DhikrTarget("سبحان الله وبحمده، سبحان الله العظيم", 33),
        "yunus" to DhikrTarget("لا إله إلا أنت سبحانك إني كنت من الظالمين", 33),
        "rabb_inni" to DhikrTarget("رب إني لما أنزلت إلي من خير فقير", 33)
    )
    val sunnah = linkedMapOf(
        "fajr" to "سنة الفجر",
        "dhuhr" to "رواتب الظهر / بعد الجمعة",
        "maghrib" to "سنة المغرب",
        "isha" to "سنة العشاء",
        "witr" to "الوتر"
    )
    val friday = linkedMapOf(
        "dua" to "تحري ساعة الإجابة والدعاء",
        "salawat" to "الصلاة على النبي ﷺ (إضافة اختيارية)"
    )
}

data class DayScores(
    val prayers: Int,
    val adhkar: Int,
    val quran: Int,
    val extras: Int,
    val friday: Int?,
    val total: Int,
    val recordedPrayers: Int,
    val hasAnyData: Boolean,
    val onTime: Int,
    val congregation: Int,
    val openingTakbir: Int,
    val sunnah: Int
)

object ScoreEngine {
    fun prayerScore(log: PrayerLog): Int {
        if (log.status != PrayerStatus.PRAYED) return 0
        return (if (log.onTime) 50 else 0) +
            (if (log.inMosque) 30 else 0) +
            (if (log.inMosque && log.inCongregation && log.openingTakbir) 20 else 0)
    }

    fun effectivePrayer(date: LocalDate, day: DayLog, prayer: PrayerKind): PrayerLog {
        if (date.dayOfWeek != DayOfWeek.FRIDAY || prayer != PrayerKind.DHUHR) {
            return day.prayers[prayer] ?: PrayerLog()
        }
        if (day.fridayPrayer.status == PrayerStatus.PRAYED) return day.fridayPrayer
        val dhuhr = day.prayers[PrayerKind.DHUHR] ?: PrayerLog()
        return if (dhuhr.status != PrayerStatus.NOT_RECORDED) dhuhr else day.fridayPrayer
    }

    private fun percent(done: Int, total: Int): Int =
        if (total == 0) 0 else (100.0 * done / total).roundToInt()

    private fun level(status: String): Int = when (status) {
        "تم", "كامل" -> 100
        "جزئي" -> 50
        else -> 0
    }

    fun quranPercent(day: DayLog): Int =
        if (day.quranStatus.isNotEmpty()) level(day.quranStatus)
        else if (day.quranRead || day.friday["kahf"] == true) 100 else 0

    fun score(date: LocalDate, day: DayLog): DayScores {
        val prayersList = PrayerKind.entries.map { effectivePrayer(date, day, it) }
        val performed = prayersList.count { it.status == PrayerStatus.PRAYED }
        val prayers = percent(performed, 5)
        val onTime = percent(prayersList.count { it.status == PrayerStatus.PRAYED && it.onTime }, 5)
        val congregation = percent(prayersList.count {
            it.status == PrayerStatus.PRAYED && it.inMosque && it.inCongregation
        }, 5)
        val takbir = percent(prayersList.count {
            it.status == PrayerStatus.PRAYED && it.inMosque && it.inCongregation && it.openingTakbir
        }, 5)

        val sunnah = percent(Catalog.sunnah.keys.sumOf { key ->
            when {
                day.sunnah.containsKey(key) -> level(day.sunnah[key] ?: "")
                key == "witr" && day.extras["witr"] == true -> 100
                else -> 0
            }
        }, Catalog.sunnah.size * 100)

        // Each dhikr has equal weight when its own 100/33 target is reached.
        // Unnumbered adhkar from older backups remain stored but do not enter this indicator.
        val adhkar = (Catalog.adhkarCounts.entries.sumOf { (id, item) ->
            100.0 * (day.adhkarCounts[id] ?: 0).coerceIn(0, item.target) / item.target
        } / Catalog.adhkarCounts.size).roundToInt()
        val quran = quranPercent(day)

        // Same daily weights as the Notion religious tracker: 40/20/10/10/10/10.
        // Opening takbir is reported independently and in the per-prayer quality indicator.
        val total = (prayers * .40 + onTime * .20 + congregation * .10 +
            sunnah * .10 + adhkar * .10 + quran * .10).roundToInt()

        val isFriday = date.dayOfWeek == DayOfWeek.FRIDAY
        val friday = if (isFriday) {
            (quran * .50 + (if (day.friday["dua"] == true) 50.0 else 0.0)).roundToInt()
        } else null

        // Notion policy: show daily rating only once a prayer has been recorded.
        val hasAnyData = prayersList.any { it.status != PrayerStatus.NOT_RECORDED }
        return DayScores(
            prayers, adhkar, quran, sunnah, friday, total,
            prayersList.count { it.status != PrayerStatus.NOT_RECORDED },
            hasAnyData, onTime, congregation, takbir, sunnah
        )
    }

    fun rating(score: Int): String = when {
        score >= 90 -> "ممتاز"
        score >= 80 -> "جيد جدًا"
        score >= 65 -> "جيد"
        score >= 50 -> "مقبول"
        else -> "يحتاج انتباه"
    }
}
