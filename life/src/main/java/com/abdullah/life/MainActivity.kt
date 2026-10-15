package com.abdullah.life

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter

private val android.content.Context.prefs by preferencesDataStore(name="life")
private val modeKey=stringPreferencesKey("mode")
private val seededKey=booleanPreferencesKey("seeded")
fun prettyTime(t:Long):String=Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
 .format(DateTimeFormatter.ofPattern("d MMM yyyy h:mm a",java.util.Locale("ar","SA")))
fun arabic(s:String):String=s.lowercase().replace(Regex("[\\u064B-\\u065F\\u0670]"),"")
 .replace(Regex("[أإآٱ]"),"ا").replace("ى","ي").replace("ة","ه").replace("ـ","")
fun toast(c:android.content.Context,s:String)=Toast.makeText(c,s,Toast.LENGTH_LONG).show()

class MainActivity:ComponentActivity() {
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  setContent {
   CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){
    MaterialTheme { LifeUI(LifeDatabase.get(this)) }
   }
  }
 }
}
data class Snapshot(
 val files:List<LifeFile>,val tasks:List<LifeTask>,val events:List<LifeEvent>,
 val activities:List<LifeActivity>,val projects:List<LifeProject>,
 val trackers:List<LifeTracker>,val entries:List<LifeTrackingEntry>,
 val cases:List<LifeCase>,val captures:List<LifeCapture>,
 val references:List<LifeReference>
)
data class FormData(val title:String,val note:String,val fileId:String?,val time:Long?,
 val important:Boolean,val critical:Boolean,val extra:String)

@Composable fun LifeUI(db:LifeDatabase){
 val context=LocalContext.current
 val d=db.dao()
 val scope=rememberCoroutineScope()
 val files by d.files().collectAsState(initial=emptyList())
 val tasks by d.tasks().collectAsState(initial=emptyList())
 val events by d.events().collectAsState(initial=emptyList())
 val activities by d.activities().collectAsState(initial=emptyList())
 val projects by d.projects().collectAsState(initial=emptyList())
 val trackers by d.trackers().collectAsState(initial=emptyList())
 val entries by d.entries().collectAsState(initial=emptyList())
 val cases by d.cases().collectAsState(initial=emptyList())
 val captures by d.captures().collectAsState(initial=emptyList())
 val references by d.references().collectAsState(initial=emptyList())
 val s=Snapshot(files,tasks,events,activities,projects,trackers,entries,cases,captures,references)
 val mode by context.prefs.data.map{it[modeKey]?:"طبيعي"}.collectAsState(initial="طبيعي")
 var route by remember{mutableStateOf("اليوم")}
 var detail by remember{mutableStateOf<Pair<String,String>?>(null)}
 var adding by remember{mutableStateOf<String?>(null)}
 var linkedFile by remember{mutableStateOf<String?>(null)}
 var linkedProject by remember{mutableStateOf<String?>(null)}
 var linkedCase by remember{mutableStateOf<String?>(null)}
 var captureDialog by remember{mutableStateOf(false)}
 var captureText by remember{mutableStateOf("")}
 var backupDialog by remember{mutableStateOf(false)}
 var restoration by remember{mutableStateOf<String?>(null)}
 var query by remember{mutableStateOf("")}
 val createBackup=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){
  uri->if(uri!=null)scope.launch{
   try{
    val content=LifeBackup.create(db,mode)
    context.contentResolver.openOutputStream(uri)?.use{it.write(content.toByteArray())}
     ?:error("تعذر فتح الملف")
    toast(context,"تم نسخ البيانات بنجاح")
   }catch(e:Exception){toast(context,"فشل النسخ: ${e.message}")}
  }
 }
 val importBackup=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){
  uri->if(uri!=null)scope.launch{
   try{
    val raw=context.contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}
     ?:error("لم أستطع قراءة الملف")
    LifeBackup.preview(raw);restoration=raw
   }catch(e:Exception){toast(context,"نسخة غير صالحة: ${e.message}")}
  }
 }
 LaunchedEffect(Unit){
  if(context.prefs.data.first()[seededKey]!=true){
   db.withTransaction{
    if(d.files().first().isEmpty()){
     listOf("الديني والروحاني","الأسرة والعلاقات","المدرسة والتعليم",
      "الإعلام والنشر","النادي والرياضة","المال والإدارة المالية",
      "الحياة الشخصية والصحة","الحضور الرقمي الشخصي")
      .forEach{d.putFile(LifeFile(title=it))}
    }
   }
   context.prefs.edit{it[seededKey]=true}
  }
 }
 val openAdd:(String,String?,String?,String?)->Unit={k,f,p,c->
  adding=k;linkedFile=f;linkedProject=p;linkedCase=c
 }
 val done:(String,String)->Unit={k,id->scope.launch{
  when(k){
   "مهمة"->tasks.find{it.id==id}?.let{d.finishTask(id,!it.completed)}
   "مشروع"->projects.find{it.id==id}?.let{d.finishProject(id,!it.completed)}
   "حالة"->cases.find{it.id==id}?.let{d.finishCase(id,!it.completed)}
  }
 }}
 val delete:(String,String)->Unit={k,id->scope.launch{
  try{
   when(k){
    "مهمة"->d.trashTask(id);"ملف"->d.trashFile(id)
    "موعد"->d.trashEvent(id);"نشاط"->d.trashActivity(id)
    "مشروع"->d.trashProject(id);"متتبّع"->d.trashTracker(id)
    "حالة"->d.trashCase(id);"التقاط"->d.trashCapture(id)
    "مرجع"->d.trashReference(id)
   }
   detail=null
  }catch(e:Exception){toast(context,"تعذر الحذف: ${e.message}")}
 }}
 val convert:(LifeCapture)->Unit={item->scope.launch{
  try{LifeBackup.convertCaptureToTask(db,item);toast(context,"أصبحت مهمة مرتبطة بسجل الالتقاط")}
  catch(e:Exception){toast(context,"لم يتم التحويل: ${e.message}")}
 }}
 val track:(String,Double,String)->Unit={id,value,note->scope.launch{
  try{d.putEntry(LifeTrackingEntry(trackerId=id,value=value,note=note))}
  catch(e:Exception){toast(context,"لم يتم الحفظ: ${e.message}")}
 }}
 Scaffold(bottomBar={
  NavigationBar{
   listOf("اليوم","المهام","الملفات","التقاط","المزيد").forEachIndexed{index,label->
    NavigationBarItem(selected=route==label,onClick={route=label;detail=null},
     icon={Text(listOf("◷","☑","▤","＋","☷")[index])},label={Text(label,maxLines=1)})
   }
  }
 },floatingActionButton={FloatingActionButton(onClick={captureDialog=true}){Text("+")}}){
  padding->
  Column(Modifier.padding(padding).fillMaxSize().padding(horizontal=12.dp)){
   Row(Modifier.fillMaxWidth().padding(vertical=8.dp),
    horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
    Column{
     Text("عبدالله | إدارة الحياة",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
     Text(if(detail!=null)"نقطة الاستمرار" else route,style=MaterialTheme.typography.labelMedium)
    }
    TextButton(onClick={route="البحث";detail=null}){Text("⌕ بحث")}
   }
   if(detail!=null){
    val key=detail!!
    DetailScreen(key.first,key.second,s,openAdd,
     onOpen={k,id->detail=k to id},onBack={detail=null},onDone=done,
     onDelete=delete,onTrack=track,onConvert=convert,
     onUpdate={k,id,st,stop,next->scope.launch{
      when(k){
       "ملف"->files.find{it.id==id}?.let{d.putFile(it.copy(currentState=st,lastStopPoint=stop,nextAction=next,updatedAt=clock()))}
       "مشروع"->projects.find{it.id==id}?.let{d.putProject(it.copy(currentState=st,lastStopPoint=stop,nextAction=next,updatedAt=clock()))}
       "حالة"->cases.find{it.id==id}?.let{d.putCase(it.copy(currentState=st,lastStopPoint=stop,nextAction=next,updatedAt=clock()))}
      }
      toast(context,"تم حفظ نقطة الاستمرار")
     }}
    )
   }else when(route){
    "اليوم"->TodayScreen(s,mode,
      onOpen={k,id->detail=k to id},
      onMode={m->scope.launch{context.prefs.edit{it[modeKey]=m}}})
    "المزيد"->MoreScreen({route=it},onBackup={backupDialog=true})
    "الإعدادات"->SettingsScreen(mode,
      onMode={m->scope.launch{context.prefs.edit{it[modeKey]=m}}},
      onBackup={backupDialog=true},onTrash={route="سلة المحذوفات"})
    "البحث"->SearchScreen(query,{query=it},s,{k,id->detail=k to id})
    else->ModuleScreen(route,s,
      onAdd={k->openAdd(k,null,null,null)},
      onOpen={k,id->detail=k to id},onDone=done,onDelete=delete,
      onTrack=track,onConvert=convert)
   }
  }
 }
 if(captureDialog)AlertDialog(onDismissRequest={captureDialog=false},
  title={Text("وش تبي تحفظ؟")},text={
   OutlinedTextField(captureText,{captureText=it},label={Text("فكرة، نص أو رابط")},
    modifier=Modifier.fillMaxWidth(),minLines=3)
  },confirmButton={TextButton(onClick={
   val raw=captureText.trim()
   if(raw.isNotBlank())scope.launch{d.putCapture(LifeCapture(content=raw))}
   captureText="";captureDialog=false
  },enabled=captureText.isNotBlank()){Text("حفظ فوري")}},
  dismissButton={TextButton(onClick={captureDialog=false}){Text("إلغاء")}})
 if(adding!=null)NewDialog(adding!!,files.filter{it.deletedAt==null},
  linkedFile,{adding=null}){f->
   val k=adding!!
   adding=null
   scope.launch{
    try{
     when(k){
      "مهمة"->d.putTask(LifeTask(title=f.title,notes=f.note,fileId=f.fileId,
       projectId=linkedProject,caseId=linkedCase,dueAt=f.time,
       important=f.important,critical=f.critical))
      "ملف"->d.putFile(LifeFile(title=f.title,currentState=f.note,nextAction=f.extra))
      "موعد"->d.putEvent(LifeEvent(title=f.title,notes=f.note,fileId=f.fileId,
       startAt=f.time?:clock()))
      "نشاط"->d.putActivity(LifeActivity(title=f.title,notes=f.note,fileId=f.fileId,
       recurrence=f.extra.ifBlank{"DAILY"},essential=f.critical))
      "مشروع"->d.putProject(LifeProject(title=f.title,outcome=f.note,
       nextAction=f.extra,fileId=f.fileId))
      "متتبّع"->d.putTracker(LifeTracker(title=f.title,unit=f.note,fileId=f.fileId,
       projectId=linkedProject))
      "حالة"->d.putCase(LifeCase(title=f.title,known=f.note,fileId=f.fileId,
       kind=f.extra.ifBlank{"مشكلة"}))
      "مرجع"->d.putReference(LifeReference(title=f.title,body=f.note,
       fileId=f.fileId,projectId=linkedProject,caseId=linkedCase))
     }
     toast(context,"تم الحفظ")
    }catch(e:Exception){toast(context,"تعذر الحفظ: ${e.message}")}
   }
  }
 if(backupDialog)AlertDialog(onDismissRequest={backupDialog=false},
  title={Text("النسخ الموحد")},
  text={Text("تُحفظ بيانات الوحدات وعلاقاتها مع بصمة تحقق، دون المرفقات الخارجية. الاستعادة تستبدل البيانات الحالية.")},
  confirmButton={Column{
   TextButton(onClick={backupDialog=false;createBackup.launch("abdullah-life-${LocalDate.now()}.json")}){Text("تصدير نسخة البيانات")}
   TextButton(onClick={backupDialog=false;importBackup.launch(arrayOf("application/json","text/plain"))}){Text("استعادة نسخة موحدة")}
  }},
  dismissButton={TextButton(onClick={backupDialog=false}){Text("إغلاق")}})
 if(restoration!=null){
  val info=runCatching{LifeBackup.preview(restoration!!)}.getOrNull()
  AlertDialog(onDismissRequest={restoration=null},
   title={Text("تأكيد الاستعادة")},
   text={Text("تاريخ النسخة: ${info?.createdAt?.let{prettyTime(it)}?:"—"}\nالعناصر: ${info?.itemCount?:0}\nالمرفقات: غير مضمنة\nهذه العملية ستستبدل البيانات الحالية. يمكن أخذ نسخة منها أولًا.")},
   confirmButton={TextButton(onClick={
    val raw=restoration!!;restoration=null
    scope.launch{
     try{LifeBackup.restore(db,raw);toast(context,"تمت الاستعادة")}
     catch(e:Exception){toast(context,"لم تُغيّر البيانات: ${e.message}")}
    }
   }){Text("استعادة")}},
   dismissButton={TextButton(onClick={restoration=null}){Text("إلغاء")}})
 }
}

@Composable fun NewDialog(kind:String,files:List<LifeFile>,initialFile:String?,
 onDismiss:()->Unit,onSave:(FormData)->Unit){
 val ctx=LocalContext.current
 var title by remember(kind){mutableStateOf("")}
 var note by remember(kind){mutableStateOf("")}
 var extra by remember(kind){mutableStateOf("")}
 var fileId by remember(kind){mutableStateOf(initialFile)}
 var time by remember(kind){mutableStateOf<Long?>(null)}
 var important by remember(kind){mutableStateOf(false)}
 var critical by remember(kind){mutableStateOf(false)}
 AlertDialog(onDismissRequest=onDismiss,title={Text("إضافة $kind")},
  text={Column(Modifier.fillMaxWidth().heightIn(max=500.dp).verticalScroll(rememberScrollState()),
   verticalArrangement=Arrangement.spacedBy(8.dp)){
   OutlinedTextField(title,{title=it},label={Text("العنوان")},modifier=Modifier.fillMaxWidth(),singleLine=true)
   if(kind!="ملف"&&files.isNotEmpty()){
    Text("ضمن ملف")
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){
     FilterChip(fileId==null,{fileId=null},label={Text("بدون")})
     files.forEach{f->FilterChip(fileId==f.id,{fileId=f.id},label={Text(f.title)})}
    }
   }
   OutlinedTextField(note,{note=it},label={Text(when(kind){
    "ملف"->"الحالة الحالية";"مشروع"->"النتيجة المطلوبة";"حالة"->"ما نعرفه";
    "متتبّع"->"وحدة القياس";else->"التفاصيل الاختيارية"
   })},modifier=Modifier.fillMaxWidth(),minLines=2)
   if(kind in listOf("ملف","مشروع"))
    OutlinedTextField(extra,{extra=it},label={Text("الخطوة التالية")},modifier=Modifier.fillMaxWidth())
   if(kind=="نشاط"){
    Row{listOf("DAILY" to "يومي","WEEKLY" to "أسبوعي").forEach{(v,label)->
     FilterChip(extra==v ||(extra==""&&v=="DAILY"),{extra=v},label={Text(label)})
    }}
    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(critical,{critical=it});Text("نشاط أساسي")}
   }
   if(kind=="حالة"){
    Row{listOf("مشكلة","قرار","أزمة").forEach{v->
     FilterChip(extra==v ||(extra==""&&v=="مشكلة"),{extra=v},label={Text(v)})
    }}
   }
   if(kind=="موعد"||kind=="مهمة"){
    TextButton(onClick={
     val n=LocalDateTime.now()
     DatePickerDialog(ctx,{_,year,month,day->
      TimePickerDialog(ctx,{_,hour,minute->
       time=LocalDateTime.of(year,month+1,day,hour,minute)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
      },n.hour,n.minute,false).show()
     },n.year,n.monthValue-1,n.dayOfMonth).show()
    }){Text("التوقيت: ${time?.let{prettyTime(it)}?:"غير محدد"}")}
   }
   if(kind=="مهمة"){
    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(important,{important=it});Text("مهمة")}
    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(critical,{critical=it});Text("حرجة في الطوارئ")}
   }
  }},
  confirmButton={TextButton(enabled=title.isNotBlank()&&(kind!="موعد"||time!=null),
   onClick={onSave(FormData(title.trim(),note.trim(),fileId,time,important,critical,extra))}){Text("حفظ")}},
  dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}
