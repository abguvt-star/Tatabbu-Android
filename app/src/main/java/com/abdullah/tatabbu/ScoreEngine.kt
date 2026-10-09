package com.abdullah.tatabbu

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * An organisational score, never a judgement about religious acceptance.
 * Missing entries are NOT considered proof of missed worship.
 */
enum class PrayerKind(val label: String) {
    FAJR("الفجر"), DHUHR("الظهر"), ASR("العصر"), MAGHRIB("المغرب"), ISHA("العشاء")
}

enum class PrayerStatus { NOT_RECORDED, PRAYED, MISSED }

data class PrayerLog(
    val status: PrayerStatus = PrayerStatus.NOT_RECORDED,
    val onTime: Boolean = false,
    val inMosque: Boolean = false,
    val openingTakbir: Boolean = false
)

data class DayLog(
    val prayers: Map<PrayerKind, PrayerLog> = emptyMap(),
    val fridayPrayer: PrayerLog = PrayerLog(),
    val adhkar: Map<String, Boolean> = emptyMap(),
    val quranRead: Boolean = false,
    val extras: Map<String, Boolean> = emptyMap(),
    val friday: Map<String, Boolean> = emptyMap()
)

object Catalog {
    val adhkar = linkedMapOf(
        "morning" to "أذكار الصباح",
        "evening" to "أذكار المساء",
        "sleep" to "أذكار النوم",
        "after_prayer" to "أذكار بعد الصلاة",
        "other" to "أذكار أخرى"
    )
    val extras = linkedMapOf(
        "rawatib" to "السنن الرواتب",
        "duha" to "صلاة الضحى",
        "witr" to "صلاة الوتر",
        "supplements" to "المكملات حسب الخطة",
        "movement" to "النشاط البدني",
        "sleep_plan" to "الالتزام بخطة النوم"
    )
    val friday = linkedMapOf(
        "kahf" to "قراءة سورة الكهف",
        "salawat" to "الصلاة على النبي ﷺ",
        "ghusl" to "الاغتسال للجمعة"
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
    val hasAnyData: Boolean
)

object ScoreEngine {
    /** 50 time + 30 mosque + 20 opening takbir, if prayer was performed. */
    fun prayerScore(log: PrayerLog): Int {
        if (log.status != PrayerStatus.PRAYED) return 0
        return (if (log.onTime) 50 else 0) +
            (if (log.inMosque) 30 else 0) +
            (if (log.inMosque && log.openingTakbir) 20 else 0)
    }

    /** On Friday, Jumu'ah replaces Dhuhr if it was performed; otherwise use logged Dhuhr. */
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

    fun score(date: LocalDate, day: DayLog): DayScores {
        val effective = PrayerKind.entries.map { effectivePrayer(date, day, it) }
        val prayers = (effective.sumOf(::prayerScore).toDouble() / 5.0).roundToInt()
        val adhkar = percent(Catalog.adhkar.keys.count { day.adhkar[it] == true }, Catalog.adhkar.size)
        val quran = if (day.quranRead) 100 else 0
        val extras = percent(Catalog.extras.keys.count { day.extras[it] == true }, Catalog.extras.size)
        val isFriday = date.dayOfWeek == DayOfWeek.FRIDAY
        val friday = if (isFriday) {
            percent(Catalog.friday.keys.count { day.friday[it] == true }, Catalog.friday.size)
        } else null
        val total = if (isFriday) {
            (prayers * .45 + adhkar * .20 + quran * .10 + extras * .10 + (friday ?: 0) * .15).roundToInt()
        } else {
            (prayers * .50 + adhkar * .25 + quran * .15 + extras * .10).roundToInt()
        }
        val hasAnyData = day.prayers.values.any { it.status != PrayerStatus.NOT_RECORDED } ||
            day.fridayPrayer.status != PrayerStatus.NOT_RECORDED ||
            day.adhkar.values.any { it } || day.quranRead ||
            day.extras.values.any { it } || day.friday.values.any { it }
        return DayScores(prayers, adhkar, quran, extras, friday, total,
            effective.count { it.status != PrayerStatus.NOT_RECORDED }, hasAnyData)
    }
}
