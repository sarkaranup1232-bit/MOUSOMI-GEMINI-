package com.example

import com.example.core.agent.AgentOrchestrator
import com.example.core.device.AgentCommandIntent
import com.example.core.device.AssistantAgentCommandRouter
import com.example.core.device.MediaFileService
import com.example.core.device.PendingPhoneActionType
import com.example.core.device.PhoneActionController
import com.example.core.device.SocialIntegrationManager
import com.example.core.device.SocialPlatform
import com.example.core.security.KeystoreCryptoManager
import com.example.core.voice.DeviceRuntimeProfile
import com.example.core.voice.HotwordCapabilityManager
import com.example.core.voice.HotwordCapabilityState
import com.example.data.local.db.MemoryDao
import com.example.data.local.db.MemoryItemEntity
import com.example.data.local.db.MemorySummaryEntity
import com.example.data.local.db.UserPreferenceEntity
import com.example.domain.context.UnifiedContextEngine
import com.example.domain.memory.FavoriteMusicCommandResult
import com.example.domain.memory.FavoriteMusicManager
import com.example.domain.memory.LongTermMemoryManager
import com.example.domain.memory.MemoryPrivacyManager
import com.example.domain.memory.MemoryRetrievalEngine
import com.example.domain.memory.MemoryWriteOutcome
import com.example.domain.memory.PersonalMemoryRepository
import com.example.domain.memory.PersonalizedResponseEngine
import com.example.domain.memory.PreferenceConfirmationManager
import com.example.domain.memory.PreferenceDiscoveryEngine
import com.example.domain.model.MemoryCategory
import com.example.domain.model.VerifiedModelCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemoryFakeMemoryDao : MemoryDao {
  private val rows = linkedMapOf<String, MemoryItemEntity>()
  private val rowsFlow = MutableStateFlow<List<MemoryItemEntity>>(emptyList())
  private val summaries = linkedMapOf<String, MemorySummaryEntity>()
  private val summariesFlow = MutableStateFlow<List<MemorySummaryEntity>>(emptyList())
  private val prefs = linkedMapOf<String, UserPreferenceEntity>()

  private fun publishRows() {
    rowsFlow.value =
      rows.values.sortedWith(
        compareByDescending<MemoryItemEntity> { it.importance }.thenByDescending { it.updatedAtMs }
      )
  }

  override fun observeAllMemories(): Flow<List<MemoryItemEntity>> = rowsFlow

  override suspend fun getApprovedMemoriesSnapshot(): List<MemoryItemEntity> =
    rowsFlow.value.filter { it.isApproved && it.isActive }

  override suspend fun getAllMemoriesSnapshot(): List<MemoryItemEntity> = rowsFlow.value

  override suspend fun getActiveMemoriesByCategory(category: String): List<MemoryItemEntity> =
    rowsFlow.value
      .filter { it.category == category && it.isApproved && it.isActive }
      .sortedByDescending { it.updatedAtMs }

  override suspend fun getMemoryById(id: String): MemoryItemEntity? = rows[id]

  override suspend fun upsertMemory(entity: MemoryItemEntity) {
    rows[entity.id] = entity
    publishRows()
  }

  override suspend fun updateActiveFlag(id: String, isActive: Boolean, updatedAtMs: Long) {
    val existing = rows[id] ?: return
    rows[id] = existing.copy(isActive = isActive, updatedAtMs = updatedAtMs)
    publishRows()
  }

  override suspend fun deleteMemory(id: String) {
    rows.remove(id)
    publishRows()
  }

  override suspend fun deleteByCategory(category: String) {
    rows.entries.removeAll { it.value.category == category }
    publishRows()
  }

  override suspend fun clearAllMemories() {
    rows.clear()
    publishRows()
  }

  override fun observeSummaries(): Flow<List<MemorySummaryEntity>> = summariesFlow

  override suspend fun upsertSummary(entity: MemorySummaryEntity) {
    summaries[entity.conversationId] = entity
    summariesFlow.value = summaries.values.sortedByDescending { it.updatedAtMs }
  }

  override suspend fun upsertUserPreference(entity: UserPreferenceEntity) {
    prefs[entity.prefKey] = entity
  }

  override suspend fun getAllPreferences(): List<UserPreferenceEntity> = prefs.values.toList()
}

class PersonalMemoryAndAgentUpgradeTest {

  private fun buildMemoryStack(dao: MemoryDao): Triple<PersonalMemoryRepository, FavoriteMusicManager, PreferenceConfirmationManager> {
    val crypto = KeystoreCryptoManager()
    val privacy = MemoryPrivacyManager(crypto)
    val ltm = LongTermMemoryManager(dao, crypto)
    val repo = PersonalMemoryRepository(ltm, privacy)
    val music = FavoriteMusicManager(repo)
    val confirmation = PreferenceConfirmationManager(repo, privacy)
    return Triple(repo, music, confirmation)
  }

  @Test
  fun `favorite song lifecycle persists across repo restart, new chats, edit, delete, and model switching`() = runBlocking {
    val sharedDao = InMemoryFakeMemoryDao()
    val (_, music1, confirm1) = buildMemoryStack(sharedDao)

    // 1. User says "My favorite song is Tum Hi Ho by Arijit Singh" -> generates confirmation prompt first
    val pending = confirm1.inspectUserStatement("My favorite song is Tum Hi Ho by Arijit Singh", memoryEnabled = true)
    assertNotNull(pending)
    assertEquals(MemoryCategory.FAVORITE_SONG, pending!!.category)
    assertEquals("Tum Hi Ho by Arijit Singh", pending.extractedContent)

    // Confirm save
    val saveOutcome = confirm1.confirmAndSave(pending, encryptAtRest = true)
    assertTrue(saveOutcome is MemoryWriteOutcome.Saved)
    val savedId = (saveOutcome as MemoryWriteOutcome.Saved).record.id

    // 2. Simulate full app restart by instantiating fresh Repository/Managers over the SAME persistent DAO
    val (repoAfterRestart, musicAfterRestart, _) = buildMemoryStack(sharedDao)
    val ltmAfterRestart = LongTermMemoryManager(sharedDao, KeystoreCryptoManager())
    val retrieval = MemoryRetrievalEngine(repoAfterRestart)
    val privacy = MemoryPrivacyManager(KeystoreCryptoManager())
    val personalizedEngine = PersonalizedResponseEngine(retrieval, privacy)
    val contextEngine = UnifiedContextEngine(ltmAfterRestart, null, retrieval, privacy)

    // 3. New chat retrieves "What is my favorite song?"
    val (personalCtx, retrievedRecords) =
      personalizedEngine.buildPersonalizationContext("What is my favorite song?", memoryEnabled = true)
    assertTrue(personalCtx.contains("Tum Hi Ho by Arijit Singh"))
    assertTrue(retrievedRecords.any { it.category == MemoryCategory.FAVORITE_SONG })

    // 4. Ask "Play my favorite song" -> resolves stored top favorite song for real playback launch
    val playCmd = musicAfterRestart.tryHandleMusicCommand("Play my favorite song")
    assertTrue(playCmd is FavoriteMusicCommandResult.LaunchMusicPlayback)
    assertEquals("Tum Hi Ho by Arijit Singh", (playCmd as FavoriteMusicCommandResult.LaunchMusicPlayback).query)

    // 5. Switch AI models (Gemini 4 Hydrogen -> Groq Reasoning -> Mousomi Auto) with identical persisted memory
    val geminiCtx =
      contextEngine.buildContext(
        query = "Suggest songs similar to my favorite song",
        model = VerifiedModelCatalog.createHydrogenSpec(),
        historyMessages = emptyList(),
        pendingMedia = emptyList(),
        memoryEnabled = true,
      )
    val groqCtx =
      contextEngine.buildContext(
        query = "Suggest songs similar to my favorite song",
        model = VerifiedModelCatalog.createGroqReasoningSpec(),
        historyMessages = emptyList(),
        pendingMedia = emptyList(),
        memoryEnabled = true,
      )
    val autoCtx =
      contextEngine.buildContext(
        query = "Suggest songs similar to my favorite song",
        model = VerifiedModelCatalog.mousomiAuto,
        historyMessages = emptyList(),
        pendingMedia = emptyList(),
        memoryEnabled = true,
      )
    assertTrue(geminiCtx.contextBlock.contains("Tum Hi Ho by Arijit Singh"))
    assertTrue(groqCtx.contextBlock.contains("Tum Hi Ho by Arijit Singh"))
    assertTrue(autoCtx.contextBlock.contains("Tum Hi Ho by Arijit Singh"))

    // 6. Edit favorite song from My Memory and verify updated value is returned
    repoAfterRestart.updateMemory(id = savedId, content = "Yellow by Coldplay", encryptAtRest = true)
    val afterEditPlay = musicAfterRestart.tryHandleMusicCommand("Play my favorite song")
    assertTrue(afterEditPlay is FavoriteMusicCommandResult.LaunchMusicPlayback)
    assertEquals("Yellow by Coldplay", (afterEditPlay as FavoriteMusicCommandResult.LaunchMusicPlayback).query)

    // 7. Delete favorite song and verify deleted memory is never injected again
    val removeRes = musicAfterRestart.tryHandleMusicCommand("Remove Yellow by Coldplay from favorites")
    assertTrue(removeRes is FavoriteMusicCommandResult.RemovedFavorite)
    val afterDeleteCtx =
      contextEngine.buildContext(
        query = "What is my favorite song?",
        model = VerifiedModelCatalog.createHydrogenSpec(),
        historyMessages = emptyList(),
        pendingMedia = emptyList(),
        memoryEnabled = true,
      )
    assertFalse(afterDeleteCtx.contextBlock.contains("Yellow by Coldplay"))
    assertEquals(0, afterDeleteCtx.injectedMemoryCount)
  }

  @Test
  fun `memory privacy blocks OTPs passwords API keys PINs and payment secrets`() = runBlocking {
    val dao = InMemoryFakeMemoryDao()
    val (repo, _, confirmation) = buildMemoryStack(dao)

    val sensitiveInputs =
      listOf(
        "My OTP is 492817",
        "Remember my password: SuperSecretPassword123",
        "My Gemini key is AIzaSyD89fJ2kL99mN3pQ4rS5tU6vW7xY8zA",
        "Groq key gsk_1234567890abcdefghijklmnopqr",
        "Remember my ATM PIN 4455",
        "My Visa card number 4532 1234 5678 9012 CVV 889",
      )

    sensitiveInputs.forEach { secret ->
      assertNull("Confirmation must skip secret input: $secret", confirmation.inspectUserStatement(secret, memoryEnabled = true))
      val writeOutcome = repo.saveConfirmedMemory(MemoryCategory.IMPORTANT_INFO, secret)
      assertTrue("Repository must block secret input: $secret", writeOutcome is MemoryWriteOutcome.BlockedSensitive)
    }

    assertTrue(dao.getAllMemoriesSnapshot().isEmpty())
  }

  @Test
  fun `preference discovery asks at most once skips already saved categories and respects opt-out toggles`() = runBlocking {
    val dao = InMemoryFakeMemoryDao()
    val (repo, _, _) = buildMemoryStack(dao)
    val discovery = PreferenceDiscoveryEngine(repo)

    // Disabled memory or disabled proactive toggle -> returns null
    assertNull(
      discovery.maybeSuggestDiscoveryQuestion(
        userPrompt = "Let's chat",
        memoryEnabled = false,
        proactiveQuestionsEnabled = true,
        userTurnCountInConversation = 3,
      )
    )
    assertNull(
      discovery.maybeSuggestDiscoveryQuestion(
        userPrompt = "Let's chat",
        memoryEnabled = true,
        proactiveQuestionsEnabled = false,
        userTurnCountInConversation = 3,
      )
    )

    // Save favorite song & language preference so discovery skips both topics
    repo.saveConfirmedMemory(MemoryCategory.FAVORITE_SONG, "Kesariya")
    repo.saveConfirmedMemory(MemoryCategory.LANGUAGE_PREFERENCE, "English and Bengali")

    val prompt =
      discovery.maybeSuggestDiscoveryQuestion(
        userPrompt = "Tell me something interesting today",
        memoryEnabled = true,
        proactiveQuestionsEnabled = true,
        userTurnCountInConversation = 3,
        nowMs = 1_000_000L,
      )
    assertNotNull(prompt)
    assertTrue(
      prompt!!.targetCategory != MemoryCategory.FAVORITE_SONG &&
        prompt.targetCategory != MemoryCategory.LANGUAGE_PREFERENCE
    )

    // Cooldown prevents spamming on the very next turn
    val immediateSecondPrompt =
      discovery.maybeSuggestDiscoveryQuestion(
        userPrompt = "Another message",
        memoryEnabled = true,
        proactiveQuestionsEnabled = true,
        userTurnCountInConversation = 4,
        nowMs = 1_005_000L,
      )
    assertNull(immediateSecondPrompt)
  }

  @Test
  fun `galaxy M51 Android 12 One UI 4_1 hotword capability evaluator reports honest states`() {
    val galaxyM51 =
      DeviceRuntimeProfile(
        manufacturer = "samsung",
        brand = "samsung",
        model = "SM-M515F",
        sdkInt = 31,
        androidRelease = "12",
        oneUiVersion = "4.1",
        isSamsungOneUi = true,
      )

    // 1. Not default assistant yet
    val notDefault =
      HotwordCapabilityManager.evaluateFromInputs(
        profile = galaxyM51,
        isDefaultAssistant = false,
        isVoiceInteractionActiveService = false,
        hasRecordAudioPermission = true,
        hasCaptureAudioHotwordPermission = false,
        hasManageVoiceKeyphrasesPermission = false,
        hasMicrophoneHardware = true,
        supportsSpeechRecognition = true,
        supportsOnDeviceRecognition = false,
      )
    assertEquals(HotwordCapabilityState.DEFAULT_ASSISTANT_NOT_SELECTED, notDefault.state)
    assertFalse(notDefault.supportsDspHotwordForThirdParty)

    // 2. Default assistant selected, mic granted, stock One UI (no privileged hotword perms)
    val stockOneUi =
      HotwordCapabilityManager.evaluateFromInputs(
        profile = galaxyM51,
        isDefaultAssistant = true,
        isVoiceInteractionActiveService = true,
        hasRecordAudioPermission = true,
        hasCaptureAudioHotwordPermission = false,
        hasManageVoiceKeyphrasesPermission = false,
        hasMicrophoneHardware = true,
        supportsSpeechRecognition = true,
        supportsOnDeviceRecognition = false,
      )
    assertEquals(HotwordCapabilityState.DSP_HARDWARE_UNSUPPORTED, stockOneUi.state)
    assertTrue(stockOneUi.supportsForegroundLiveMicInSession)
    assertFalse(stockOneUi.supportsDspHotwordForThirdParty)
    assertTrue(stockOneUi.reasonSummary.contains("CAPTURE_AUDIO_HOTWORD"))

    // 3. Mic permission missing
    val micMissing =
      HotwordCapabilityManager.evaluateFromInputs(
        profile = galaxyM51,
        isDefaultAssistant = true,
        isVoiceInteractionActiveService = true,
        hasRecordAudioPermission = false,
        hasCaptureAudioHotwordPermission = false,
        hasManageVoiceKeyphrasesPermission = false,
        hasMicrophoneHardware = true,
        supportsSpeechRecognition = true,
        supportsOnDeviceRecognition = false,
      )
    assertEquals(HotwordCapabilityState.PERMISSION_REQUIRED, micMissing.state)
    assertFalse(micMissing.supportsForegroundLiveMicInSession)

    // 4. Privileged system ROM with enrolled keyphrase
    val systemRomReady =
      HotwordCapabilityManager.evaluateFromInputs(
        profile = galaxyM51,
        isDefaultAssistant = true,
        isVoiceInteractionActiveService = true,
        hasRecordAudioPermission = true,
        hasCaptureAudioHotwordPermission = true,
        hasManageVoiceKeyphrasesPermission = true,
        hasMicrophoneHardware = true,
        supportsSpeechRecognition = true,
        supportsOnDeviceRecognition = true,
        keyphraseEnrolled = true,
      )
    assertEquals(HotwordCapabilityState.HOTWORD_READY, systemRomReady.state)
    assertTrue(systemRomReady.supportsDspHotwordForThirdParty)
  }

  @Test
  fun `command router, multi-step splitter, image extraction, phone & social gates remain accurate`() {
    val router = AssistantAgentCommandRouter()

    // Declarative favorite statement goes to chat + confirmation flow (not AgentOrchestrator)
    assertNull(router.classify("My favorite song is Tum Hi Ho"))

    // Music retrieval / playback commands route to ExecuteAgentTask
    assertTrue(router.classify("Play my favorite song") is AgentCommandIntent.ExecuteAgentTask)
    assertTrue(router.classify("Show my favorite music") is AgentCommandIntent.ExecuteAgentTask)
    assertTrue(router.classify("Remove Yellow from favorites") is AgentCommandIntent.ExecuteAgentTask)

    // Image generation command routing
    val imgIntent = router.classify("Generate an image of a cyberpunk Dhaka skyline")
    assertTrue(imgIntent is AgentCommandIntent.GenerateImage)
    assertEquals("a cyberpunk Dhaka skyline", (imgIntent as AgentCommandIntent.GenerateImage).imagePrompt)
    assertEquals("a sunset over Padma river", MediaFileService.extractImageGenerationPrompt("Draw a picture of a sunset over Padma river"))

    // Compound multi-step splitting
    val steps = AgentOrchestrator.splitCompoundCommand("Set media volume to 50% and then play my favorite song; open call history")
    assertEquals(listOf("Set media volume to 50%", "play my favorite song", "open call history"), steps)

    // OS silent toggle honesty
    val wifiToggle = router.classify("Turn off Wi-Fi")
    assertTrue(wifiToggle is AgentCommandIntent.UnsupportedSilentToggle)

    // Phone & Social confirmation routing still honors confirmation & privacy rules
    val callAction = PhoneActionController.tryParseConfirmationCommand("Call Mom")
    assertNotNull(callAction)
    assertEquals(PendingPhoneActionType.DIAL_PHONE, callAction!!.type)

    val waAction = SocialIntegrationManager.tryParseSocialCommand("Send WhatsApp message to Rahul saying Hi there")
    assertNotNull(waAction)
    assertEquals(SocialPlatform.WHATSAPP, waAction!!.platform)
  }
}
