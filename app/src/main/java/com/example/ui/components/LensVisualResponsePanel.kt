package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.model.LensAnalysisResult
import com.example.domain.model.LensAnnotatedComponent
import com.example.domain.model.LensCalloutColor
import com.example.domain.model.LensComparisonResult
import com.example.domain.model.LensFollowUpTurn
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import kotlin.math.roundToInt

@Composable
fun LensVisualResponsePanel(
  capturedFilePath: String?,
  isAnalyzing: Boolean,
  activeStageLabel: String,
  analysisResult: LensAnalysisResult?,
  selectedComponentId: String?,
  showAnnotations: Boolean,
  followUpTurns: List<LensFollowUpTurn>,
  isAnsweringFollowUp: Boolean,
  isComparing: Boolean,
  comparisonResult: LensComparisonResult?,
  errorMessage: String?,
  onSelectComponent: (String?) -> Unit,
  onToggleAnnotations: () -> Unit,
  onAskFollowUp: (String) -> Unit,
  onStartCompareSecondObject: () -> Unit,
  onDismissComparison: () -> Unit,
  onContinueInChat: (LensAnalysisResult) -> Unit,
  onScanAnother: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uriHandler = LocalUriHandler.current
  var followUpInput by remember { mutableStateOf("") }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 14.dp, vertical = 10.dp)
        .testTag("lens_visual_response_panel"),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // A. Original Captured Image with Interactive Colored Callout Pins & Highlights
    if (!capturedFilePath.isNullOrBlank()) {
      AnnotatedCaptureCard(
        filePath = capturedFilePath,
        components = analysisResult?.components.orEmpty(),
        selectedComponentId = selectedComponentId,
        showAnnotations = showAnnotations,
        isAnalyzing = isAnalyzing,
        onSelectComponent = onSelectComponent,
        onToggleAnnotations = onToggleAnnotations,
      )
    }

    if (isAnalyzing) {
      GlassSectionCard(borderColor = NeonCyan.copy(alpha = 0.55f)) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            color = NeonCyan,
            strokeWidth = 2.6.dp,
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Analyzing visual capture…",
              color = Color.White,
              fontFamily = DisplayFontFamily,
              fontWeight = FontWeight.SemiBold,
              fontSize = 15.sp,
            )
            Text(
              text = activeStageLabel.ifBlank { "Identifying object parts, context & verified web references" },
              color = Color.White.copy(alpha = 0.72f),
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
    }

    if (!errorMessage.isNullOrBlank()) {
      GlassSectionCard(borderColor = Color(0xFFEF4444).copy(alpha = 0.7f)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Color(0xFFF87171))
            Text(
              text = "Visual Analysis Issue",
              color = Color(0xFFFCA5A5),
              fontWeight = FontWeight.SemiBold,
            )
          }
          Text(
            text = errorMessage,
            color = Color.White.copy(alpha = 0.88f),
            style = MaterialTheme.typography.bodySmall,
          )
          OutlinedButton(
            onClick = onScanAnother,
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
          ) {
            Text("Back to Camera", color = NeonCyan)
          }
        }
      }
    }

    if (analysisResult != null) {
      // B. Identified Object Name, Category & Honest Confidence Indicator
      GlassSectionCard(
        borderColor =
          if (analysisResult.isUncertain) Color(0xFFF59E0B).copy(alpha = 0.65f)
          else NeonCyan.copy(alpha = 0.55f),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = analysisResult.identifiedName,
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                color = Color.White,
                modifier = Modifier.testTag("lens_identified_name"),
              )
              if (analysisResult.category.isNotBlank()) {
                Text(
                  text = "${analysisResult.category} • ${analysisResult.mode.label} Mode",
                  style = MaterialTheme.typography.labelMedium,
                  color = NeonCyan,
                )
              }
            }
            Surface(
              shape = CircleShape,
              color =
                if (analysisResult.isUncertain) Color(0xFFF59E0B).copy(alpha = 0.2f)
                else Color(0xFF22C55E).copy(alpha = 0.2f),
              border =
                BorderStroke(
                  1.dp,
                  if (analysisResult.isUncertain) Color(0xFFF59E0B) else Color(0xFF22C55E),
                ),
            ) {
              Text(
                text = analysisResult.confidenceLabel,
                color = if (analysisResult.isUncertain) Color(0xFFFCD34D) else Color(0xFF86EFAC),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              )
            }
          }

          if (analysisResult.isUncertain && analysisResult.uncertaintyReason.isNotBlank()) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFFF59E0B).copy(alpha = 0.14f),
              border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f)),
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                Text(
                  text = analysisResult.uncertaintyReason,
                  style = MaterialTheme.typography.bodySmall,
                  color = Color(0xFFFDE68A),
                )
              }
            }
          }

          // Action Strip: Continue in Chat | Compare 2nd Object | Scan Another
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = { onContinueInChat(analysisResult) },
              shape = CircleShape,
              colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
              modifier = Modifier.testTag("lens_continue_in_chat_button"),
            ) {
              Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Continue in Chat", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
              onClick = onStartCompareSecondObject,
              shape = CircleShape,
              border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.7f)),
              modifier = Modifier.testTag("lens_compare_button"),
            ) {
              Icon(Icons.Outlined.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFD8B4FE))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Compare", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
              onClick = onScanAnother,
              shape = CircleShape,
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
              modifier = Modifier.testTag("lens_scan_another_button"),
            ) {
              Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Scan Again", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
          }
        }
      }

      // Two-Object Side-by-Side Comparison Card (if active)
      if (isComparing) {
        GlassSectionCard(borderColor = NeonPurple.copy(alpha = 0.65f)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = NeonPurple, strokeWidth = 2.4.dp)
            Text("Comparing both objects side-by-side…", color = Color.White, style = MaterialTheme.typography.bodyMedium)
          }
        }
      } else if (comparisonResult != null) {
        ComparisonResultCard(
          comparison = comparisonResult,
          onDismiss = onDismissComparison,
        )
      }

      // C. Highlighted Components & Color-Coded Legend
      if (analysisResult.components.isNotEmpty()) {
        GlassSectionCard(borderColor = NeonPurple.copy(alpha = 0.45f)) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "Highlighted Components (Tap any pin or chip)",
              fontFamily = DisplayFontFamily,
              fontWeight = FontWeight.SemiBold,
              fontSize = 15.sp,
              color = Color.White,
            )

            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              analysisResult.components.forEach { comp ->
                val isSelected = comp.id == selectedComponentId
                val chipColor = Color(comp.colorCategory.hexArgb)
                Surface(
                  onClick = { onSelectComponent(if (isSelected) null else comp.id) },
                  shape = CircleShape,
                  color = if (isSelected) chipColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
                  border = BorderStroke(1.2.dp, chipColor.copy(alpha = if (isSelected) 1f else 0.6f)),
                  modifier = Modifier.testTag("lens_component_chip_${comp.number}"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Box(
                      modifier = Modifier.size(18.dp).clip(CircleShape).background(chipColor),
                      contentAlignment = Alignment.Center,
                    ) {
                      Text(
                        text = "${comp.number}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                      )
                    }
                    Text(
                      text = comp.title,
                      color = Color.White,
                      style = MaterialTheme.typography.labelMedium,
                      maxLines = 1,
                    )
                  }
                }
              }
            }

            val selected =
              analysisResult.components.firstOrNull { it.id == selectedComponentId }
                ?: analysisResult.components.first()
            val selectedColor = Color(selected.colorCategory.hexArgb)
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = selectedColor.copy(alpha = 0.13f),
              border = BorderStroke(1.dp, selectedColor.copy(alpha = 0.55f)),
              modifier = Modifier.fillMaxWidth().testTag("lens_selected_component_card"),
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    text = "${selected.number}. ${selected.title}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                  )
                  Surface(
                    shape = CircleShape,
                    color = selectedColor.copy(alpha = 0.25f),
                  ) {
                    Text(
                      text = selected.colorCategory.semanticRole,
                      color = Color.White,
                      style = MaterialTheme.typography.labelSmall,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                  }
                }
                Text(
                  text = selected.detailedExplanation.ifBlank { selected.shortExplanation },
                  color = Color.White.copy(alpha = 0.88f),
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }
        }
      }

      // Extracted / Translated Text (if applicable)
      if (analysisResult.extractedOrTranslatedText.isNotBlank()) {
        GlassSectionCard(borderColor = Color(0xFF3B82F6).copy(alpha = 0.55f)) {
          StructuredDetailBlock(
            icon = Icons.Outlined.TextFields,
            iconTint = Color(0xFF60A5FA),
            title = "Extracted & Translated Text",
            body = analysisResult.extractedOrTranslatedText,
          )
        }
      }

      // D. Detailed Explanation (Characteristics, Purpose, How It Works)
      GlassSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          if (analysisResult.visualCharacteristics.isNotBlank()) {
            StructuredDetailBlock(
              icon = Icons.Outlined.AutoAwesome,
              iconTint = NeonCyan,
              title = "Visual Characteristics",
              body = analysisResult.visualCharacteristics,
            )
          }
          if (analysisResult.purposeAndFunctionality.isNotBlank()) {
            StructuredDetailBlock(
              icon = Icons.Outlined.FactCheck,
              iconTint = Color(0xFF22C55E),
              title = "Purpose & Functionality",
              body = analysisResult.purposeAndFunctionality,
            )
          }
          if (analysisResult.howItWorks.isNotBlank()) {
            StructuredDetailBlock(
              icon = Icons.Outlined.SettingsSuggest,
              iconTint = Color(0xFF60A5FA),
              title = "How It Works",
              body = analysisResult.howItWorks,
            )
          }
          if (analysisResult.shoppingGuidance.isNotBlank()) {
            StructuredDetailBlock(
              icon = Icons.Outlined.ShoppingBag,
              iconTint = Color(0xFFC084FC),
              title = "Product & Buying Notes",
              body = analysisResult.shoppingGuidance,
            )
          }
        }
      }

      // E. Real Web Reference Images (Explicitly Separated from Captured Image)
      if (analysisResult.referenceImages.isNotEmpty()) {
        GlassSectionCard(borderColor = ElectricBlue.copy(alpha = 0.5f)) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(Icons.Outlined.Public, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
              Text(
                text = "Web Reference Images (External Sources)",
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color.White,
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              analysisResult.referenceImages.forEach { ref ->
                Surface(
                  onClick = { runCatching { uriHandler.openUri(ref.sourcePageUrl) } },
                  shape = RoundedCornerShape(16.dp),
                  color = Color(0xFF0A1022).copy(alpha = 0.9f),
                  border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                  modifier = Modifier.width(206.dp).testTag("lens_web_ref_card"),
                ) {
                  Column {
                    Box(modifier = Modifier.fillMaxWidth().height(118.dp)) {
                      AsyncImage(
                        model = ref.imageUrl,
                        contentDescription = ref.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                      )
                      Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                      ) {
                        Text(
                          text = "Web Reference",
                          color = NeonCyan,
                          fontSize = 10.sp,
                          fontWeight = FontWeight.SemiBold,
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                      }
                    }
                    Column(
                      modifier = Modifier.padding(10.dp),
                      verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Text(
                        text = ref.title,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      if (ref.snippet.isNotBlank()) {
                        Text(
                          text = ref.snippet,
                          color = Color.White.copy(alpha = 0.7f),
                          style = MaterialTheme.typography.bodySmall,
                          maxLines = 2,
                          overflow = TextOverflow.Ellipsis,
                        )
                      }
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                      ) {
                        Text(
                          text = ref.sourceWebsite,
                          color = NeonCyan,
                          style = MaterialTheme.typography.labelSmall,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          modifier = Modifier.weight(1f),
                        )
                        Icon(
                          imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                          contentDescription = "Open external link",
                          tint = NeonCyan,
                          modifier = Modifier.size(13.dp),
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      // F. Applications, Safety & Key Facts
      if (
        analysisResult.practicalApplications.isNotEmpty() ||
        analysisResult.interestingFacts.isNotEmpty() ||
        analysisResult.safetyInformation.isNotBlank()
      ) {
        GlassSectionCard {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (analysisResult.practicalApplications.isNotEmpty()) {
              BulletDetailSection(
                title = "Practical Applications",
                items = analysisResult.practicalApplications,
                accent = Color(0xFF22C55E),
              )
            }
            if (analysisResult.safetyInformation.isNotBlank()) {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFEF4444).copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.padding(10.dp),
                  verticalAlignment = Alignment.Top,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(Icons.Outlined.Shield, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
                  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Safety Information", color = Color(0xFFFCA5A5), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(analysisResult.safetyInformation, color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.bodySmall)
                  }
                }
              }
            }
            if (analysisResult.interestingFacts.isNotEmpty()) {
              BulletDetailSection(
                title = "Interesting Facts",
                items = analysisResult.interestingFacts,
                accent = Color(0xFFA855F7),
              )
            }
          }
        }
      }

      // G. Related Discoveries (Tappable Follow-Up Queries)
      if (analysisResult.relatedDiscoveries.isNotEmpty()) {
        GlassSectionCard {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(17.dp))
              Text("Related Discoveries", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            }
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              analysisResult.relatedDiscoveries.forEach { topic ->
                Surface(
                  onClick = { onAskFollowUp("Tell me more about: $topic") },
                  shape = CircleShape,
                  color = Color.White.copy(alpha = 0.08f),
                  border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.35f)),
                ) {
                  Text(
                    text = topic,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                  )
                }
              }
            }
          }
        }
      }

      // H. Verified Web Sources
      if (analysisResult.verifiedSources.isNotEmpty()) {
        GlassSectionCard {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "Verified Web Sources",
              color = Color.White,
              fontWeight = FontWeight.SemiBold,
              style = MaterialTheme.typography.titleSmall,
            )
            analysisResult.verifiedSources.forEach { src ->
              Surface(
                onClick = { runCatching { uriHandler.openUri(src.url) } },
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(src.title, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${src.sourceDomain} • ${src.verificationStatus.label}", color = NeonCyan, style = MaterialTheme.typography.labelSmall)
                  }
                  Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open source", tint = NeonCyan, modifier = Modifier.size(16.dp))
                }
              }
            }
          }
        }
      }

      // Follow-Up Multimodal Q&A Turns
      followUpTurns.forEach { turn ->
        GlassSectionCard(borderColor = ElectricBlue.copy(alpha = 0.45f)) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "You asked: ${turn.question}",
              color = NeonCyan,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
            )
            MarkdownMessageContent(
              markdown = turn.answerMarkdown,
              textColor = Color.White.copy(alpha = 0.92f),
            )
          }
        }
      }

      // Follow-up Input Composer Inside Lens
      GlassSectionCard(borderColor = NeonCyan.copy(alpha = 0.5f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = followUpInput,
            onValueChange = { followUpInput = it },
            placeholder = { Text("Ask a follow-up about this image…", color = Color.White.copy(alpha = 0.5f)) },
            singleLine = true,
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                cursorColor = NeonCyan,
              ),
            shape = CircleShape,
            modifier = Modifier.weight(1f).testTag("lens_follow_up_input"),
          )
          Surface(
            onClick = {
              if (followUpInput.isNotBlank() && !isAnsweringFollowUp) {
                val q = followUpInput.trim()
                followUpInput = ""
                onAskFollowUp(q)
              }
            },
            shape = CircleShape,
            color = ElectricBlue,
            modifier = Modifier.size(48.dp).testTag("lens_follow_up_send"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              if (isAnsweringFollowUp) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
              } else {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Ask Lens", tint = Color.White, modifier = Modifier.size(20.dp))
              }
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))
  }
}

@Composable
private fun AnnotatedCaptureCard(
  filePath: String,
  components: List<LensAnnotatedComponent>,
  selectedComponentId: String?,
  showAnnotations: Boolean,
  isAnalyzing: Boolean,
  onSelectComponent: (String?) -> Unit,
  onToggleAnnotations: () -> Unit,
) {
  val bitmap =
    remember(filePath) {
      runCatching { BitmapFactory.decodeFile(filePath) }.getOrNull()
    }
  val aspect =
    remember(bitmap) {
      if (bitmap != null && bitmap.height > 0) {
        (bitmap.width.toFloat() / bitmap.height.toFloat()).coerceIn(0.65f, 1.75f)
      } else {
        1.0f
      }
    }

  Surface(
    shape = RoundedCornerShape(22.dp),
    color = Color(0xFF0A0F1F),
    border = BorderStroke(1.2.dp, Brush.linearGradient(listOf(NeonPurple, ElectricBlue, NeonCyan))),
    modifier = Modifier.fillMaxWidth().testTag("lens_captured_image_card"),
  ) {
    Column {
      BoxWithConstraints(
        modifier =
          Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(Color.Black),
      ) {
        if (bitmap != null) {
          Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Your captured image",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        }

        // Colored highlight boxes & pointer rings drawn directly on Canvas
        if (showAnnotations && components.isNotEmpty()) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            components.forEach { comp ->
              val c = Color(comp.colorCategory.hexArgb)
              val isSelected = comp.id == selectedComponentId
              if (
                comp.normLeft != null &&
                comp.normTop != null &&
                comp.normRight != null &&
                comp.normBottom != null
              ) {
                val left = comp.normLeft * w
                val top = comp.normTop * h
                val boxW = ((comp.normRight - comp.normLeft) * w).coerceAtLeast(24f)
                val boxH = ((comp.normBottom - comp.normTop) * h).coerceAtLeast(24f)
                drawRoundRect(
                  color = c.copy(alpha = if (isSelected) 0.22f else 0.08f),
                  topLeft = Offset(left, top),
                  size = Size(boxW, boxH),
                  cornerRadius = CornerRadius(14f, 14f),
                )
                drawRoundRect(
                  color = c.copy(alpha = if (isSelected) 0.95f else 0.72f),
                  topLeft = Offset(left, top),
                  size = Size(boxW, boxH),
                  cornerRadius = CornerRadius(14f, 14f),
                  style =
                    Stroke(
                      width = if (isSelected) 4.5f else 2.5f,
                      pathEffect = if (isSelected) null else PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                    ),
                )
              }
              val px = comp.normX * w
              val py = comp.normY * h
              drawCircle(
                color = c.copy(alpha = if (isSelected) 0.35f else 0.2f),
                radius = if (isSelected) 32f else 22f,
                center = Offset(px, py),
              )
            }
          }

          // Interactive numbered callout pins
          val boxW = constraints.maxWidth.toFloat()
          val boxH = constraints.maxHeight.toFloat()
          val pinHalfPx = with(androidx.compose.ui.platform.LocalDensity.current) { 15.dp.toPx() }
          components.forEach { comp ->
            val pinColor = Color(comp.colorCategory.hexArgb)
            val isSelected = comp.id == selectedComponentId
            val xPx = (comp.normX * boxW - pinHalfPx).roundToInt().coerceIn(0, (boxW - pinHalfPx * 2).toInt().coerceAtLeast(0))
            val yPx = (comp.normY * boxH - pinHalfPx).roundToInt().coerceIn(0, (boxH - pinHalfPx * 2).toInt().coerceAtLeast(0))

            Box(
              modifier =
                Modifier
                  .offset { IntOffset(xPx, yPx) }
                  .size(30.dp)
                  .clip(CircleShape)
                  .background(pinColor)
                  .border(
                    width = if (isSelected) 2.5.dp else 1.5.dp,
                    color = Color.White,
                    shape = CircleShape,
                  )
                  .clickable { onSelectComponent(comp.id) }
                  .testTag("lens_callout_pin_${comp.number}"),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "${comp.number}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }

        // Top badge explicitly distinguishing User Capture
        Row(
          modifier = Modifier.fillMaxWidth().padding(10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
          ) {
            Text(
              text = if (isAnalyzing) "Your Capture • Scanning" else "Your Captured Image",
              color = Color.White,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
          }

          if (components.isNotEmpty()) {
            Surface(
              onClick = onToggleAnnotations,
              shape = CircleShape,
              color = Color.Black.copy(alpha = 0.72f),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
              modifier = Modifier.testTag("lens_toggle_annotations_button"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = if (showAnnotations) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                  contentDescription = "Toggle annotations",
                  tint = NeonCyan,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = if (showAnnotations) "Pins On" else "Pins Off",
                  color = Color.White,
                  style = MaterialTheme.typography.labelSmall,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ComparisonResultCard(
  comparison: LensComparisonResult,
  onDismiss: () -> Unit,
) {
  val bmp1 = remember(comparison.firstImagePath) { runCatching { BitmapFactory.decodeFile(comparison.firstImagePath) }.getOrNull() }
  val bmp2 = remember(comparison.secondImagePath) { runCatching { BitmapFactory.decodeFile(comparison.secondImagePath) }.getOrNull() }

  GlassSectionCard(borderColor = NeonPurple.copy(alpha = 0.7f)) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Side-by-Side Comparison",
          fontFamily = DisplayFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          color = Color.White,
        )
        Text(
          text = "Close",
          color = NeonCyan,
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.clickable(onClick = onDismiss),
        )
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
          if (bmp1 != null) {
            Image(
              bitmap = bmp1.asImageBitmap(),
              contentDescription = comparison.firstObjectName,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(12.dp)),
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(comparison.firstObjectName, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
          if (bmp2 != null) {
            Image(
              bitmap = bmp2.asImageBitmap(),
              contentDescription = comparison.secondObjectName,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(12.dp)),
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(comparison.secondObjectName, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }

      if (comparison.verdictSummary.isNotBlank()) {
        Text(comparison.verdictSummary, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
      }
      if (comparison.similarities.isNotEmpty()) {
        BulletDetailSection("Similarities", comparison.similarities, Color(0xFF22C55E))
      }
      if (comparison.differences.isNotEmpty()) {
        BulletDetailSection("Key Differences", comparison.differences, Color(0xFF06B6D4))
      }
    }
  }
}

@Composable
private fun GlassSectionCard(
  borderColor: Color = Color.White.copy(alpha = 0.16f),
  content: @Composable () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = Color(0xCC0C1224),
    border = BorderStroke(1.dp, borderColor),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(modifier = Modifier.padding(14.dp)) {
      content()
    }
  }
}

@Composable
private fun StructuredDetailBlock(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  iconTint: Color,
  title: String,
  body: String,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(17.dp))
      Text(
        text = title,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.titleSmall,
      )
    }
    Text(
      text = body,
      color = Color.White.copy(alpha = 0.86f),
      style = MaterialTheme.typography.bodySmall,
      lineHeight = 19.sp,
    )
  }
}

@Composable
private fun BulletDetailSection(
  title: String,
  items: List<String>,
  accent: Color,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
    items.forEach { item ->
      Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Box(
          modifier =
            Modifier
              .padding(top = 6.dp)
              .size(6.dp)
              .clip(CircleShape)
              .background(accent)
        )
        Text(
          text = item,
          color = Color.White.copy(alpha = 0.86f),
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}
