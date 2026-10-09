package com.abdullah.tatabbu

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val Green = Color(0xFF168C50)
private val LightGreen = Color(0xFFE7F7EC)
private val DarkText = Color(0xFF192B36)
private val Blue = Color(0xFF2367BC)
private val LightBlue = Color(0xFFEAF2FF)
private val Purple = Color(0xFF7438BE)
private val LightPurple = Color(0xFFF3ECFF)
private val Orange = Color(0xFFBE6615)
private val LightOrange = Color(0xFFFFF3E5)
private val Muted = Color(0xFF647480)
private val Page = Color(0xFFF7F9FC)
private val ArabicLocale = Locale.forLanguageTag("ar-SA")

private enum class Tab(val title: String, val icon: String) {
    TODAY("اليوم", "⌂"), CALENDAR("التقويم", "▦"), STATS("الإحصائيات", "▥"), SETTINGS("الإعدادات", "⚙")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colorScheme = lightColorScheme(
                        primary = Green,
                        onPrimary = Color.White,
                        background = Page,
                        surface = Color.White,
                        onSurface = DarkText
                    )
                ) {
                    TrackerApp()
                }
            }
        }
    }
}

@Composable
private fun TrackerApp() {
    val context = LocalContext.current
    val store = remember(context) { TrackerStorage(context) }
    var logs by remember { mutableStateOf(store.load()) }
    var dateString by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY.name) }
    var monthString by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var editingPrayer by remember { mutableStateOf<PrayerKind?>(null) }
    var editingFridayPrayer by remember { mutableStateOf(false) }

    val date = LocalDate.parse(dateString)
    val month = YearMonth.parse(monthString)
    val log = logs[dateString] ?: DayLog()
    val score = ScoreEngine.score(date, log)

    fun changeDay(transform: (DayLog) -> DayLog) {
        logs = logs + (dateString to transform(logs[dateString] ?: DayLog()))
        store.save(logs)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(store.export(logs))
            } ?: error("Cannot write backup")
        }.onSuccess {
            Toast.makeText(context, "تم حفظ النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "تعذّر حفظ النسخة", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: error("Cannot read backup")
            val imported = store.parse(json)
            // Imported backup replaces the current local records.
            store.save(imported)
            logs = imported
        }.onSuccess {
            Toast.makeText(context, "تم استرجاع النسخة", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "الملف غير صالح للاسترجاع", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = Page,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(Color.White).statusBarsPadding().padding(horizontal = 20.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("تتبّع", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = DarkText)
                    Text("التتبع الديني اليومي", color = Muted, fontSize = 12.sp)
                }
                Text("نسخة 0.3", fontSize = 11.sp, color = Muted)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item.name,
                        onClick = { tab = item.name },
                        icon = { Text(item.icon, fontSize = 23.sp) },
                        label = { Text(item.title, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { inner ->
        when (Tab.valueOf(tab)) {
            Tab.TODAY -> TodayScreen(
                date = date, log = log, scores = score,
                onToday = { dateString = LocalDate.now().toString() },
                onPrayer = { prayer, isFriday ->
                    editingPrayer = prayer
                    editingFridayPrayer = isFriday
                },
                onQuran = { status -> changeDay { it.copy(quranStatus = status, quranRead = status == "كامل") } },
                onSunnah = { id, status -> changeDay { it.copy(sunnah = it.sunnah + (id to status)) } },
                onCount = { id, count -> changeDay { it.copy(adhkarCounts = it.adhkarCounts + (id to count)) } },
                onParents = { done -> changeDay { it.copy(parentsVisited = done) } },
                onFriday = { id, done -> changeDay { it.copy(friday = it.friday + (id to done)) } },
                modifier = Modifier.padding(inner)
            )
            Tab.CALENDAR -> CalendarScreen(
                month = month, logs = logs,
                onMonth = { monthString = it.toString() },
                onSelectDate = {
                    dateString = it.toString()
                    tab = Tab.TODAY.name
                },
                modifier = Modifier.padding(inner)
            )
            Tab.STATS -> StatsScreen(
                month = month, logs = logs,
                onMonth = { monthString = it.toString() },
                modifier = Modifier.padding(inner)
            )
            Tab.SETTINGS -> SettingsScreen(
                onExport = { exportLauncher.launch("tatabbu-${LocalDate.now()}.json") },
                onImport = { importLauncher.launch("application/json") },
                modifier = Modifier.padding(inner)
            )
        }
    }

    editingPrayer?.let { prayer ->
        val friday = editingFridayPrayer
        val initial = if (friday) log.fridayPrayer else (log.prayers[prayer] ?: PrayerLog())
        key(dateString, prayer, friday) {
            PrayerEditor(
                name = if (friday) "صلاة الجمعة" else "صلاة ${prayer.label}",
                initial = initial,
                onDismiss = { editingPrayer = null },
                onSave = { entry ->
                    if (friday) changeDay { it.copy(fridayPrayer = entry) }
                    else changeDay { it.copy(prayers = it.prayers + (prayer to entry)) }
                    editingPrayer = null
                }
            )
        }
    }
}

@Composable
private fun TodayScreen(
    date: LocalDate,
    log: DayLog,
    scores: DayScores,
    onToday: () -> Unit,
    onPrayer: (PrayerKind, Boolean) -> Unit,
    onCount: (String, Int) -> Unit,
    onQuran: (String) -> Unit,
    onSunnah: (String, String) -> Unit,
    onParents: (Boolean) -> Unit,
    onFriday: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isFriday = date.dayOfWeek == DayOfWeek.FRIDAY
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(date.format(DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", ArabicLocale)),
                    fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    HijrahDate.from(date).format(
                        DateTimeFormatter.ofPattern("d MMMM yyyy", ArabicLocale)
                    ) + " هـ",
                    color = Green, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                )
                Text("الصلوات المسجلة: ${scores.recordedPrayers} من 5",
                    fontSize = 12.sp, color = Muted)
            }
            if (date != LocalDate.now()) TextButton(onClick = onToday) { Text("العودة لليوم") }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("مؤشر اليوم الديني", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(7.dp))
                    Text(if (scores.hasAnyData) ScoreEngine.rating(scores.total)
                        else "يظهر التقييم بعد تسجيل أول صلاة",
                        color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("مؤشر متابعة شخصي وليس حكمًا على قبول العبادة",
                        fontSize = 11.sp, color = Muted)
                }
                if (scores.hasAnyData) ScoreCircle(scores.total)
                else Text("—", fontSize = 35.sp, color = Muted)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("أداء الصلاة", scores.prayers, "◈", LightGreen, Green, Modifier.weight(1f))
            MetricCard("في الوقت", scores.onTime, "◷", LightBlue, Blue, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("الجماعة", scores.congregation, "◉", LightPurple, Purple, Modifier.weight(1f))
            MetricCard("السنن والوتر", scores.sunnah, "☾", LightOrange, Orange, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("الأذكار", scores.adhkar, "✧", LightBlue, Blue, Modifier.weight(1f))
            MetricCard("القرآن", scores.quran, "▤", LightGreen, Green, Modifier.weight(1f))
        }

        SectionCard("الصلوات الخمس", "التوقيت والمكان والجماعة وتكبيرة الإحرام لكل صلاة") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                PrayerKind.entries.forEach { prayer ->
                    val current = ScoreEngine.effectivePrayer(date, log, prayer)
                    val title = if (isFriday && prayer == PrayerKind.DHUHR) "الجمعة" else prayer.label
                    Card(
                        onClick = { onPrayer(prayer, isFriday && prayer == PrayerKind.DHUHR) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = when (current.status) {
                            PrayerStatus.PRAYED -> LightGreen
                            PrayerStatus.MISSED -> Color(0xFFFFEAEA)
                            PrayerStatus.NOT_RECORDED -> Page
                        })
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (current.status == PrayerStatus.PRAYED) "✓" else "○",
                                color = if (current.status == PrayerStatus.PRAYED) Green else Muted)
                            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (current.status == PrayerStatus.NOT_RECORDED) "—"
                                else "${ScoreEngine.prayerScore(current)}%", fontSize = 11.sp,
                                color = if (current.status == PrayerStatus.PRAYED) Green else Muted)
                        }
                    }
                }
            }
            Text("تكبيرة الإحرام: ${scores.openingTakbir}% من الصلوات الخمس",
                color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (isFriday) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { onPrayer(PrayerKind.DHUHR, false) }) {
                        Text("تسجيل الظهر بدل الجمعة", fontSize = 11.sp)
                    }
                }
            }
        }

        SectionCard("السنن الرواتب والوتر", "لكل بند: تم أو جزئي أو لم يتم") {
            Catalog.sunnah.forEach { (id, title) ->
                val saved = log.sunnah[id] ?: if (id == "witr" && log.extras["witr"] == true) "تم" else ""
                StatusLine(title, saved, listOf("تم", "جزئي", "لم يتم")) {
                    onSunnah(id, it)
                }
            }
        }

        SectionCard("الأذكار العددية", "فقط الأذكار ذات الهدف ١٠٠ أو ٣٣، مرتبة حسب العدد") {
            val completed = Catalog.adhkarCounts.count { (id, item) ->
                (log.adhkarCounts[id] ?: 0) >= item.target
            }
            Text("الأذكار المكتملة: ${completed} من ${Catalog.adhkarCounts.size}",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Green)
            Text("أذكار ١٠٠ مرة", fontWeight = FontWeight.Bold, color = DarkText)
            Catalog.adhkarCounts.filterValues { it.target == 100 }.forEach { (id, item) ->
                CountLine(item.title, log.adhkarCounts[id] ?: 0, item.target) { onCount(id, it) }
            }
            HorizontalDivider()
            Text("أذكار ٣٣ مرة", fontWeight = FontWeight.Bold, color = DarkText)
            Catalog.adhkarCounts.filterValues { it.target == 33 }.forEach { (id, item) ->
                CountLine(item.title, log.adhkarCounts[id] ?: 0, item.target) { onCount(id, it) }
            }
            Text("الأعداد هنا أهداف شخصية للتتبع وفق قائمتك، وليست حكمًا شرعيًا بعدد الذكر.",
                color = Muted, fontSize = 11.sp)
        }

        SectionCard("القرآن الكريم", if (isFriday)
            "يوم الجمعة: سورة الكهف بدل ورد الختمة" else
            "ورد الختمة: كامل أو جزئي أو لم يتم") {
            val saved = log.quranStatus.ifBlank {
                if (log.quranRead || (isFriday && log.friday["kahf"] == true)) "كامل" else ""
            }
            StatusLine(if (isFriday) "سورة الكهف" else "الورد اليومي",
                saved, listOf("كامل", "جزئي", "لم يتم"), onQuran)
            if (!isFriday) {
                Text("الختمة الحالية في نوشن: 12 سبتمبر – 27 أكتوبر 2026. يوم الجمعة مستثنى من الورد.",
                    fontSize = 12.sp, color = Muted)
            }
        }

        if (isFriday) {
            SectionCard("المتابعة الخاصة بيوم الجمعة", "تقييم مستقل عن نسبة اليوم", LightPurple) {
                CheckLine("تحري ساعة الإجابة والدعاء", log.friday["dua"] == true) {
                    onFriday("dua", it)
                }
                CheckLine("الصلاة على النبي ﷺ (متابعة اختيارية)", log.friday["salawat"] == true) {
                    onFriday("salawat", it)
                }
                Text("إنجاز الجمعة: ${scores.friday ?: 0}% • 50% للكهف و50% للدعاء",
                    color = Purple, fontWeight = FontWeight.Bold)
            }
        }

        SectionCard("المتابعة الدورية", "بنود أسبوعية وشهرية مستقلة عن نسبة اليوم") {
            if (isFriday) {
                CheckLine("تحديث مواقيت الصلاة لهذا الأسبوع", log.friday["prayer_times"] == true) {
                    onFriday("prayer_times", it)
                }
            }
            CheckLine("الصدقة الشهرية (سجّلها يوم تنفيذها)", log.friday["charity"] == true) {
                onFriday("charity", it)
            }
        }

        SectionCard("زيارة الوالدين", "تسجيل مستقل لا يدخل في تقييم العبادات") {
            CheckLine("تمت زيارة الوالدين", log.parentsVisited, onParents)
        }

        Text("البيانات محفوظة على جوالك، ويمكن تصدير نسخة احتياطية من الإعدادات.",
            color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
    }
}

@Composable
private fun StatusLine(
    title: String, value: String, options: List<String>,
    onChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = value == option,
                    onClick = { onChange(if (value == option) "" else option) },
                    label = { Text(option, fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
private fun CountLine(title: String, count: Int, target: Int, onChange: (Int) -> Unit) {
    val current = count.coerceIn(0, target)
    val finished = current == target
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (finished) LightGreen else Page)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f))
                Text("${current} / ${target}", fontWeight = FontWeight.Bold, color = Green,
                    fontSize = 14.sp)
            }
            LinearProgressIndicator(
                progress = { current.toFloat() / target },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Green, trackColor = Color.White
            )
            if (finished) {
                Text("✓ اكتمل الذكر", color = Green, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(-1, 1, 10, target).forEach { amount ->
                    OutlinedButton(
                        onClick = { onChange((current + amount).coerceIn(0, target)) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        Text(if (amount > 0) "+" + amount else "−1", fontSize = 12.sp)
                    }
                }
                TextButton(onClick = { onChange(0) }) {
                    Text("تصفير", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String, value: Int, symbol: String, bg: Color, fg: Color,
    modifier: Modifier = Modifier
) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("$symbol  $title", fontWeight = FontWeight.SemiBold, color = fg, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text("$value%", fontWeight = FontWeight.ExtraBold, color = fg, fontSize = 25.sp)
        }
    }
}

@Composable
private fun ScoreCircle(value: Int) {
    Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier.fillMaxSize(), color = Green,
            trackColor = LightGreen, strokeWidth = 9.dp, strokeCap = StrokeCap.Round
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value%", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Text("إنجاز", fontSize = 11.sp, color = Muted)
        }
    }
}

@Composable
private fun SectionCard(
    title: String, subtitle: String, background: Color = Color.White,
    content: @Composable () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = background),
        shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = Muted)
            content()
        }
    }
}

@Composable
private fun CheckLine(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(title, fontSize = 14.sp)
    }
}

@Composable
private fun PrayerEditor(
    name: String, initial: PrayerLog, onDismiss: () -> Unit,
    onSave: (PrayerLog) -> Unit
) {
    var draft by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("وش حالة الصلاة؟", fontSize = 14.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        PrayerStatus.PRAYED to "صليتها",
                        PrayerStatus.MISSED to "فاتتني",
                        PrayerStatus.NOT_RECORDED to "لم أسجّل"
                    ).forEach { (status, label) ->
                        FilterChip(
                            selected = draft.status == status,
                            onClick = { draft = draft.copy(status = status) },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
                if (draft.status == PrayerStatus.PRAYED) {
                    HorizontalDivider()
                    ToggleLine("صليتها في وقتها", draft.onTime) {
                        draft = draft.copy(onTime = it)
                    }
                    ToggleLine("صليتها في المسجد", draft.inMosque) {
                        draft = draft.copy(inMosque = it,
                            inCongregation = if (it) draft.inCongregation else false,
                            openingTakbir = if (it) draft.openingTakbir else false)
                    }
                    ToggleLine("صليتها جماعة في المسجد", draft.inCongregation,
                        enabled = draft.inMosque) {
                        draft = draft.copy(inCongregation = it,
                            openingTakbir = if (it) draft.openingTakbir else false)
                    }
                    ToggleLine("أدركت تكبيرة الإحرام مع الإمام", draft.openingTakbir,
                        enabled = draft.inMosque && draft.inCongregation) {
                        draft = draft.copy(openingTakbir = it)
                    }
                    Text("درجة هذه الصلاة: ${ScoreEngine.prayerScore(draft)} من 100",
                        color = Green, fontWeight = FontWeight.Bold)
                }
                Text("الدرجة للتنظيم والمتابعة فقط، وليست تقييمًا لثواب الصلاة.",
                    fontSize = 11.sp, color = Muted)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(draft) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun ToggleLine(title: String, checked: Boolean, enabled: Boolean = true,
    onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontSize = 13.sp, modifier = Modifier.weight(1f),
            color = if (enabled) DarkText else Muted)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun MonthSelector(month: YearMonth, onChange: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(month.minusMonths(1)) }) { Text("‹ السابق") }
        Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", ArabicLocale)),
            fontWeight = FontWeight.Bold, fontSize = 18.sp)
        TextButton(onClick = { onChange(month.plusMonths(1)) }) { Text("التالي ›") }
    }
}

@Composable
private fun CalendarScreen(
    month: YearMonth, logs: Map<String, DayLog>, onMonth: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit, modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("تقويم الإنجاز", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MonthSelector(month, onMonth)
                val labels = listOf("السبت", "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة")
                Row(Modifier.fillMaxWidth()) {
                    labels.forEach { label ->
                        Text(label.take(3), modifier = Modifier.weight(1f), fontSize = 10.sp,
                            color = Muted, textAlign = TextAlign.Center)
                    }
                }
                val offset = (month.atDay(1).dayOfWeek.value + 1) % 7
                val cellCount = ((offset + month.lengthOfMonth() + 6) / 7) * 7
                for (week in 0 until cellCount / 7) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (dayOfWeek in 0..6) {
                            val number = week * 7 + dayOfWeek - offset + 1
                            if (number in 1..month.lengthOfMonth()) {
                                val date = month.atDay(number)
                                val score = ScoreEngine.score(date, logs[date.toString()] ?: DayLog())
                                val color = when {
                                    !score.hasAnyData -> Color(0xFFF1F3F6)
                                    score.recordedPrayers < 5 -> LightBlue
                                    score.total >= 80 -> LightGreen
                                    score.total >= 50 -> LightOrange
                                    else -> Color(0xFFFFEAEA)
                                }
                                Column(
                                    Modifier.weight(1f).height(62.dp)
                                        .background(color, RoundedCornerShape(10.dp))
                                        .clickable { onSelectDate(date) }
                                        .padding(top = 7.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("$number", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(if (score.hasAnyData) "${score.total}%" else "—",
                                        fontSize = 10.sp, color = Muted)
                                }
                            } else {
                                Spacer(Modifier.weight(1f).height(62.dp))
                            }
                        }
                    }
                }
            }
        }
        SectionCard("قراءة التقويم", "اضغط على أي يوم لعرضه أو تعديل تسجيلاته") {
            Text("الأخضر: 80% فأكثر مع تسجيل الصلوات الخمس", color = Green, fontSize = 13.sp)
            Text("البرتقالي: من 50% إلى 79% مع اكتمال تسجيل الصلوات", color = Orange, fontSize = 13.sp)
            Text("الأزرق: تسجيل جزئي، والرمادي: لم يُسجّل شيء", color = Blue, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StatsScreen(
    month: YearMonth, logs: Map<String, DayLog>, onMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    val days = (1..month.lengthOfMonth()).map { day ->
        val date = month.atDay(day)
        date to ScoreEngine.score(date, logs[date.toString()] ?: DayLog())
    }
    val recorded = days.filter { it.second.hasAnyData }
    fun avg(values: List<Int>): Int = if (values.isEmpty()) 0 else values.average().roundToInt()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("الإحصائيات", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        MonthSelector(month, onMonth)
        SectionCard("ملخص الشهر", "المتوسطات للأيام التي أُدخلت فيها بيانات فقط") {
            Text("${recorded.size} يوم مسجّل من ${month.lengthOfMonth()} يوم",
                fontWeight = FontWeight.Bold, color = DarkText)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("المعدل", avg(recorded.map { it.second.total }), "◉",
                    LightGreen, Green, Modifier.weight(1f))
                MetricCard("الصلاة", avg(recorded.map { it.second.prayers }), "◈",
                    LightBlue, Blue, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("الأذكار", avg(recorded.map { it.second.adhkar }), "◉",
                    LightPurple, Purple, Modifier.weight(1f))
                MetricCard("القرآن", avg(recorded.map { it.second.quran }), "▤",
                    LightOrange, Orange, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("في الوقت", avg(recorded.map { it.second.onTime }), "◷",
                    LightBlue, Blue, Modifier.weight(1f))
                MetricCard("الجماعة", avg(recorded.map { it.second.congregation }), "◉",
                    LightGreen, Green, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("السنن", avg(recorded.map { it.second.sunnah }), "☾",
                    LightPurple, Purple, Modifier.weight(1f))
                MetricCard("تكبيرة الإحرام", avg(recorded.map { it.second.openingTakbir }), "✧",
                    LightOrange, Orange, Modifier.weight(1f))
            }
            Text("ملاحظة: الأيام ذات التسجيل الجزئي قد تُظهر نسبة أقل من الأداء الفعلي.",
                fontSize = 12.sp, color = Muted)
        }
        SectionCard("أداء الأيام", "نسبة الإنجاز حسب البيانات المسجلة لكل يوم") {
            if (recorded.isEmpty()) {
                Text("ما فيه تسجيلات لهذا الشهر حتى الآن", color = Muted)
            } else {
                recorded.forEach { (date, score) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("${date.dayOfMonth}", modifier = Modifier.width(28.dp), fontSize = 12.sp)
                        LinearProgressIndicator(
                            progress = { score.total / 100f },
                            modifier = Modifier.weight(1f).height(8.dp),
                            color = if (score.recordedPrayers == 5) Green else Blue,
                            trackColor = Color(0xFFEDF0F4), strokeCap = StrokeCap.Round
                        )
                        Text("${score.total}%", modifier = Modifier.width(42.dp), fontSize = 12.sp,
                            textAlign = TextAlign.End)
                    }
                }
            }
        }
        val fridayScores = recorded.mapNotNull { it.second.friday }
        if (fridayScores.isNotEmpty()) {
            SectionCard("أعمال الجمعة", "متوسط أيام الجمعة المسجلة") {
                Text("${avg(fridayScores)}%", fontSize = 24.sp, color = Purple,
                    fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun SettingsScreen(onExport: () -> Unit, onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("الإعدادات", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        SectionCard("حفظ بياناتك", "كل التسجيلات محفوظة محليًا على جهازك") {
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text("تصدير نسخة احتياطية")
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text("استرجاع نسخة احتياطية")
            }
            Text("تنبيه: الاسترجاع يستبدل جميع البيانات الموجودة حاليًا في التطبيق.",
                fontSize = 12.sp, color = Orange)
        }
        SectionCard("طريقة حساب الإنجاز", "وفق نموذج الملف الديني في نوشن") {
            Text("40% لأداء الصلوات، 20% للصلاة في الوقت، 10% للجماعة")
            Text("10% للسنن والوتر، 10% للأذكار، 10% للقرآن")
            Text("تكبيرة الإحرام مؤشر مستقل، ودرجة جودة كل صلاة 50/30/20.")
            Text("تقييم الجمعة مستقل: 50% للكهف و50% للدعاء.",
                fontSize = 12.sp, color = Muted)
        }
        SectionCard("مهم", "حتى تكون الإحصائيات مفهومة") {
            Text("• «لم أسجّل» يختلف عن «فاتتني». عدم التسجيل لا يعني أن الصلاة فاتتك.")
            Text("• لا يوجد ربط تلقائي بتطبيق الأذكار أو القرآن في هذه النسخة.")
            Text("• لا تحتاج إنترنت لاستخدام التتبع الأساسي.")
            Text("• هذه درجات تنظيمية، ولا تقيس قبول العبادة أو ثوابها.")
        }
        Text("تتبّع • النسخة 0.2", fontSize = 12.sp, color = Muted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
