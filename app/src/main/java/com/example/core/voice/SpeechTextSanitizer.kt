package com.example.core.voice

/**
 * Sanitizes markdown, code blocks, URLs, tables, and punctuation artifacts before TTS playback
 * so Google Female/Male voices read responses naturally without robotic symbols or awkward pauses.
 */
object SpeechTextSanitizer {

  private val FENCED_CODE_REGEX = Regex("```[\\s\\S]*?```")
  private val INLINE_CODE_REGEX = Regex("`([^`]+)`")
  private val MARKDOWN_LINK_REGEX = Regex("\\[([^\\]]+)]\\((https?://[^)]+|[^)]+)\\)")
  private val RAW_URL_REGEX = Regex("https?://[^\\s)]+")
  private val TABLE_DIVIDER_REGEX = Regex("^\\s*\\|?(?:\\s*:?-{2,}:?\\s*\\|)+\\s*$", RegexOption.MULTILINE)
  private val HEADER_PREFIX_REGEX = Regex("^\\s{0,3}#{1,6}\\s*", RegexOption.MULTILINE)
  private val BLOCKQUOTE_PREFIX_REGEX = Regex("^\\s*>+\\s?", RegexOption.MULTILINE)
  private val BULLET_PREFIX_REGEX = Regex("^\\s*(?:[-*•]|\\d+\\.)\\s+", RegexOption.MULTILINE)
  private val SYMBOL_NOISE_REGEX = Regex("[*_~`#|<>^{}\\[\\]\\\\=]+")
  private val MULTI_PUNCT_REGEX = Regex("([!?.]){2,}")
  private val MULTI_SPACE_REGEX = Regex("\\s+")

  fun sanitizeForSpeech(rawMarkdown: String): String {
    if (rawMarkdown.isBlank()) return ""
    var text = rawMarkdown
    text = text.replace(FENCED_CODE_REGEX, ". Code block omitted. ")
    text = text.replace(INLINE_CODE_REGEX, " $1 ")
    text = text.replace(MARKDOWN_LINK_REGEX, "$1")
    text = text.replace(RAW_URL_REGEX) { match ->
      val host =
        match.value
          .removePrefix("https://")
          .removePrefix("http://")
          .substringBefore("/")
          .removePrefix("www.")
      if (host.isNotBlank()) " link to $host " else " link "
    }
    text = text.replace(TABLE_DIVIDER_REGEX, " ")
    text = text.replace(HEADER_PREFIX_REGEX, "")
    text = text.replace(BLOCKQUOTE_PREFIX_REGEX, "")
    text = text.replace(BULLET_PREFIX_REGEX, ". ")
    text = text.replace(SYMBOL_NOISE_REGEX, " ")
    text = text.replace(MULTI_PUNCT_REGEX, "$1")
    text = text.replace(MULTI_SPACE_REGEX, " ").trim()
    return text.trimStart('.', ' ')
  }

  /**
   * Scores an Android TextToSpeech voice name/quality metadata for natural Google speech output
   * in the requested gender and locale, prioritizing local high-quality voices and avoiding
   * low-quality or robotic synthetic variants.
   */
  fun scoreVoiceCandidate(
    voiceName: String,
    localeLanguage: String,
    targetLanguage: String,
    desiredMale: Boolean,
    isNetworkRequired: Boolean,
    qualityScore: Int = 300,
    latencyScore: Int = 300,
  ): Int {
    if (!localeLanguage.equals(targetLanguage, ignoreCase = true)) return Int.MIN_VALUE

    val name = voiceName.lowercase()
    var score = 0

    // Prefer crisp local voices to eliminate network round-trip latency and choppiness
    if (!isNetworkRequired) score += 120 else score -= 35
    if (name.contains("local")) score += 45

    // Prefer Android VOICE_QUALITY_HIGH (400) / VERY_HIGH (500)
    score += (qualityScore / 5).coerceIn(0, 120)
    if (latencyScore <= 200) score += 20

    val looksMale =
      name.contains("male") ||
        name.contains("-m-") ||
        name.endsWith("-m") ||
        name.contains("_m_") ||
        name.contains("iom") ||
        name.contains("tpd") ||
        name.contains("gba") ||
        name.contains("iol") ||
        name.contains("sfg-m") ||
        name.contains("charon")

    val looksFemale =
      name.contains("female") ||
        name.contains("-f-") ||
        name.endsWith("-f") ||
        name.contains("_f_") ||
        name.contains("sfg") ||
        name.contains("iob") ||
        name.contains("tpf") ||
        name.contains("aoede") ||
        name.contains("neural")

    if (desiredMale) {
      if (looksMale) score += 180
      if (looksFemale && !looksMale) score -= 140
    } else {
      if (looksFemale) score += 180
      if (looksMale && !looksFemale) score -= 140
    }

    // Penalize legacy robotic pico/espeak tags if Google TTS voices are available
    if (name.contains("espeak") || name.contains("robot")) score -= 250
    return score
  }
}
