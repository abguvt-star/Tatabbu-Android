package com.abdullah.life

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

fun freshId(): String = UUID.randomUUID().toString()
fun clock(): Long = System.currentTimeMillis()

@Entity(tableName="life_files", indices=[Index("title")])
data class LifeFile(
    @PrimaryKey val id:String=freshId(), val title:String, val currentState:String="",
    val lastStopPoint:String="", val nextAction:String="", val horizon:String="",
    val updatedAt:Long=clock(), val archived:Boolean=false, val deletedAt:Long?=null
)
@Entity(tableName="life_tasks", indices=[Index("fileId"),Index("projectId"),Index("caseId"),Index("dueAt")])
data class LifeTask(
    @PrimaryKey val id:String=freshId(),val title:String,val notes:String="",
    val fileId:String?=null,val projectId:String?=null,val caseId:String?=null,
    val dueAt:Long?=null,val important:Boolean=false,val critical:Boolean=false,
    val completed:Boolean=false,val updatedAt:Long=clock(),
    val archived:Boolean=false,val deletedAt:Long?=null
)
@Entity(tableName="life_events", indices=[Index("fileId"),Index("startAt")])
data class LifeEvent(
    @PrimaryKey val id:String=freshId(),val title:String,val notes:String="",
    val startAt:Long,val endAt:Long?=null,val fileId:String?=null,
    val updatedAt:Long=clock(),val archived:Boolean=false,val deletedAt:Long?=null
)
@Entity(tableName="life_activities", indices=[Index("fileId")])
data class LifeActivity(
    @PrimaryKey val id:String=freshId(),val title:String,val notes:String="",
    val fileId:String?=null,val recurrence:String="DAILY", val timeMinutes:Int?=null,
    val minimumMode:String="",val essential:Boolean=false,
    val updatedAt:Long=clock(),val archived:Boolean=false,val deletedAt:Long?=null
)
@Entity(tableName="life_projects",indices=[Index("fileId")])
data class LifeProject(
    @PrimaryKey val id:String=freshId(),val title:String,val outcome:String="",
    val definitionDone:String="",val currentState:String="",
    val lastStopPoint:String="",val nextAction:String="",val fileId:String?=null,
    val completed:Boolean=false,val updatedAt:Long=clock(),
    val archived:Boolean=false,val deletedAt:Long?=null
)
@Entity(tableName="life_trackers",indices=[Index("fileId"),Index("projectId"),Index("activityId")])
data class LifeTracker(
    @PrimaryKey val id:String=freshId(),val title:String,val unit:String="",
    val fileId:String?=null,val projectId:String?=null,val activityId:String?=null,
    val updatedAt:Long=clock(),val archived:Boolean=false,val deletedAt:Long?=null
)
@Entity(tableName="life_tracking_entries", indices=[Index("trackerId"),Index("recordedAt")],
    foreignKeys=[ForeignKey(entity=LifeTracker::class,parentColumns=["id"],childColumns=["trackerId"],onDelete=ForeignKey.RESTRICT)])
data class LifeTrackingEntry(
    @PrimaryKey val id:String=freshId(),val trackerId:String,val value:Double,
    val note:String="",val recordedAt:Long=clock(),val deletedAt:Long?=null
)
@Entity(tableName="life_cases",indices=[Index("fileId")])
data class LifeCase(
    @PrimaryKey val id:String=freshId(),val title:String,val kind:String="مشكلة",
    val known:String="",val unknown:String="",val currentState:String="",
    val lastStopPoint:String="",val nextAction:String="",val fileId:String?=null,
    val completed:Boolean=false,val updatedAt:Long=clock(),
    val archived:Boolean=false,val deletedAt:Long?=null
)
@Entity(tableName="life_captures")
data class LifeCapture(
    @PrimaryKey val id:String=freshId(),val content:String,val kind:String="نص",
    val uri:String?=null,val createdAt:Long=clock(),val processedAt:Long?=null,
    val destinationTaskId:String?=null,val deletedAt:Long?=null
)
@Entity(tableName="life_references", indices=[Index("fileId"),Index("projectId"),Index("caseId")])
data class LifeReference(
    @PrimaryKey val id:String=freshId(),val title:String,val body:String="",
    val fileId:String?=null,val projectId:String?=null,val caseId:String?=null,
    val updatedAt:Long=clock(),val archived:Boolean=false,val deletedAt:Long?=null
)
@Entity(tableName="life_unresolved")
data class UnresolvedReference(
    @PrimaryKey val id:String=freshId(),val sourceType:String,val sourceId:String,
    val targetType:String,val targetId:String,val foundAt:Long=clock()
)
@Entity(tableName="life_changes")
data class LifeChange(@PrimaryKey val id:String=freshId(),val what:String,val entityId:String,val at:Long=clock())

@Dao interface LifeDao {
 @Query("SELECT * FROM life_files ORDER BY updatedAt DESC") fun files():Flow<List<LifeFile>>
 @Query("SELECT * FROM life_tasks ORDER BY updatedAt DESC") fun tasks():Flow<List<LifeTask>>
 @Query("SELECT * FROM life_events ORDER BY startAt ASC") fun events():Flow<List<LifeEvent>>
 @Query("SELECT * FROM life_activities ORDER BY updatedAt DESC") fun activities():Flow<List<LifeActivity>>
 @Query("SELECT * FROM life_projects ORDER BY updatedAt DESC") fun projects():Flow<List<LifeProject>>
 @Query("SELECT * FROM life_trackers ORDER BY updatedAt DESC") fun trackers():Flow<List<LifeTracker>>
 @Query("SELECT * FROM life_tracking_entries ORDER BY recordedAt DESC") fun entries():Flow<List<LifeTrackingEntry>>
 @Query("SELECT * FROM life_cases ORDER BY updatedAt DESC") fun cases():Flow<List<LifeCase>>
 @Query("SELECT * FROM life_captures ORDER BY createdAt DESC") fun captures():Flow<List<LifeCapture>>
 @Query("SELECT * FROM life_references ORDER BY updatedAt DESC") fun references():Flow<List<LifeReference>>
 @Query("SELECT * FROM life_unresolved") fun unresolved():Flow<List<UnresolvedReference>>
 @Query("SELECT * FROM life_changes ORDER BY at DESC LIMIT 100") fun changes():Flow<List<LifeChange>>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putFile(x:LifeFile)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putTask(x:LifeTask)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putEvent(x:LifeEvent)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putActivity(x:LifeActivity)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putProject(x:LifeProject)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putTracker(x:LifeTracker)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putEntry(x:LifeTrackingEntry)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putCase(x:LifeCase)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putCapture(x:LifeCapture)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putReference(x:LifeReference)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putUnresolved(x:UnresolvedReference)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putChange(x:LifeChange)
 @Query("UPDATE life_tasks SET completed=:done,updatedAt=:time WHERE id=:id") suspend fun finishTask(id:String,done:Boolean,time:Long=clock())
 @Query("UPDATE life_projects SET completed=:done,updatedAt=:time WHERE id=:id") suspend fun finishProject(id:String,done:Boolean,time:Long=clock())
 @Query("UPDATE life_cases SET completed=:done,updatedAt=:time WHERE id=:id") suspend fun finishCase(id:String,done:Boolean,time:Long=clock())
 @Query("UPDATE life_captures SET processedAt=:time,destinationTaskId=:taskId WHERE id=:id AND processedAt IS NULL") suspend fun processCapture(id:String,taskId:String,time:Long=clock()):Int
 @Query("UPDATE life_tasks SET deletedAt=:time WHERE id=:id") suspend fun trashTask(id:String,time:Long=clock())
 @Query("UPDATE life_files SET deletedAt=:time WHERE id=:id") suspend fun trashFile(id:String,time:Long=clock())
 @Query("UPDATE life_events SET deletedAt=:time WHERE id=:id") suspend fun trashEvent(id:String,time:Long=clock())
 @Query("UPDATE life_activities SET deletedAt=:time WHERE id=:id") suspend fun trashActivity(id:String,time:Long=clock())
 @Query("UPDATE life_projects SET deletedAt=:time WHERE id=:id") suspend fun trashProject(id:String,time:Long=clock())
 @Query("UPDATE life_trackers SET deletedAt=:time WHERE id=:id") suspend fun trashTracker(id:String,time:Long=clock())
 @Query("UPDATE life_tracking_entries SET deletedAt=:time WHERE id=:id") suspend fun trashEntry(id:String,time:Long=clock())
 @Query("UPDATE life_cases SET deletedAt=:time WHERE id=:id") suspend fun trashCase(id:String,time:Long=clock())
 @Query("UPDATE life_captures SET deletedAt=:time WHERE id=:id") suspend fun trashCapture(id:String,time:Long=clock())
 @Query("UPDATE life_references SET deletedAt=:time WHERE id=:id") suspend fun trashReference(id:String,time:Long=clock())
 @Query("DELETE FROM life_changes") suspend fun clearChanges()
 @Query("DELETE FROM life_unresolved") suspend fun clearUnresolved()
 @Query("DELETE FROM life_tracking_entries") suspend fun clearEntries()
 @Query("DELETE FROM life_trackers") suspend fun clearTrackers()
 @Query("DELETE FROM life_tasks") suspend fun clearTasks()
 @Query("DELETE FROM life_events") suspend fun clearEvents()
 @Query("DELETE FROM life_activities") suspend fun clearActivities()
 @Query("DELETE FROM life_projects") suspend fun clearProjects()
 @Query("DELETE FROM life_cases") suspend fun clearCases()
 @Query("DELETE FROM life_captures") suspend fun clearCaptures()
 @Query("DELETE FROM life_references") suspend fun clearReferences()
 @Query("DELETE FROM life_files") suspend fun clearFiles()
}
@Database(entities=[LifeFile::class,LifeTask::class,LifeEvent::class,LifeActivity::class,
 LifeProject::class,LifeTracker::class,LifeTrackingEntry::class,LifeCase::class,
 LifeCapture::class,LifeReference::class,UnresolvedReference::class,LifeChange::class],
 version=1,exportSchema=false)
abstract class LifeDatabase:RoomDatabase(){
 abstract fun dao():LifeDao
 companion object {
  @Volatile private var singleton:LifeDatabase?=null
  fun get(context:Context):LifeDatabase=singleton ?: synchronized(this){
   singleton ?: Room.databaseBuilder(context.applicationContext,LifeDatabase::class.java,
     "abdullah-life.db").build().also {singleton=it}
  }
 }
}
