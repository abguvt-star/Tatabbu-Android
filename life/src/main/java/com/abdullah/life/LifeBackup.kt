package com.abdullah.life

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

/**
 * Native JSON backup. It contains actual entity values, not a renamed database URI.
 * It deliberately does not pretend to include external Android content URIs as attachments.
 */
object LifeBackup {
 private val gson=Gson()
 private fun digest(payload:String):String {
  val md=MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
  return md.joinToString("") { "%02x".format(it) }
 }
 suspend fun create(db:LifeDatabase, mode:String):String {
  val d=db.dao()
  val data=JsonObject()
  data.add("files",gson.toJsonTree(d.files().first()))
  data.add("tasks",gson.toJsonTree(d.tasks().first()))
  data.add("events",gson.toJsonTree(d.events().first()))
  data.add("activities",gson.toJsonTree(d.activities().first()))
  data.add("projects",gson.toJsonTree(d.projects().first()))
  data.add("trackers",gson.toJsonTree(d.trackers().first()))
  data.add("entries",gson.toJsonTree(d.entries().first()))
  data.add("cases",gson.toJsonTree(d.cases().first()))
  data.add("captures",gson.toJsonTree(d.captures().first()))
  data.add("references",gson.toJsonTree(d.references().first()))
  data.add("unresolved",gson.toJsonTree(d.unresolved().first()))
  val root=JsonObject()
  root.addProperty("app","abdullah-life")
  root.addProperty("backupVersion",1)
  root.addProperty("schemaVersion",1)
  root.addProperty("createdAt",clock())
  root.addProperty("attachmentsIncluded",false)
  root.addProperty("mode",mode)
  root.add("data",data)
  root.addProperty("checksumSha256",digest(data.toString()))
  return gson.toJson(root)
 }
 data class Preview(val createdAt:Long,val itemCount:Int,val attachments:Boolean,val mode:String)
 private fun verified(raw:String):JsonObject{
  val root=JsonParser.parseString(raw).asJsonObject
  require(root.get("app").asString=="abdullah-life") { "هذه ليست نسخة من تطبيق إدارة الحياة" }
  require(root.get("backupVersion").asInt==1&&root.get("schemaVersion").asInt==1) { "إصدار النسخة غير مدعوم" }
  val data=root.getAsJsonObject("data")
  require(root.get("checksumSha256").asString==digest(data.toString())) { "النسخة تالفة أو تغيّرت محتوياتها" }
  return root
 }
 fun preview(raw:String):Preview {
  val root=verified(raw);val data=root.getAsJsonObject("data")
  var count=0
  for(name in listOf("files","tasks","events","activities","projects","trackers","entries","cases","captures","references")){
   count+=data.getAsJsonArray(name).size()
  }
  return Preview(root.get("createdAt").asLong,count,root.get("attachmentsIncluded").asBoolean,
      root.get("mode").asString)
 }
 private inline fun <reified T> load(data:JsonObject,key:String):List<T> {
  val type=object:TypeToken<List<T>>(){}.type
  return gson.fromJson(data.getAsJsonArray(key),type)
 }
 private fun <T> unique(list:List<T>,id:(T)->String):Boolean=list.size==list.map(id).toSet().size

 /** All modifications are one SQLite transaction; failures never leave half a restore. */
 suspend fun restore(db:LifeDatabase,raw:String):Preview {
  val root=verified(raw);val data=root.getAsJsonObject("data")
  val files=load<LifeFile>(data,"files")
  val tasks=load<LifeTask>(data,"tasks")
  val events=load<LifeEvent>(data,"events")
  val activities=load<LifeActivity>(data,"activities")
  val projects=load<LifeProject>(data,"projects")
  val trackers=load<LifeTracker>(data,"trackers")
  val entries=load<LifeTrackingEntry>(data,"entries")
  val cases=load<LifeCase>(data,"cases")
  val captures=load<LifeCapture>(data,"captures")
  val references=load<LifeReference>(data,"references")
  val unresolved=load<UnresolvedReference>(data,"unresolved")
  require(unique(files){it.id}&&unique(tasks){it.id}&&unique(events){it.id}&&
    unique(activities){it.id}&&unique(projects){it.id}&&unique(trackers){it.id}&&
    unique(entries){it.id}&&unique(cases){it.id}&&unique(captures){it.id}&&
    unique(references){it.id}) { "يوجد UUID مكرر ضمن النسخة" }
  val trackerIds=trackers.map{it.id}.toSet()
  require(entries.all{it.trackerId in trackerIds}) { "بعض سجلات التتبع ليس لها متتبّع" }
  val filesIds=files.map{it.id}.toSet()
  val projectIds=projects.map{it.id}.toSet()
  val caseIds=cases.map{it.id}.toSet()
  val activityIds=activities.map{it.id}.toSet()
  val missing=mutableListOf<UnresolvedReference>()
  fun check(type:String,id:String,targetType:String,target:String?,known:Set<String>) {
   if(target!=null&&target !in known) missing.add(UnresolvedReference(sourceType=type,
    sourceId=id,targetType=targetType,targetId=target))
  }
  tasks.forEach {
   check("task",it.id,"file",it.fileId,filesIds)
   check("task",it.id,"project",it.projectId,projectIds)
   check("task",it.id,"case",it.caseId,caseIds)
  }
  events.forEach{check("event",it.id,"file",it.fileId,filesIds)}
  activities.forEach{check("activity",it.id,"file",it.fileId,filesIds)}
  projects.forEach{check("project",it.id,"file",it.fileId,filesIds)}
  cases.forEach{check("case",it.id,"file",it.fileId,filesIds)}
  trackers.forEach {
   check("tracker",it.id,"file",it.fileId,filesIds)
   check("tracker",it.id,"project",it.projectId,projectIds)
   check("tracker",it.id,"activity",it.activityId,activityIds)
  }
  references.forEach {
   check("reference",it.id,"file",it.fileId,filesIds)
   check("reference",it.id,"project",it.projectId,projectIds)
   check("reference",it.id,"case",it.caseId,caseIds)
  }
  val d=db.dao()
  db.withTransaction {
   d.clearEntries();d.clearUnresolved();d.clearChanges()
   d.clearTasks();d.clearEvents();d.clearActivities();d.clearReferences()
   d.clearCaptures();d.clearTrackers();d.clearProjects();d.clearCases();d.clearFiles()
   files.forEach{d.putFile(it)}
   projects.forEach{d.putProject(it)}
   cases.forEach{d.putCase(it)}
   trackers.forEach{d.putTracker(it)}
   activities.forEach{d.putActivity(it)}
   tasks.forEach{d.putTask(it)}
   events.forEach{d.putEvent(it)}
   entries.forEach{d.putEntry(it)}
   captures.forEach{d.putCapture(it)}
   references.forEach{d.putReference(it)}
   (unresolved+missing).distinctBy{it.sourceType+it.sourceId+it.targetType+it.targetId}
     .forEach{d.putUnresolved(it)}
   d.putChange(LifeChange(what="استعادة نسخة موحدة",entityId="backup"))
  }
  return preview(raw)
 }
 suspend fun convertCaptureToTask(db:LifeDatabase,capture:LifeCapture,fileId:String?=null):String {
  require(capture.processedAt==null){"سبق معالجة الالتقاط"}
  val id=freshId();val d=db.dao()
  db.withTransaction {
   d.putTask(LifeTask(id=id,title=capture.content.take(250),notes=capture.content,fileId=fileId))
   check(d.processCapture(capture.id,id)==1){"تغيرت حالة الالتقاط"}
   d.putChange(LifeChange(what="تحويل التقاط إلى مهمة",entityId=id))
  }
  return id
 }
}
