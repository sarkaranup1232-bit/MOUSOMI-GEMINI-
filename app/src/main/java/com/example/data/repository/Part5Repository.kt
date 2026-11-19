package com.example.data.repository

import com.example.data.local.db.DrawingAnnotationEntity
import com.example.data.local.db.ImageVersionEntity
import com.example.data.local.db.Part5Dao
import com.example.data.local.db.RichCardEntity
import com.example.data.local.db.TaskPlanEntity
import com.example.data.local.db.TaskStepEntity
import com.example.data.local.db.VoiceSessionEntity
import com.example.domain.model.AgentStepPlan
import com.example.domain.model.AnnotationStroke
import com.example.domain.model.GeneratedImageResult
import com.example.domain.model.ImageVersionRecord
import com.example.domain.model.NormBoundingBox
import com.example.domain.model.RichLinkMetadata
import com.example.domain.model.VoiceGender
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class Part5Repository @Inject constructor(
  private val part5Dao: Part5Dao,
  private val json: Json,
) {
  fun observeImageVersions(rootMediaId: String): Flow<List<ImageVersionRecord>> =
    part5Dao.observeImageVersions(rootMediaId).map { list -> list.map(::toVersionRecord) }

  fun observeConversationImageVersions(conversationId: String): Flow<List<ImageVersionRecord>> =
    part5Dao.observeConversationImageVersions(conversationId).map { list -> list.map(::toVersionRecord) }

  suspend fun getNextVersionNumber(rootMediaId: String): Int =
    (part5Dao.getImageVersionsSnapshot(rootMediaId).maxOfOrNull { it.versionNumber } ?: 0) + 1

  suspend fun getImageVersionsSnapshot(rootMediaId: String): List<ImageVersionRecord> =
    part5Dao.getImageVersionsSnapshot(rootMediaId).map(::toVersionRecord)

  suspend fun saveImageVersion(
    conversationId: String,
    messageId: String,
    result: GeneratedImageResult,
  ) {
    part5Dao.upsertImageVersion(
      ImageVersionEntity(
        id = result.mediaId,
        rootMediaId = result.rootMediaId,
        parentMediaId = result.parentMediaId,
        conversationId = conversationId,
        messageId = messageId,
        versionNumber = result.versionNumber,
        prompt = result.prompt,
        editInstruction = result.editInstruction,
        filePath = result.filePath,
        mimeType = result.mimeType,
        width = result.width,
        height = result.height,
        providerModel = result.providerModel,
        createdAtMs = result.createdAtMs,
      )
    )
  }

  suspend fun saveTaskPlan(
    conversationId: String,
    goal: String,
    steps: List<AgentStepPlan>,
    status: String,
  ) {
    val planId = "plan_${UUID.randomUUID()}"
    val now = System.currentTimeMillis()
    part5Dao.upsertTaskPlan(
      TaskPlanEntity(
        id = planId,
        conversationId = conversationId,
        goal = goal,
        status = status,
        createdAtMs = now,
        updatedAtMs = now,
      )
    )
    part5Dao.upsertTaskSteps(
      steps.mapIndexed { idx, step ->
        TaskStepEntity(
          id = step.id,
          planId = planId,
          stepOrder = idx,
          agentRole = step.agent.label,
          description = step.description,
          status = step.status.name,
          requiresApproval = step.requiresUserApproval,
          outputSummary = step.outputSummary,
        )
      }
    )
  }

  suspend fun recordVoiceSession(
    sessionId: String,
    conversationId: String,
    voiceGender: VoiceGender,
    languageTag: String,
    turnsCount: Int,
    startedAtMs: Long,
    endedAtMs: Long?,
  ) {
    part5Dao.upsertVoiceSession(
      VoiceSessionEntity(
        id = sessionId,
        conversationId = conversationId,
        voiceGender = voiceGender.name,
        languageTag = languageTag,
        turnsCount = turnsCount,
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
      )
    )
  }

  suspend fun saveDrawingAnnotation(
    targetMediaId: String?,
    strokes: List<AnnotationStroke>,
    box: NormBoundingBox,
    promptNote: String,
  ) {
    part5Dao.upsertDrawingAnnotation(
      DrawingAnnotationEntity(
        id = "ann_${UUID.randomUUID()}",
        targetMediaId = targetMediaId,
        strokesJson = runCatching { json.encodeToString(strokes) }.getOrDefault("[]"),
        boundingBoxJson = runCatching { json.encodeToString(box) }.getOrDefault("{}"),
        promptNote = promptNote,
        createdAtMs = System.currentTimeMillis(),
      )
    )
  }

  suspend fun saveRichCards(messageId: String, cards: List<RichLinkMetadata>) {
    val now = System.currentTimeMillis()
    cards.forEach { card ->
      part5Dao.upsertRichCard(
        RichCardEntity(
          id = card.id,
          messageId = messageId,
          kind = card.kind.name,
          title = card.title,
          subtitle = card.subtitle,
          url = card.url,
          packageName = card.packageName,
          createdAtMs = now,
        )
      )
    }
  }

  private fun toVersionRecord(entity: ImageVersionEntity): ImageVersionRecord =
    ImageVersionRecord(
      id = entity.id,
      rootMediaId = entity.rootMediaId,
      parentMediaId = entity.parentMediaId,
      conversationId = entity.conversationId,
      messageId = entity.messageId,
      versionNumber = entity.versionNumber,
      prompt = entity.prompt,
      editInstruction = entity.editInstruction,
      filePath = entity.filePath,
      mimeType = entity.mimeType,
      width = entity.width,
      height = entity.height,
      providerModel = entity.providerModel,
      createdAtMs = entity.createdAtMs,
    )
}
