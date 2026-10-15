package com.abdullah.life

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class RecordRow(val kind:String,val id:String,val title:String,val description:String="",
 val completed:Boolean=false,val archived:Boolean=false,val deleted:Boolean=false)

fun Snapshot.rows():List<RecordRow> {
 val all=mutableListOf<RecordRow>()
 files.forEach{all+=RecordRow("ملف",it.id,it.title,it.currentState,archived=it.archived,deleted=it.deletedAt!=null)}
 tasks.forEach{all+=RecordRow("مهمة",it.id,it.title,it.notes,it.completed,it.archived,it.deletedAt!=null)}
 events.forEach{all+=RecordRow("موعد",it.id,it.title,prettyTime(it.startAt),archived=it.archived,deleted=it.deletedAt!=null)}
 activities.forEach{all+=RecordRow("نشاط",it.id,it.title,it.notes,archived=it.archived,deleted=it.deletedAt!=null)}
 projects.forEach{all+=RecordRow("مشروع",it.id,it.title,it.outcome,it.completed,it.archived,it.deletedAt!=null)}
 trackers.forEach{all+=RecordRow("متتبّع",it.id,it.title,it.unit,archived=it.archived,deleted=it.deletedAt!=null)}
 cases.forEach{all+=RecordRow("حالة",it.id,it.title,it.kind,it.completed,it.archived,it.deletedAt!=null)}
 captures.forEach{all+=RecordRow("التقاط",it.id,it.content.take(90),it.kind,
  completed=it.processedAt!=null,deleted=it.deletedAt!=null)}
 references.forEach{all+=RecordRow("مرجع",it.id,it.title,it.body,archived=it.archived,deleted=it.deletedAt!=null)}
 return all
}
val modes=listOf("طبيعي","مخفف","طوارئ","إجازة")
@Composable fun Modes(current:String, onMode:(String)->Unit) {
 Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){
  modes.forEach{m->
   FilterChip(selected=current==m,onClick={onMode(m)},label={Text(m)})
  }
 }
}
@Composable fun InfoHeader(title:String){
 Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,
  modifier=Modifier.padding(top=12.dp,bottom=6.dp))
}
@Composable fun RecordCard(row:RecordRow,onOpen:()->Unit,onDone:(()->Unit)?=null){
 Card(Modifier.fillMaxWidth().padding(vertical=3.dp).clickable(onClick=onOpen)) {
  Row(Modifier.padding(12.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
    Text(row.title,style=MaterialTheme.typography.bodyLarge,
     fontWeight=if(row.completed)FontWeight.Normal else FontWeight.Medium)
    if(row.description.isNotBlank())Text(row.description.take(140),
     style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(row.completed)Text("مكتمل / تمت معالجته",style=MaterialTheme.typography.labelSmall)
   }
   if(onDone!=null) Checkbox(checked=row.completed,onCheckedChange={onDone()})
  }
 }
}
@Composable fun EmptyScreen(text:String){
 Text(text,color=MaterialTheme.colorScheme.onSurfaceVariant,
  modifier=Modifier.fillMaxWidth().padding(16.dp))
}
@Composable fun TodayScreen(s:Snapshot,mode:String,
 onOpen:(String,String)->Unit,onMode:(String)->Unit){
 val now=System.currentTimeMillis()
 val local=LocalDate.now()
 val beginning=local.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
 val end=local.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
 val todayEvents=s.events.filter{it.deletedAt==null&&!it.archived&&it.startAt in beginning until end}
 val active=todayEvents.firstOrNull{it.startAt<=now&&it.endAt!=null&&it.endAt>now}
 val next=todayEvents.filter{it.startAt>now}.minByOrNull{it.startAt}
 val todo=s.tasks.filter{it.deletedAt==null&&!it.archived&&!it.completed&&
  (it.important||it.critical||(it.dueAt!=null&&it.dueAt in beginning until end))}
  .filter{if(mode=="طوارئ"||mode=="مخفف")it.critical else true}
 val activity=s.activities.filter{it.deletedAt==null&&!it.archived&&it.recurrence=="DAILY"}
  .filter{if(mode=="طوارئ"||mode=="مخفف")it.essential else true}
 val hijri=runCatching{DateTimeFormatter.ofPattern("d MMMM yyyy",Locale("ar","SA"))
  .format(HijrahDate.now())}.getOrDefault("التاريخ الهجري غير متاح")
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=88.dp)){
  item{
   Text(local.format(DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy",Locale("ar","SA"))),
    style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
   Text("هجري: $hijri",style=MaterialTheme.typography.bodyMedium)
   Modes(mode,onMode)
   InfoHeader("الآن")
   Text(active?.title ?: "لا يوجد شيء مرتبط بالوقت الآن.",
    modifier=Modifier.padding(vertical=7.dp))
   InfoHeader("التالي")
   if(next!=null)RecordCard(RecordRow("موعد",next.id,next.title,prettyTime(next.startAt)),
    {onOpen("موعد",next.id)})
   else EmptyScreen("لا يوجد موعد آخر مؤكد اليوم.")
   InfoHeader("مواعيد اليوم")
  }
  if(todayEvents.isEmpty())item{EmptyScreen("ما فيه مواعيد مسجلة لهذا اليوم.")}
  items(todayEvents,key={"event"+it.id}){e->
   RecordCard(RecordRow("موعد",e.id,e.title,prettyTime(e.startAt)),{onOpen("موعد",e.id)})
  }
  item{InfoHeader("المهام التي تحتاج انتباهًا")}
  if(todo.isEmpty())item{EmptyScreen("ما فيه مهمة محددة تتطلب تدخلك الآن.")}
  items(todo.take(8),key={"task"+it.id}){t->
   RecordCard(RecordRow("مهمة",t.id,t.title,
    t.dueAt?.let{prettyTime(it)}?:"مهمة مهمة"),{onOpen("مهمة",t.id)})
  }
  item{InfoHeader("الأنشطة المناسبة اليوم")}
  if(activity.isEmpty())item{EmptyScreen("لا توجد أنشطة أساسية معروضة في هذا الوضع.")}
  items(activity.take(6),key={"act"+it.id}){a->
   RecordCard(RecordRow("نشاط",a.id,a.title,a.notes),{onOpen("نشاط",a.id)})
  }
 }
}
@Composable fun MoreScreen(onNavigate:(String)->Unit,onBackup:()->Unit) {
 val nav=listOf("التقويم","الأنشطة","المشاريع","التتبع","القرارات والمشكلات",
  "المرجع","التقارير","البحث","الإعدادات")
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=90.dp)){
  item{InfoHeader("الوحدات")}
  items(nav){n->RecordCard(RecordRow("menu",n,n),{onNavigate(n)})}
  item{TextButton(onClick=onBackup){Text("النسخ الاحتياطي والاستعادة")}}
 }
}
@Composable fun SettingsScreen(mode:String,onMode:(String)->Unit,onBackup:()->Unit,onTrash:()->Unit){
 Column(Modifier.verticalScroll(rememberScrollState()).fillMaxSize().padding(bottom=80.dp)){
  InfoHeader("الوضع التشغيلي")
  Modes(mode,onMode)
  InfoHeader("البيانات")
  Button(onClick=onBackup){Text("نسخة احتياطية / استعادة")}
  Text("تُحفظ البيانات محليًا على هذا الهاتف ولا تحتاج إلى حساب أو إنترنت.",
   style=MaterialTheme.typography.bodySmall)
  TextButton(onClick=onTrash){Text("سلة المحذوفات")}
  InfoHeader("خصوصية")
  Text("لا تخزّن كلمات المرور أو رموز الاستعادة داخل السجلات العادية.",
   style=MaterialTheme.typography.bodySmall)
  InfoHeader("الإصدار")
  Text("0.1.1 • نسخة أولية وظيفية؛ الميزات غير المكتملة غير مدّعاة.")
 }
}
@Composable fun ModuleScreen(route:String,s:Snapshot,
 onAdd:(String)->Unit,onOpen:(String,String)->Unit,onDone:(String,String)->Unit,
 onDelete:(String,String)->Unit,onTrack:(String,Double,String)->Unit,onConvert:(LifeCapture)->Unit){
 val kind=when(route){
  "المهام"->"مهمة";"الملفات"->"ملف";"التقاط"->"التقاط";"التقويم"->"موعد"
  "الأنشطة"->"نشاط";"المشاريع"->"مشروع";"التتبع"->"متتبّع"
  "القرارات والمشكلات"->"حالة";"المرجع"->"مرجع";else->null
 }
 var showArchived by remember(route){mutableStateOf(false)}
 val records=s.rows()
 if(route=="التقارير"){
  val active=records.count{!it.deleted&&!it.archived}
  Column{
   InfoHeader("ملخص واقعي من البيانات")
   listOf("العناصر الحالية: $active","المهام المفتوحة: ${s.tasks.count{!it.completed&&it.deletedAt==null}}",
    "المشاريع المفتوحة: ${s.projects.count{!it.completed&&it.deletedAt==null}}",
    "الالتقاطات غير المعالجة: ${s.captures.count{it.processedAt==null&&it.deletedAt==null}}")
    .forEach{Text(it,modifier=Modifier.padding(8.dp))}
   Text("التقرير وصفي، ولا يعطي درجة لحياتك.")
  }
  return
 }
 if(route=="سلة المحذوفات"){
  LazyColumn {
   item{InfoHeader("عناصر محذوفة (محفوظة مؤقتًا)")}
   items(records.filter{it.deleted}){r->
    RecordCard(r,{onOpen(r.kind,r.id)})
   }
   item{EmptyScreen("اضغط على أي عنصر لاستعادته. الحذف النهائي غير مفعّل حفاظًا على بياناتك.")}
  }
  return
 }
 val chosen=records.filter{kind==null||it.kind==kind}
  .filter{!it.deleted&&(showArchived||!it.archived)}
  .filter{if(kind=="مهمة"&&!showArchived)!it.completed else true}
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=80.dp)){
  item{
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
    horizontalArrangement=Arrangement.SpaceBetween){
    Text(route,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
    if(kind!=null&&kind!="التقاط")
     Button(onClick={onAdd(kind)}){Text("+ إضافة")}
   }
   if(kind=="مهمة"||kind=="مشروع"){
    Row(verticalAlignment=Alignment.CenterVertically){
     Checkbox(showArchived,{showArchived=it});Text("إظهار المكتمل والمؤرشف")
    }
   }
  }
  if(chosen.isEmpty())item{EmptyScreen("لا توجد عناصر هنا حاليًا.")}
  items(chosen,key={it.id}){r->
   RecordCard(r,{onOpen(r.kind,r.id)},
    if(r.kind in listOf("مهمة","مشروع","حالة")){{onDone(r.kind,r.id)}} else null)
  }
 }
}
@Composable fun SearchScreen(text:String,onText:(String)->Unit,s:Snapshot,onOpen:(String,String)->Unit){
 val rows=s.rows().filter{!it.deleted}
 val result=if(text.isBlank())emptyList()else rows.filter{
  arabic(it.title+" "+it.description).contains(arabic(text.trim()))
 }
 Column(Modifier.fillMaxSize()){
  OutlinedTextField(text,onText,label={Text("ابحث في جميع الوحدات")},
   modifier=Modifier.fillMaxWidth(),singleLine=true)
  val order=listOf("مهمة","موعد","ملف","مشروع","حالة","مرجع","متتبّع","نشاط","التقاط")
  LazyColumn(contentPadding=PaddingValues(bottom=70.dp)){
   order.forEach{k->
    val group=result.filter{it.kind==k}
    if(group.isNotEmpty()){
     item{InfoHeader(k)}
     items(group,key={it.id}){r->RecordCard(r,{onOpen(r.kind,r.id)})}
    }
   }
  }
 }
}
@Composable fun DetailScreen(kind:String,id:String,s:Snapshot,
 onCreate:(String,String?,String?,String?)->Unit,onOpen:(String,String)->Unit,
 onBack:()->Unit,onDone:(String,String)->Unit,onDelete:(String,String)->Unit,
 onRestore:(String,String)->Unit,
 onTrack:(String,Double,String)->Unit,onConvert:(LifeCapture)->Unit,
 onUpdate:(String,String,String,String,String)->Unit){
 val record=s.rows().firstOrNull{it.kind==kind&&it.id==id}
 val file=s.files.find{it.id==id}
 val project=s.projects.find{it.id==id}
 val case=s.cases.find{it.id==id}
 val tracker=s.trackers.find{it.id==id}
 val capture=s.captures.find{it.id==id}
 var current by remember(id){mutableStateOf(file?.currentState?:project?.currentState?:case?.currentState?:"")}
 var stop by remember(id){mutableStateOf(file?.lastStopPoint?:project?.lastStopPoint?:case?.lastStopPoint?:"")}
 var next by remember(id){mutableStateOf(file?.nextAction?:project?.nextAction?:case?.nextAction?:"")}
 var valueText by remember(id){mutableStateOf("")}
 var entryNote by remember(id){mutableStateOf("")}
 var confirmDelete by remember(id){mutableStateOf(false)}
 if(record==null){Column{TextButton(onClick=onBack){Text("رجوع")};EmptyScreen("العنصر غير موجود")};return}
 if(record.deleted) {
  Column(Modifier.fillMaxWidth().padding(top=16.dp)) {
   TextButton(onClick=onBack){Text("‹ رجوع إلى سلة المحذوفات")}
   Text(record.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
   Text("هذا العنصر موجود في سلة المحذوفات، ولم تُحذف بياناته نهائيًا.")
   Button(onClick={onRestore(kind,id)},modifier=Modifier.padding(top=12.dp)){Text("استعادة العنصر")}
  }
  return
 }
 val linkedTasks=when(kind){
  "ملف"->s.tasks.filter{it.fileId==id}
  "مشروع"->s.tasks.filter{it.projectId==id}
  "حالة"->s.tasks.filter{it.caseId==id}
  else->emptyList()
 }.filter{it.deletedAt==null}
 val relatedProjects=if(kind=="ملف")s.projects.filter{it.fileId==id&&it.deletedAt==null}else emptyList()
 val relatedRefs=when(kind){
  "ملف"->s.references.filter{it.fileId==id}
  "مشروع"->s.references.filter{it.projectId==id}
  "حالة"->s.references.filter{it.caseId==id}
  else->emptyList()
 }.filter{it.deletedAt==null}
 val f=when(kind){"ملف"->id;"مشروع"->project?.fileId;"حالة"->case?.fileId;else->null}
 val p=if(kind=="مشروع")id else null
 val c=if(kind=="حالة")id else null
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=90.dp)){
  item{
   TextButton(onClick=onBack){Text("‹ رجوع")}
   Text(record.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
   if(record.description.isNotBlank())Text(record.description,Modifier.padding(vertical=6.dp))
   if(kind=="ملف"||kind=="مشروع"||kind=="حالة"){
    InfoHeader("العودة من آخر نقطة")
    OutlinedTextField(current,{current=it},label={Text("الحالة الحالية")},
     modifier=Modifier.fillMaxWidth())
    OutlinedTextField(stop,{stop=it},label={Text("وقفت هنا")},
     modifier=Modifier.fillMaxWidth())
    OutlinedTextField(next,{next=it},label={Text("الخطوة التالية")},
     modifier=Modifier.fillMaxWidth())
    Button(onClick={onUpdate(kind,id,current,stop,next)},modifier=Modifier.padding(top=8.dp)){
     Text("حفظ نقطة الاستمرار")
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     TextButton(onClick={onCreate("مهمة",f,p,c)}){Text("+ مهمة مرتبطة")}
     if(kind=="ملف")TextButton(onClick={onCreate("مشروع",id,null,null)}){Text("+ مشروع")}
     if(kind=="حالة")TextButton(onClick={onCreate("مرجع",f,null,id)}){Text("+ مرجع")}
    }
   }
   if(kind=="التقاط" && capture!=null){
    InfoHeader("صندوق الوارد")
    Text(capture.content)
    if(capture.processedAt==null)Button(onClick={onConvert(capture)}){Text("تحويل إلى مهمة حقيقية")}
    else Text("تمت المعالجة • المهمة: ${capture.destinationTaskId?: "—"}")
   }
   if(kind=="متتبّع" && tracker!=null){
    InfoHeader("إضافة قياس")
    OutlinedTextField(valueText,{valueText=it},label={Text("القيمة")},
     modifier=Modifier.fillMaxWidth())
    OutlinedTextField(entryNote,{entryNote=it},label={Text("ملاحظة اختيارية")},
     modifier=Modifier.fillMaxWidth())
    Button(enabled=valueText.toDoubleOrNull()!=null,onClick={
     valueText.toDoubleOrNull()?.let{onTrack(id,it,entryNote)}
     valueText="";entryNote=""
    }){Text("حفظ القياس")}
   }
   if(kind in listOf("مهمة","مشروع","حالة")){
    Button(onClick={onDone(kind,id)}){Text(if(record.completed)"إعادة فتح" else "مكتمل")}
   }
  }
  if(linkedTasks.isNotEmpty()){
   item{InfoHeader("المهام المرتبطة")}
   items(linkedTasks){t->RecordCard(RecordRow("مهمة",t.id,t.title,t.notes,t.completed),
    {onOpen("مهمة",t.id)}, {onDone("مهمة",t.id)})}
  }
  if(relatedProjects.isNotEmpty()){
   item{InfoHeader("مشاريع هذا الملف")}
   items(relatedProjects){x->RecordCard(RecordRow("مشروع",x.id,x.title,x.outcome,x.completed),
    {onOpen("مشروع",x.id)})}
  }
  if(relatedRefs.isNotEmpty()){
   item{InfoHeader("المراجع المرتبطة")}
   items(relatedRefs){x->RecordCard(RecordRow("مرجع",x.id,x.title,x.body),
    {onOpen("مرجع",x.id)})}
  }
  if(kind=="متتبّع"){
   val tracking=s.entries.filter{it.trackerId==id&&it.deletedAt==null}
   item{InfoHeader("السجل")}
   items(tracking){e->
    Card(Modifier.fillMaxWidth().padding(vertical=3.dp)) {
     Text("${e.value} • ${prettyTime(e.recordedAt)} ${e.note}",
      modifier=Modifier.padding(12.dp))
    }
   }
  }
  item{
   Spacer(Modifier.height(14.dp))
   TextButton(onClick={confirmDelete=true}){Text("نقل إلى سلة المحذوفات")}
  }
 }
 if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},
  title={Text("تأكيد الحذف")},
  text={Text(if(kind=="ملف"&&(linkedTasks.isNotEmpty()||relatedProjects.isNotEmpty()||relatedRefs.isNotEmpty()))
   "هذا الملف مرتبط بعناصر أخرى. لن نحذف بياناتها. حذف الملف قد يجعل الروابط بحاجة إلى مراجعة."
   else "سيُنقل العنصر إلى المحذوفات دون حذف نهائي.")},
  confirmButton={TextButton(onClick={confirmDelete=false;onDelete(kind,id)}){Text("نقل للمحذوفات")}},
  dismissButton={TextButton(onClick={confirmDelete=false}){Text("إلغاء")}})
}
