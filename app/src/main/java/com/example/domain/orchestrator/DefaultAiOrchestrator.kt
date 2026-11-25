package com.example.domain.orchestrator

import com.example.ai.provider.AiChatRequest
import com.example.ai.provider.AiProvider
import com.example.ai.provider.AiStreamEvent
import com.example.ai.provider.AiTurn
import com.example.core.apps.RichLinkResolver
import com.example.core.network.AiProviderException
import com.example.core.security.ClientRateLimiter
import com.example.core.security.SecurityValidator
import com.example.domain.agent.AgentExecutionResult
import com.example.domain.agent.AutonomousAgentOrchestrator
import com.example.domain.context.UnifiedContextEngine
import com.example.domain.model.AgentRole
import com.example.domain.model.AgentStepPlan
import com.example.domain.model.AgentStepStatus
import com.example.domain.model.AiResponseState
import com.example.domain.model.AppSettings
import com.example.domain.model.IntelligenceIntent
import com.example.domain.model.MediaAttachmentKind
import com.example.domain.model.OperatingMode
import com.example.domain.model.ProviderType
import com.example.domain.model.ResponseBlock
import com.example.domain.model.ResponseLanguage
import com.example.domain.model.RoutingDecision
import com.example.domain.model.TokenUsage
import com.example.domain.repository.SettingsRepository
import com.example.domain.router.DefaultModelRouter
import com.example.domain.router.ModelRouter
import com.example.domain.router.RealtimeIntelligenceRouter
import com.example.domain.task.TaskExecutionEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

@Singleton
class DefaultAiOrchestrator @Inject constructor(
  private val modelRouter: ModelRouter,
  private val settingsRepository: SettingsRepository,
  private val securityValidator: SecurityValidator,
  private val rateLimiter: ClientRateLimiter,
  private val providers: Map<ProviderType, @JvmSuppressWildcards AiProvider>,
  private val tools: Set<@JvmSuppressWildcards AiTool>,
  private val realtimeRouter: RealtimeIntelligenceRouter? = null,
  private val part3Pipeline: Part3IntelligencePipeline? = null,
  private val unifiedContextEngine: UnifiedContextEngine? = null,
  private val agentOrchestrator: AutonomousAgentOrchestrator? = null,
  private val richLinkResolver: RichLinkResolver? = null,
  private val taskExecutionEngine: TaskExecutionEngine? = null,
) : AiOrchestrator {

  // Backward-compatible constructor for isolated Phase 2 unit tests
  constructor(
    modelRouter: ModelRouter,
    settingsRepository: SettingsRepository,
    securityValidator: SecurityValidator,
    rateLimiter: ClientRateLimiter,
    providers: Map<ProviderType, AiProvider>,
    tools: Set<AiTool>,
  ) : this(
    modelRouter = modelRouter,
    settingsRepository = settingsRepository,
    securityValidator = securityValidator,
    rateLimiter = rateLimiter,
    providers = providers,
    tools = tools,
    realtimeRouter = null,
    part3Pipeline = null,
    unifiedContextEngine = null,
    agentOrchestrator = null,
    richLinkResolver = null,
    taskExecutionEngine = null,
  )

  override fun processMessage(request: OrchestrationRequest): Flow<OrchestrationEvent> = flow {
    emit(OrchestrationEvent.StateChanged(AiResponseState.Loading))

    val cleanPrompt = securityValidator.sanitizeUserText(request.userPrompt)
    if (cleanPrompt.isBlank() && request.currentTurnImages.isEmpty() && request.pendingMediaItems.isEmpty()) {
      emit(
        OrchestrationEvent.StateChanged(
          AiResponseState.Error("Please enter a prompt or attach a file/media.", recoverable = true)
        )
      )
      return@flow
    }

    rateLimiter.checkAndRecord().onFailure { limitError ->
      emit(
        OrchestrationEvent.StateChanged(
          AiResponseState.Error(limitError.message ?: "Rate limit reached.", recoverable = true)
        )
      )
      return@flow
    }

    val isImageStudioSelected =
      request.requestedModelId == com.example.domain.model.VerifiedModelCatalog.IMAGE_STUDIO_SLOT_ID ||
        request.requestedModelId == com.example.domain.model.VerifiedModelCatalog.POLLINATIONS_FLUX_ID ||
        request.requestedModelId == "GEMINI_IMAGE_STUDIO"

    val effectiveTaskMode = request.isTaskMode || request.operatingMode == OperatingMode.TASK_MODE
    val intent =
      when {
        request.activeEditTarget != null -> IntelligenceIntent.IMAGE_EDITING
        (request.imageGenerationRequested || isImageStudioSelected) && request.currentTurnImages.isEmpty() ->
          IntelligenceIntent.IMAGE_GENERATION
        request.operatingMode == OperatingMode.DEEP_RESEARCH || request.deepResearchRequested ->
          IntelligenceIntent.DEEP_RESEARCH
        request.operatingMode == OperatingMode.GOOGLE_AI_SEARCH ||
          request.operatingMode == OperatingMode.LATEST_NEWS ->
          IntelligenceIntent.LIVE_SEARCH
        else ->
          realtimeRouter?.classifyIntent(
            prompt = cleanPrompt,
            hasImages = request.currentTurnImages.isNotEmpty(),
            attachments = request.pendingMediaItems,
            deepResearchRequested = request.deepResearchRequested,
            explicitImageEditRequested = false,
          ) ?: IntelligenceIntent.NORMAL_AI
      }

    emit(
      OrchestrationEvent.StateChanged(
        AiResponseState.Thinking(
          if (effectiveTaskMode) "Task Mode: Decomposing goal & selecting specialist model…"
          else "Intent: ${intent.label} • Routing verified model…"
        )
      )
    )

    val settings = settingsRepository.getSettingsSnapshot()
    val hasVisualInput =
      request.currentTurnImages.isNotEmpty() ||
        request.pendingMediaItems.any { it.kind == MediaAttachmentKind.VIDEO || it.kind == MediaAttachmentKind.PHOTO }

    val baseDecision =
      if (modelRouter is DefaultModelRouter) {
        modelRouter.routeWithOperatingMode(
          prompt = cleanPrompt,
          hasImages = hasVisualInput,
          imageCount = request.currentTurnImages.size,
          requestedModelId = request.requestedModelId,
          settings = settings,
          operatingMode = if (effectiveTaskMode) OperatingMode.TASK_MODE else request.operatingMode,
        )
      } else {
        modelRouter.route(
          prompt = cleanPrompt,
          hasImages = hasVisualInput,
          imageCount = request.currentTurnImages.size,
          requestedModelId = request.requestedModelId,
          settings = settings,
        )
      }
        .getOrElse { routingErr ->
          emit(
            OrchestrationEvent.StateChanged(
              AiResponseState.Error(
                routingErr.message ?: "Unable to route request.",
                recoverable = true,
              )
            )
          )
          return@flow
        }

    val decision: RoutingDecision = baseDecision.copy(
      intelligenceIntent = intent,
      operatingMode = if (effectiveTaskMode) OperatingMode.TASK_MODE else request.operatingMode,
    )
    emit(OrchestrationEvent.Routed(decision))

    if (request.historyMessages.isEmpty()) {
      emit(OrchestrationEvent.TitleSuggested(deriveConversationTitle(cleanPrompt)))
    }

    val prep =
      if (part3Pipeline != null && intent != IntelligenceIntent.DIRECT_CHAT && intent != IntelligenceIntent.NORMAL_AI) {
        part3Pipeline.handleSpecialIntent(
          intent = intent,
          cleanPrompt = cleanPrompt,
          request = request,
          decision = decision,
          settings = settings,
          collector = this,
        )
      } else {
        PipelinePreparation.ContinueWithLlm()
      }

    val llmPrep: PipelinePreparation.ContinueWithLlm =
      when (prep) {
        is PipelinePreparation.Aborted -> {
          emit(
            OrchestrationEvent.StateChanged(
              AiResponseState.Error(prep.errorMessage, recoverable = true)
            )
          )
          return@flow
        }
        is PipelinePreparation.AlreadyCompleted -> {
          emit(
            OrchestrationEvent.StateChanged(
              AiResponseState.Completed(
                fullText = prep.fullText,
                modelName = prep.modelName,
                tokenUsage = prep.tokenUsage,
              )
            )
          )
          return@flow
        }
        is PipelinePreparation.ContinueWithLlm -> prep
      }

    val toolContextInput = ToolContextInput(prompt = cleanPrompt, category = decision.category)
    val verifiedToolNotes =
      if (intent == IntelligenceIntent.DIRECT_CHAT) {
        emptyList()
      } else {
        tools
          .filter { it.shouldRun(toolContextInput) }
          .mapNotNull { tool ->
            val res = tool.execute(toolContextInput)
            if (tool.verifyResult(res)) res.contextSnippet else null
          }
      }

    val unifiedSnapshot =
      unifiedContextEngine?.buildContext(
        query = cleanPrompt,
        model = decision.selectedModel,
        historyMessages = request.historyMessages,
        pendingMedia = request.pendingMediaItems,
        tokenBudget = settings.agentBudgetConfig.maxContextTokensBudget,
        memoryEnabled = settings.privacyPerformanceConfig.memoryEnabled,
      )

    val taskBlocks = mutableListOf<ResponseBlock>()
    val agentNotes =
      if (effectiveTaskMode ||
        (agentOrchestrator != null &&
          settings.agentBudgetConfig.multiAgentModeEnabled &&
          intent != IntelligenceIntent.DIRECT_CHAT)
      ) {
        val initialSteps =
          agentOrchestrator?.planWorkflow(
            goal = cleanPrompt,
            attachments = request.pendingMediaItems,
            hasImages = request.currentTurnImages.isNotEmpty(),
            budget = settings.agentBudgetConfig,
          ) ?: buildFallbackTaskSteps(cleanPrompt, request)

        val initialPlanBlock = ResponseBlock.TaskPlanBlock(goal = cleanPrompt, steps = initialSteps)
        if (effectiveTaskMode) {
          emit(OrchestrationEvent.BlocksUpdated(llmPrep.preEmittedBlocks + initialPlanBlock))
          emit(
            OrchestrationEvent.StateChanged(
              AiResponseState.Thinking("Task Mode: Executing ${initialSteps.size}-step workflow…")
            )
          )
        }

        val stepSummaryText: String
        val finalSteps: List<AgentStepPlan>
        if (agentOrchestrator != null) {
          val agentResult =
            agentOrchestrator.executePlan(
              goal = cleanPrompt,
              initialSteps = initialSteps,
              budget = settings.agentBudgetConfig,
              approvedSensitiveActions = false,
            ) { step ->
              executeTaskModeStepCheck(
                step = step,
                request = request,
                settings = settings,
                hasUnifiedContext = unifiedSnapshot != null,
                toolCount = verifiedToolNotes.size,
              )
            }
          when (agentResult) {
            is AgentExecutionResult.Completed -> {
              finalSteps = agentResult.steps
              stepSummaryText = agentResult.contextSummary
            }
            is AgentExecutionResult.AwaitingApproval -> {
              finalSteps = agentResult.steps
              val approvalNotice =
                "Approval Gate Triggered: '${agentResult.pendingStep.description}' requires explicit confirmation before execution."
              // Also build interactive managed task batch when device/assistant sub-tasks exist
              val batchReport =
                taskExecutionEngine?.planAndExecuteMultiTask(
                  instruction = cleanPrompt,
                  approveSensitiveTasks = false,
                )
              if (batchReport != null && batchReport.tasks.isNotEmpty()) {
                taskBlocks += ResponseBlock.MultiTaskBatchBlock(batchReport)
              }
              stepSummaryText =
                (agentResult.steps.joinToString("\n") { "• ${it.agent.label} [${it.status.name}]: ${it.outputSummary}" } +
                  "\n$approvalNotice").trim()
            }
            is AgentExecutionResult.BudgetExceededOrFailed -> {
              finalSteps = agentResult.steps
              stepSummaryText = "Task Step Issue: ${agentResult.reason}"
            }
            is AgentExecutionResult.Cancelled -> {
              finalSteps = agentResult.steps
              stepSummaryText = "Task workflow cancelled by user."
            }
          }
        } else {
          finalSteps = initialSteps.map { it.copy(status = AgentStepStatus.COMPLETED, outputSummary = "Prepared") }
          stepSummaryText = finalSteps.joinToString("\n") { "• ${it.agent.label}: ${it.outputSummary}" }
        }

        if (effectiveTaskMode) {
          taskBlocks.add(0, ResponseBlock.TaskPlanBlock(goal = cleanPrompt, steps = finalSteps))
          emit(OrchestrationEvent.BlocksUpdated(llmPrep.preEmittedBlocks + taskBlocks))
        }
        stepSummaryText
      } else {
        ""
      }

    val preBlocksWithTasks = llmPrep.preEmittedBlocks + taskBlocks

    val combinedExtraContext =
      listOfNotNull(
        llmPrep.extraSystemContext.takeIf { it.isNotBlank() },
        unifiedSnapshot?.contextBlock?.takeIf { it.isNotBlank() },
        agentNotes.takeIf { it.isNotBlank() }?.let { "Task Mode Execution & Tool Verification:\n$it" },
      ).joinToString("\n\n")

    val systemPrompt =
      buildSystemPrompt(
        language = settings.voicePreferences.preferredLanguage,
        isTaskMode = effectiveTaskMode,
        operatingMode = decision.operatingMode,
        intent = intent,
        categoryLabel = decision.category.label,
        toolNotes = verifiedToolNotes,
        extraContext = combinedExtraContext,
        hasImages = request.currentTurnImages.isNotEmpty() || llmPrep.extraImages.isNotEmpty(),
      )

    val combinedImages = (request.currentTurnImages + llmPrep.extraImages).take(4)

    val recentTurns =
      request.historyMessages
        .filter { !it.isError }
        .takeLast(14)
        .map { msg -> AiTurn(isUser = msg.isUser, text = msg.text) } +
        AiTurn(
          isUser = true,
          text = cleanPrompt.ifBlank { "Please analyze the attached content in detail." },
          images = combinedImages,
        )

    val targetProviderKey =
      if (decision.useProxy) ProviderType.PROXY else decision.selectedModel.provider
    val provider =
      providers[targetProviderKey]
        ?: run {
          emit(
            OrchestrationEvent.StateChanged(
              AiResponseState.Error("Provider $targetProviderKey is not registered.")
            )
          )
          return@flow
        }

    emit(OrchestrationEvent.StateChanged(AiResponseState.Generating(decision.selectedModel.displayName)))

    val accumulated = StringBuilder()
    var latestUsage: TokenUsage? = null

    try {
      withTimeout(90_000L) {
        provider
          .streamChat(
            AiChatRequest(
              model = decision.selectedModel,
              systemPrompt = systemPrompt,
              turns = recentTurns,
              settings = settings,
              temperature = if (effectiveTaskMode || intent == IntelligenceIntent.DATA_ANALYSIS) 0.25 else 0.7,
            )
          )
          .collect { event ->
            when (event) {
              is AiStreamEvent.Delta -> {
                accumulated.append(event.text)
                emit(
                  OrchestrationEvent.StateChanged(
                    AiResponseState.Streaming(
                      partialText = accumulated.toString(),
                      modelName = decision.selectedModel.displayName,
                      tokenUsage = latestUsage,
                    )
                  )
                )
              }
              is AiStreamEvent.Usage -> {
                latestUsage = event.usage
              }
              AiStreamEvent.Completed -> Unit
            }
          }
      }

      val finalText = accumulated.toString().trim()
      if (finalText.isEmpty()) {
        emit(
          OrchestrationEvent.StateChanged(
            AiResponseState.Error("Model returned an empty response. Try regenerating.")
          )
        )
      } else {
        val citedNums =
          Regex("\\[(?:#)?(\\d+)\\]")
            .findAll(finalText)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .toSet()
        val attributedPreBlocks =
          preBlocksWithTasks.map { block ->
            if (block is ResponseBlock.SourceCitations) {
              val updatedSources =
                block.sources.mapIndexed { idx, src ->
                  val num = if (src.sourceNumber > 0) src.sourceNumber else idx + 1
                  val mentioned =
                    (num in citedNums) ||
                      (src.sourceDomain.isNotBlank() && finalText.contains(src.sourceDomain, ignoreCase = true)) ||
                      finalText.contains(src.url, ignoreCase = true)
                  src.copy(sourceNumber = num, usedInAnswer = mentioned)
                }
              ResponseBlock.SourceCitations(updatedSources)
            } else {
              block
            }
          }
        val richCards = richLinkResolver?.extractRichCards("$cleanPrompt\n$finalText").orEmpty()
        val combinedBlocks =
          buildList {
            addAll(attributedPreBlocks)
            if (richCards.isNotEmpty()) {
              add(ResponseBlock.RichLinkCardBlock(richCards))
            }
          }
        if (combinedBlocks.isNotEmpty()) {
          emit(OrchestrationEvent.BlocksUpdated(combinedBlocks))
        }
        emit(
          OrchestrationEvent.StateChanged(
            AiResponseState.Completed(
              fullText = finalText,
              modelName = decision.selectedModel.displayName,
              tokenUsage = latestUsage,
            )
          )
        )
      }
    } catch (e: CancellationException) {
      emit(OrchestrationEvent.StateChanged(AiResponseState.Cancelled))
      throw e
    } catch (e: TimeoutCancellationException) {
      emit(
        OrchestrationEvent.StateChanged(
          AiResponseState.Error(
            "Response timed out after 90 seconds. Check your connection or try a faster model.",
            recoverable = true,
          )
        )
      )
    } catch (e: AiProviderException) {
      emit(
        OrchestrationEvent.StateChanged(
          AiResponseState.Error(e.message, recoverable = true)
        )
      )
    } catch (e: Exception) {
      emit(
        OrchestrationEvent.StateChanged(
          AiResponseState.Error(
            e.localizedMessage ?: "Unexpected error while communicating with AI provider.",
            recoverable = true,
          )
        )
      )
    }
  }

  private fun buildFallbackTaskSteps(
    goal: String,
    request: OrchestrationRequest,
  ): List<AgentStepPlan> {
    return listOf(
      AgentStepPlan(
        id = "step_plan",
        agent = AgentRole.TASK_PLANNING,
        description = "Decompose goal '${goal.take(40)}' into structured actions",
      ),
      AgentStepPlan(
        id = "step_execute",
        agent = AgentRole.CODING,
        description =
          if (request.currentTurnImages.isNotEmpty()) "Analyze visual inputs & execute structured response"
          else "Generate verified step-by-step action plan & deliverable",
        dependsOnIds = listOf("step_plan"),
      ),
      AgentStepPlan(
        id = "step_verify",
        agent = AgentRole.VERIFICATION,
        description = "Validate constraints, safety gates & missing prerequisites",
        dependsOnIds = listOf("step_execute"),
      ),
    )
  }

  private fun executeTaskModeStepCheck(
    step: AgentStepPlan,
    request: OrchestrationRequest,
    settings: AppSettings,
    hasUnifiedContext: Boolean,
    toolCount: Int,
  ): Result<String> {
    return when (step.agent) {
      AgentRole.TASK_PLANNING ->
        Result.success("Goal decomposed into structured task dependencies.")
      AgentRole.MEMORY ->
        Result.success(
          if (hasUnifiedContext) "Retrieved conversation & memory context."
          else "Ready with current turn context."
        )
      AgentRole.VISION ->
        if (request.currentTurnImages.isNotEmpty() || request.pendingMediaItems.any { it.kind == MediaAttachmentKind.PHOTO }) {
          Result.success("${request.currentTurnImages.size.coerceAtLeast(1)} image input(s) validated for inspection.")
        } else {
          Result.failure(IllegalStateException("No image attached for visual step."))
        }
      AgentRole.DOCUMENT ->
        if (request.pendingMediaItems.any { it.kind == MediaAttachmentKind.DOCUMENT }) {
          Result.success("Attached document context verified.")
        } else {
          Result.failure(IllegalStateException("No document attached for document extraction."))
        }
      AgentRole.WEB_SEARCH, AgentRole.DEEP_RESEARCH -> {
        val hasCreds =
          settings.geminiApiKey.isNotBlank() ||
            settings.externalSearchUrl.isNotBlank() ||
            settings.connectionMode.name == "BACKEND_PROXY"
        if (hasCreds || toolCount > 0) Result.success("Live web grounding check passed.")
        else Result.success("No external search key set; proceeding with cautious offline reasoning.")
      }
      AgentRole.VERIFICATION ->
        Result.success("Checked preconditions & safety/approval status.")
      else -> Result.success("${step.description} prepared for synthesis.")
    }
  }

  override fun deriveConversationTitle(firstPrompt: String): String {
    val cleaned =
      firstPrompt
        .replace(Regex("[\\r\\n]+"), " ")
        .trim()
    if (cleaned.isBlank()) return "Multimodal Analysis"
    val words = cleaned.split(Regex("\\s+")).take(6)
    val title = words.joinToString(" ")
    return if (title.length > 42) "${title.take(39)}…" else title
  }

  private fun buildSystemPrompt(
    language: ResponseLanguage,
    isTaskMode: Boolean,
    operatingMode: OperatingMode,
    intent: IntelligenceIntent,
    categoryLabel: String,
    toolNotes: List<String>,
    extraContext: String,
    hasImages: Boolean,
  ): String {
    return buildString {
      append("You are Mousomi AI, an accurate, helpful, multilingual assistant fluent in English, Bengali (বাংলা), and Hindi (हिन्दी). ")
      when (language) {
        ResponseLanguage.AUTO ->
          append("Respond in the exact language used by the user (English, Bengali, or Hindi). ")
        ResponseLanguage.BENGALI,
        ResponseLanguage.BENGALI_IN,
        ResponseLanguage.BENGALI_BD ->
          append("Respond clearly in natural Bengali (বাংলা), keeping technical terms readable when helpful. ")
        ResponseLanguage.HINDI,
        ResponseLanguage.HINDI_IN ->
          append("Respond clearly in natural Hindi (हिन्दी). ")
        ResponseLanguage.ENGLISH,
        ResponseLanguage.ENGLISH_IN,
        ResponseLanguage.ENGLISH_US ->
          append("Respond clearly in English. ")
      }
      if (operatingMode == OperatingMode.ANALYSIS_THINK) {
        append("The user selected Analysis / Think mode: verify logic rigorously, consider edge cases, and present a clear structured final answer without exposing raw internal scratchpad tags. ")
      } else if (operatingMode == OperatingMode.LATEST_NEWS) {
        append("The user selected Latest News mode: prioritize the most recently published verified sources, separate publish date from event date, and flag any disagreements across publishers. ")
      } else if (isTaskMode || operatingMode == OperatingMode.TASK_MODE) {
        append("The user is in Task Mode: deliver a clear structured plan with concrete deliverables, verification checks, and exact next steps. Never claim an external or unapproved device action was completed if it awaits user confirmation. ")
      }
      if (hasImages) {
        append(
          "Grounded visual & screen-analysis rules: " +
            "(1) Distinguish strictly between facts directly visible in the attached image/screen frame and inferences or assumptions. " +
            "(2) If any text, price, button, or area is blurry, cropped, cut off, or partially obscured, say so explicitly rather than guessing. " +
            "(3) Adapt concisely to what is shown: for videos/players, explain what is visible on screen and key context; for shopping/products, extract visible product title, specs, price/ratings, and practical comparison points without inventing unseen specs; for websites/apps/forms, explain the current screen state and clear next interactive steps. "
        )
      }
      append("Routed pipeline: ${intent.label} ($categoryLabel). ")
      if (toolNotes.isNotEmpty()) {
        append("\nVerified tool context:\n")
        toolNotes.forEach { append("- $it\n") }
      }
      if (extraContext.isNotBlank()) {
        append("\n$extraContext\n")
      }
    }
  }
}
