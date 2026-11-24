package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(
  @PrimaryKey val id: String,
  val title: String,
  val createdAtMs: Long,
  val updatedAtMs: Long,
)

@Entity(
  tableName = "messages",
  foreignKeys = [
    ForeignKey(
      entity = ConversationEntity::class,
      parentColumns = ["id"],
      childColumns = ["conversationId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [Index("conversationId")],
)
data class MessageEntity(
  @PrimaryKey val id: String,
  val conversationId: String,
  val isUser: Boolean,
  val text: String,
  val timestampMs: Long,
  val imageUrisJson: String,
  val isSampleArtwork: Boolean,
  val modelUsed: String?,
  val categoryLabel: String?,
  val promptTokens: Int?,
  val completionTokens: Int?,
  val totalTokens: Int?,
  val isError: Boolean,
  val blocksJson: String = "[]",
  val attachedMediaJson: String = "[]",
)

@Dao
interface ChatDao {
  @Query("SELECT * FROM conversations ORDER BY updatedAtMs DESC")
  fun observeConversations(): Flow<List<ConversationEntity>>

  @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
  suspend fun getConversationById(id: String): ConversationEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertConversation(entity: ConversationEntity)

  @Query("UPDATE conversations SET title = :title, updatedAtMs = :updatedAtMs WHERE id = :id")
  suspend fun updateConversationTitle(id: String, title: String, updatedAtMs: Long)

  @Query("DELETE FROM conversations WHERE id = :id")
  suspend fun deleteConversation(id: String)

  @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestampMs ASC")
  fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

  @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestampMs ASC")
  suspend fun getMessagesSnapshot(conversationId: String): List<MessageEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertMessage(entity: MessageEntity)

  @Query("DELETE FROM messages WHERE id = :messageId")
  suspend fun deleteMessage(messageId: String)

  @Query("DELETE FROM messages WHERE conversationId = :conversationId AND timestampMs >= :fromTimestampMs")
  suspend fun deleteMessagesFrom(conversationId: String, fromTimestampMs: Long)
}

@Database(
  entities = [
    ConversationEntity::class,
    MessageEntity::class,
    UserPreferenceEntity::class,
    MemoryItemEntity::class,
    MemorySummaryEntity::class,
    AgentTaskEntity::class,
    ResearchSourceEntity::class,
    MediaMetadataEntity::class,
    AutomationTaskEntity::class,
    ImageVersionEntity::class,
    TaskPlanEntity::class,
    TaskStepEntity::class,
    VoiceSessionEntity::class,
    DrawingAnnotationEntity::class,
    RichCardEntity::class,
    ReminderEntity::class,
    ManagedTaskEntity::class,
    DeviceActionLogEntity::class,
    ImageTaskEntity::class,
    ImageAssetEntity::class,
    ImageProviderStatusEntity::class,
    TodoNoteEntity::class,
  ],
  version = 7,
  exportSchema = false,
)
abstract class MousomiDatabase : RoomDatabase() {
  abstract fun chatDao(): ChatDao
  abstract fun memoryDao(): MemoryDao
  abstract fun part4OperationsDao(): Part4OperationsDao
  abstract fun part5Dao(): Part5Dao
  abstract fun addOnDao(): AddOnDao
  abstract fun imageStudioDao(): ImageStudioDao
  abstract fun todoNoteDao(): TodoNoteDao

  companion object {
    val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE messages ADD COLUMN blocksJson TEXT NOT NULL DEFAULT '[]'")
          db.execSQL("ALTER TABLE messages ADD COLUMN attachedMediaJson TEXT NOT NULL DEFAULT '[]'")
        }
      }

    val MIGRATION_2_3 =
      object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_preferences (
              prefKey TEXT NOT NULL PRIMARY KEY,
              prefValue TEXT NOT NULL,
              encrypted INTEGER NOT NULL DEFAULT 0,
              updatedAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memory_items (
              id TEXT NOT NULL PRIMARY KEY,
              category TEXT NOT NULL,
              contentPayload TEXT NOT NULL,
              keywordsCsv TEXT NOT NULL,
              importance REAL NOT NULL,
              isApproved INTEGER NOT NULL,
              isEncrypted INTEGER NOT NULL,
              createdAtMs INTEGER NOT NULL,
              updatedAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_items_category ON memory_items(category)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_items_updatedAtMs ON memory_items(updatedAtMs)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memory_summaries (
              conversationId TEXT NOT NULL PRIMARY KEY,
              summaryText TEXT NOT NULL,
              updatedAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_summaries_conversationId ON memory_summaries(conversationId)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS agent_tasks (
              id TEXT NOT NULL PRIMARY KEY,
              goal TEXT NOT NULL,
              selectedAgentsJson TEXT NOT NULL,
              status TEXT NOT NULL,
              summary TEXT NOT NULL,
              tokenUsage INTEGER NOT NULL,
              durationMs INTEGER NOT NULL,
              createdAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_tasks_createdAtMs ON agent_tasks(createdAtMs)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS research_sources (
              url TEXT NOT NULL PRIMARY KEY,
              title TEXT NOT NULL,
              publisher TEXT NOT NULL,
              domain TEXT NOT NULL,
              verificationStatus TEXT NOT NULL,
              retrievedAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_research_sources_domain ON research_sources(domain)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_research_sources_retrievedAtMs ON research_sources(retrievedAtMs)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS media_metadata (
              id TEXT NOT NULL PRIMARY KEY,
              kind TEXT NOT NULL,
              displayName TEXT NOT NULL,
              mimeType TEXT NOT NULL,
              byteSize INTEGER NOT NULL,
              summaryNote TEXT NOT NULL,
              createdAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_media_metadata_createdAtMs ON media_metadata(createdAtMs)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS automation_tasks (
              id TEXT NOT NULL PRIMARY KEY,
              workflowType TEXT NOT NULL,
              title TEXT NOT NULL,
              targetPackageOrArg TEXT NOT NULL,
              status TEXT NOT NULL,
              resultLog TEXT NOT NULL,
              triggerSource TEXT NOT NULL,
              createdAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_automation_tasks_createdAtMs ON automation_tasks(createdAtMs)")
        }
      }

    val MIGRATION_3_4 =
      object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS image_versions (
              id TEXT NOT NULL PRIMARY KEY,
              rootMediaId TEXT NOT NULL,
              parentMediaId TEXT,
              conversationId TEXT NOT NULL,
              messageId TEXT NOT NULL,
              versionNumber INTEGER NOT NULL,
              prompt TEXT NOT NULL,
              editInstruction TEXT,
              filePath TEXT NOT NULL,
              mimeType TEXT NOT NULL,
              width INTEGER NOT NULL,
              height INTEGER NOT NULL,
              providerModel TEXT NOT NULL,
              createdAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_image_versions_rootMediaId ON image_versions(rootMediaId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_image_versions_conversationId ON image_versions(conversationId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_image_versions_createdAtMs ON image_versions(createdAtMs)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS task_plans (
              id TEXT NOT NULL PRIMARY KEY,
              conversationId TEXT NOT NULL,
              goal TEXT NOT NULL,
              status TEXT NOT NULL,
              createdAtMs INTEGER NOT NULL,
              updatedAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_task_plans_conversationId ON task_plans(conversationId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_task_plans_createdAtMs ON task_plans(createdAtMs)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS task_steps (
              id TEXT NOT NULL PRIMARY KEY,
              planId TEXT NOT NULL,
              stepOrder INTEGER NOT NULL,
              agentRole TEXT NOT NULL,
              description TEXT NOT NULL,
              status TEXT NOT NULL,
              requiresApproval INTEGER NOT NULL,
              outputSummary TEXT NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_task_steps_planId ON task_steps(planId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_task_steps_stepOrder ON task_steps(stepOrder)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS voice_sessions (
              id TEXT NOT NULL PRIMARY KEY,
              conversationId TEXT NOT NULL,
              voiceGender TEXT NOT NULL,
              languageTag TEXT NOT NULL,
              turnsCount INTEGER NOT NULL,
              startedAtMs INTEGER NOT NULL,
              endedAtMs INTEGER
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_voice_sessions_startedAtMs ON voice_sessions(startedAtMs)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS drawing_annotations (
              id TEXT NOT NULL PRIMARY KEY,
              targetMediaId TEXT,
              strokesJson TEXT NOT NULL,
              boundingBoxJson TEXT NOT NULL,
              promptNote TEXT NOT NULL,
              createdAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_drawing_annotations_targetMediaId ON drawing_annotations(targetMediaId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_drawing_annotations_createdAtMs ON drawing_annotations(createdAtMs)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS rich_cards (
              id TEXT NOT NULL PRIMARY KEY,
              messageId TEXT NOT NULL,
              kind TEXT NOT NULL,
              title TEXT NOT NULL,
              subtitle TEXT NOT NULL,
              url TEXT NOT NULL,
              packageName TEXT,
              createdAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_rich_cards_messageId ON rich_cards(messageId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_rich_cards_createdAtMs ON rich_cards(createdAtMs)")
        }
      }

    val MIGRATION_4_5 =
      object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reminders (
              id TEXT NOT NULL PRIMARY KEY,
              type TEXT NOT NULL,
              title TEXT NOT NULL,
              hour INTEGER,
              minute INTEGER,
              durationSeconds INTEGER,
              scheduledAtMs INTEGER NOT NULL,
              recurrence TEXT NOT NULL,
              daysOfWeekCsv TEXT NOT NULL,
              enabled INTEGER NOT NULL,
              status TEXT NOT NULL,
              lastTriggeredAtMs INTEGER,
              createdAtMs INTEGER NOT NULL,
              verificationNote TEXT NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_scheduledAtMs ON reminders(scheduledAtMs)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_type ON reminders(type)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_status ON reminders(status)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS managed_tasks (
              id TEXT NOT NULL PRIMARY KEY,
              batchId TEXT NOT NULL,
              orderIndex INTEGER NOT NULL,
              description TEXT NOT NULL,
              kind TEXT NOT NULL,
              targetArg TEXT NOT NULL,
              requiredPermissionsJson TEXT NOT NULL,
              dependsOnIdsJson TEXT NOT NULL,
              priority INTEGER NOT NULL,
              state TEXT NOT NULL,
              resultMessage TEXT NOT NULL,
              errorDetails TEXT NOT NULL,
              retryCount INTEGER NOT NULL,
              maxRetries INTEGER NOT NULL,
              requiresApproval INTEGER NOT NULL,
              createdAtMs INTEGER NOT NULL,
              updatedAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_managed_tasks_batchId ON managed_tasks(batchId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_managed_tasks_createdAtMs ON managed_tasks(createdAtMs)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_managed_tasks_state ON managed_tasks(state)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS device_action_logs (
              id TEXT NOT NULL PRIMARY KEY,
              category TEXT NOT NULL,
              command TEXT NOT NULL,
              targetLabel TEXT NOT NULL,
              packageName TEXT,
              success INTEGER NOT NULL,
              statusLabel TEXT NOT NULL,
              verifiedDetail TEXT NOT NULL,
              timestampMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_device_action_logs_timestampMs ON device_action_logs(timestampMs)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_device_action_logs_category ON device_action_logs(category)")
        }
      }

    val MIGRATION_5_6 =
      object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS image_tasks (
              taskId TEXT NOT NULL PRIMARY KEY,
              conversationId TEXT NOT NULL,
              featureType TEXT NOT NULL,
              executionSource TEXT NOT NULL,
              prompt TEXT NOT NULL,
              editPlanJson TEXT NOT NULL,
              inputAssetId TEXT,
              maskPath TEXT,
              outputAssetId TEXT,
              providerId TEXT NOT NULL,
              modelId TEXT NOT NULL,
              status TEXT NOT NULL,
              errorMessage TEXT,
              createdAtMs INTEGER NOT NULL,
              updatedAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_image_tasks_conversationId ON image_tasks(conversationId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_image_tasks_status ON image_tasks(status)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_image_tasks_createdAtMs ON image_tasks(createdAtMs)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS image_assets (
              assetId TEXT NOT NULL PRIMARY KEY,
              localPreviewPath TEXT NOT NULL,
              mediaStoreUri TEXT,
              mimeType TEXT NOT NULL,
              width INTEGER NOT NULL,
              height INTEGER NOT NULL,
              fileSizeBytes INTEGER NOT NULL,
              hasTransparency INTEGER NOT NULL,
              sourceBadge TEXT NOT NULL,
              createdAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_image_assets_createdAtMs ON image_assets(createdAtMs)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS image_provider_status (
              providerId TEXT NOT NULL PRIMARY KEY,
              displayName TEXT NOT NULL,
              connectionStatus TEXT NOT NULL,
              quotaState TEXT NOT NULL,
              isFreeTier INTEGER NOT NULL,
              activeModelId TEXT NOT NULL,
              lastVerifiedAtMs INTEGER,
              lastErrorDetail TEXT
            )
            """.trimIndent()
          )
        }
      }

    val MIGRATION_6_7 =
      object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE memory_items ADD COLUMN originalStatement TEXT NOT NULL DEFAULT ''")
          db.execSQL("ALTER TABLE memory_items ADD COLUMN tagsCsv TEXT NOT NULL DEFAULT ''")
          db.execSQL("ALTER TABLE memory_items ADD COLUMN confidence REAL NOT NULL DEFAULT 0.85")
          db.execSQL("ALTER TABLE memory_items ADD COLUMN confirmationStatus TEXT NOT NULL DEFAULT 'CONFIRMED'")
          db.execSQL("ALTER TABLE memory_items ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
          db.execSQL("ALTER TABLE memory_items ADD COLUMN lastConfirmedAtMs INTEGER NOT NULL DEFAULT 0")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_items_isActive ON memory_items(isActive)")
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS todo_note_items (
              id TEXT NOT NULL PRIMARY KEY,
              kind TEXT NOT NULL,
              title TEXT NOT NULL,
              body TEXT NOT NULL,
              scheduleTimeLabel TEXT NOT NULL,
              isCompleted INTEGER NOT NULL,
              createdAtMs INTEGER NOT NULL,
              updatedAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_note_items_kind ON todo_note_items(kind)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_note_items_updatedAtMs ON todo_note_items(updatedAtMs)")
        }
      }
  }
}
