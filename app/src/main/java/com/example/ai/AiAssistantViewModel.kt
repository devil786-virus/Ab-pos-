package com.example.ai

import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveAudioConversationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiAssistantViewModel"

enum class AiChatRole(
  val modelId: String,
  val titleBn: String,
  val titleEn: String,
  val taskHintBn: String,
  val taskHintEn: String,
  val systemRoleInstruction: String,
) {
  GENERAL(
    modelId = "gemini-4-hydrogen",
    titleBn = "সাধারণ সহায়ক",
    titleEn = "General",
    taskHintBn = "সাধারণ ব্যবসা ও দোকান প্রশ্ন",
    taskHintEn = "General store tasks",
    systemRoleInstruction =
      "Role: General Retail Store Assistant for 'আবরণী বস্ত্রালয়' (Avarani Bastralaya), " +
        "Nimtala Bazar, Khejuri, Purba Medinipur. Give warm, clear, practical retail help for everyday billing, customer service, and store operations.",
  ),
  DEEP_REASONING(
    modelId = "gemini-4-carbon",
    titleBn = "গভীর বিশ্লেষক",
    titleEn = "Deep Reasoning",
    taskHintBn = "গভীর লাভ-ক্ষতি ও স্টক বিশ্লেষণ",
    taskHintEn = "Deep financial & stock analysis",
    systemRoleInstruction =
      "Role: Senior Retail Financial & Inventory Analyst for Avarani Bastralaya. " +
        "Perform deep step-by-step reasoning over daily & monthly sales, expenses, profit margins, Khata credit risk, and reorder prioritization with concrete numbers.",
  ),
  COMPLEX_INTERACTIVE(
    modelId = "gemini-4-neon",
    titleBn = "ইন্টারঅ্যাক্টিভ প্ল্যানার",
    titleEn = "Interactive",
    taskHintBn = "উৎসব সেল ও কাস্টমার প্ল্যান",
    taskHintEn = "Complex interactive workflows",
    systemRoleInstruction =
      "Role: Interactive Retail Campaign & Customer Workflow Strategist for Avarani Bastralaya. " +
        "Guide the shopkeeper interactively through multi-step seasonal sale campaigns (Durga Puja, Eid, Wedding), bundle pricing, and due recovery plans.",
  ),
  FAST(
    modelId = "gemini-3.1-flash-lite",
    titleBn = "দ্রুত সহায়ক",
    titleEn = "Fast",
    taskHintBn = "দ্রুত অনুবাদ, SMS ও হিসাব",
    taskHintEn = "Ultra-fast quick tasks",
    systemRoleInstruction =
      "Role: High-Speed Quick Helper for Avarani Bastralaya. " +
        "Keep answers ultra-concise, direct, and ready to copy-paste for quick SMS/WhatsApp due reminders, Bengali-English translations, and rapid bill checks.",
  ),
}

data class AiChatMessage(
  val id: String,
  val isUser: Boolean,
  val text: String,
  val isStreaming: Boolean = false,
  val role: AiChatRole? = null,
)

sealed interface AiUiState {
  data object Idle : AiUiState

  data object Loading : AiUiState

  data class Streaming(val text: String) : AiUiState

  data class Success(val text: String) : AiUiState

  data class Error(val message: String) : AiUiState
}

sealed interface LiveVoiceState {
  data object Idle : LiveVoiceState

  data object Connecting : LiveVoiceState

  data object Active : LiveVoiceState

  data class Error(val message: String) : LiveVoiceState
}

data class LiveTranscriptEntry(
  val id: String,
  val isUser: Boolean,
  val text: String,
)

data class ShopSnapshot(
  val lang: String = "bn",
  val todaySales: Double = 0.0,
  val todayCashIn: Double = 0.0,
  val todayExpenses: Double = 0.0,
  val monthSales: Double = 0.0,
  val monthExpenses: Double = 0.0,
  val monthProfit: Double = 0.0,
  val totalMarketDue: Double = 0.0,
  val lowStockSummary: String = "",
  val topDueSummary: String = "",
)

class AiAssistantViewModel : ViewModel() {

  private val modelsByRole: Map<AiChatRole, GenerativeModel> by lazy {
    AiChatRole.entries.associateWith { role ->
      Firebase.ai.generativeModel(
        modelName = role.modelId,
        systemInstruction =
          content {
            text(
              "${role.systemRoleInstruction} " +
                "Always use Indian Rupee (₹) formatting, respond in the user's preferred language (Bengali or English), " +
                "and keep advice practical for a West Bengal clothing shop."
            )
          },
      )
    }
  }

  @OptIn(PublicPreviewAPI::class)
  private val liveModel by lazy {
    Firebase.ai.liveModel(
      modelName = "gemini-3.8-live",
      generationConfig =
        liveGenerationConfig {
          responseModality = ResponseModality.AUDIO
          inputAudioTranscription = AudioTranscriptionConfig()
          outputAudioTranscription = AudioTranscriptionConfig()
        },
      systemInstruction =
        content {
          text(
            "You are the real-time voice assistant for 'আবরণী বস্ত্রালয়' (Avarani Bastralaya), " +
              "a saree and garment store at Nimtala Bazar, Khejuri, Purba Medinipur. " +
              "Speak naturally, warmly, and concisely in Bengali or English matching the speaker. " +
              "Use Indian Rupee (₹) amounts when discussing sales, expenses, profit, or customer Khata dues."
          )
        },
    )
  }

  private val _selectedRole = MutableStateFlow(AiChatRole.GENERAL)
  val selectedRole: StateFlow<AiChatRole> = _selectedRole.asStateFlow()

  private val _uiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
  val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

  private val _messages = MutableStateFlow<List<AiChatMessage>>(emptyList())
  val messages: StateFlow<List<AiChatMessage>> = _messages.asStateFlow()

  private val _shopSnapshot = MutableStateFlow(ShopSnapshot())
  val shopSnapshot: StateFlow<ShopSnapshot> = _shopSnapshot.asStateFlow()

  private val _liveVoiceState = MutableStateFlow<LiveVoiceState>(LiveVoiceState.Idle)
  val liveVoiceState: StateFlow<LiveVoiceState> = _liveVoiceState.asStateFlow()

  private val _liveTranscripts = MutableStateFlow<List<LiveTranscriptEntry>>(emptyList())
  val liveTranscripts: StateFlow<List<LiveTranscriptEntry>> = _liveTranscripts.asStateFlow()

  private var activeChatRole: AiChatRole? = null
  private var activeChatSession: Chat? = null
  private var activeJob: Job? = null

  @OptIn(PublicPreviewAPI::class)
  private var liveSession: LiveSession? = null
  private var liveJob: Job? = null

  fun updateShopSnapshot(rawJson: String) {
    try {
      val obj = JSONObject(rawJson)
      _shopSnapshot.value =
        ShopSnapshot(
          lang = obj.optString("lang", "bn"),
          todaySales = obj.optDouble("todaySales", 0.0),
          todayCashIn = obj.optDouble("todayCashIn", 0.0),
          todayExpenses = obj.optDouble("todayExpenses", 0.0),
          monthSales = obj.optDouble("monthSales", 0.0),
          monthExpenses = obj.optDouble("monthExpenses", 0.0),
          monthProfit = obj.optDouble("monthProfit", 0.0),
          totalMarketDue = obj.optDouble("totalMarketDue", 0.0),
          lowStockSummary = obj.optString("lowStockSummary", ""),
          topDueSummary = obj.optString("topDueSummary", ""),
        )
    } catch (e: Exception) {
      Log.w(TAG, "Failed to parse shop snapshot JSON", e)
    }
  }

  fun selectRole(role: AiChatRole) {
    if (_selectedRole.value == role) return
    _selectedRole.value = role
    // Rebuild chat session on next send with the preserved conversation history
    activeChatSession = null
    activeChatRole = null
  }

  private fun getOrCreateChat(role: AiChatRole): Chat {
    val existing = activeChatSession
    if (existing != null && activeChatRole == role) {
      return existing
    }
    val model = modelsByRole.getValue(role)
    val priorHistory: List<Content> =
      _messages.value
        .filter { !it.isStreaming && it.text.isNotBlank() }
        .map { msg -> content(role = if (msg.isUser) "user" else "model") { text(msg.text) } }
    return model.startChat(history = priorHistory).also {
      activeChatSession = it
      activeChatRole = role
    }
  }

  private fun buildSnapshotContextBlock(): String {
    val snap = _shopSnapshot.value
    val preferredLang = if (snap.lang == "en") "English" else "Bengali (বাংলা)"
    return buildString {
      appendLine("[Live POS Store Context]")
      appendLine("- Preferred Language: $preferredLang")
      appendLine("- Today Sales: ₹${snap.todaySales}, Cash In: ₹${snap.todayCashIn}, Expenses: ₹${snap.todayExpenses}")
      appendLine("- This Month Sales: ₹${snap.monthSales}, Expenses: ₹${snap.monthExpenses}, Profit: ₹${snap.monthProfit}")
      appendLine("- Total Pending Customer Khata Dues: ₹${snap.totalMarketDue}")
      if (snap.lowStockSummary.isNotBlank()) {
        appendLine("- Low Stock Items: ${snap.lowStockSummary}")
      }
      if (snap.topDueSummary.isNotBlank()) {
        appendLine("- Customers with Dues: ${snap.topDueSummary}")
      }
    }
  }

  fun sendPrompt(userPrompt: String, includeShopContext: Boolean = true) {
    val cleanPrompt = userPrompt.trim()
    if (cleanPrompt.isEmpty()) return

    activeJob?.cancel()
    val role = _selectedRole.value
    val isFirstTurn = _messages.value.isEmpty()
    val userMsgId = "u_${System.currentTimeMillis()}"
    val modelMsgId = "m_${System.currentTimeMillis() + 1}"

    val outgoingText =
      if (includeShopContext && isFirstTurn) {
        "${buildSnapshotContextBlock()}\nShopkeeper Message: $cleanPrompt"
      } else {
        cleanPrompt
      }

    _messages.value =
      _messages.value + AiChatMessage(id = userMsgId, isUser = true, text = cleanPrompt)

    activeJob =
      viewModelScope.launch {
        _uiState.value = AiUiState.Loading
        val accumulated = StringBuilder()
        try {
          val chat = getOrCreateChat(role)
          withTimeout(60_000L) {
            chat.sendMessageStream(outgoingText).collect { chunk ->
              val chunkText = chunk.text.orEmpty()
              if (chunkText.isNotEmpty()) {
                accumulated.append(chunkText)
                val currentText = accumulated.toString()
                _uiState.value = AiUiState.Streaming(currentText)
                upsertModelMessage(modelMsgId, currentText, isStreaming = true, role = role)
              }
            }
          }
          val finalText = accumulated.toString()
          if (finalText.isNotBlank()) {
            upsertModelMessage(modelMsgId, finalText, isStreaming = false, role = role)
            _uiState.value = AiUiState.Success(finalText)
          } else {
            removeStreamingPlaceholder(modelMsgId)
            _uiState.value = AiUiState.Error("Gemini returned an empty response. Please try again.")
          }
        } catch (e: TimeoutCancellationException) {
          Log.e(TAG, "Gemini multi-turn streaming timed out", e)
          removeStreamingPlaceholder(modelMsgId)
          _uiState.value =
            AiUiState.Error("Request timed out after 60 seconds. Check your internet connection and retry.")
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "Gemini chat generation failed", e)
          removeStreamingPlaceholder(modelMsgId)
          _uiState.value =
            AiUiState.Error(e.localizedMessage ?: "Failed to connect to Gemini AI.")
        }
      }
  }

  fun clearConversation() {
    activeJob?.cancel()
    activeChatSession = null
    activeChatRole = null
    _messages.value = emptyList()
    _uiState.value = AiUiState.Idle
  }

  fun dismissError() {
    if (_uiState.value is AiUiState.Error) {
      _uiState.value = AiUiState.Idle
    }
  }

  @SuppressLint("MissingPermission")
  @OptIn(PublicPreviewAPI::class)
  fun startVoiceConversation() {
    if (_liveVoiceState.value == LiveVoiceState.Connecting || _liveVoiceState.value == LiveVoiceState.Active) {
      return
    }
    liveJob?.cancel()
    liveJob =
      viewModelScope.launch {
        _liveVoiceState.value = LiveVoiceState.Connecting
        try {
          val session = withTimeout(30_000L) { liveModel.connect() }
          liveSession = session

          // Seed current shop stats silently before user begins speaking
          runCatching {
            session.send(
              content { text("Store context for this voice session:\n${buildSnapshotContextBlock()}") },
              turnComplete = false,
            )
          }

          val config =
            liveAudioConversationConfig {
              enableInterruptions = true
              transcriptHandler = { inputTranscription, outputTranscription ->
                val userSpoken = inputTranscription?.text?.trim().orEmpty()
                val modelSpoken = outputTranscription?.text?.trim().orEmpty()
                if (userSpoken.isNotEmpty()) {
                  appendLiveTranscript(isUser = true, chunk = userSpoken)
                }
                if (modelSpoken.isNotEmpty()) {
                  appendLiveTranscript(isUser = false, chunk = modelSpoken)
                }
              }
              goAwayHandler = {
                Log.w(TAG, "Live API session go-away received")
                appendLiveTranscript(
                  isUser = false,
                  chunk = " [Session ending soon — tap Start to reconnect]",
                )
              }
            }

          _liveVoiceState.value = LiveVoiceState.Active
          session.startAudioConversation(config)
        } catch (e: TimeoutCancellationException) {
          Log.e(TAG, "Gemini Live API connection timed out", e)
          cleanupLiveSession()
          _liveVoiceState.value =
            LiveVoiceState.Error("Voice connection timed out. Please check your network and try again.")
        } catch (e: CancellationException) {
          cleanupLiveSession()
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "Gemini Live API voice conversation failed", e)
          cleanupLiveSession()
          _liveVoiceState.value =
            LiveVoiceState.Error(e.localizedMessage ?: "Failed to start Live voice conversation.")
        }
      }
  }

  @OptIn(PublicPreviewAPI::class)
  fun sendVoiceTextPrompt(prompt: String) {
    val session = liveSession ?: return
    val clean = prompt.trim()
    if (clean.isEmpty() || _liveVoiceState.value != LiveVoiceState.Active) return
    appendLiveTranscript(isUser = true, chunk = clean)
    viewModelScope.launch {
      try {
        session.send(clean, turnComplete = true)
      } catch (e: Exception) {
        Log.e(TAG, "Failed sending text turn to LiveSession", e)
      }
    }
  }

  @OptIn(PublicPreviewAPI::class)
  fun stopVoiceConversation() {
    liveJob?.cancel()
    liveJob = null
    viewModelScope.launch {
      cleanupLiveSession()
      _liveVoiceState.value = LiveVoiceState.Idle
    }
  }

  fun clearVoiceTranscripts() {
    _liveTranscripts.value = emptyList()
  }

  fun dismissVoiceError() {
    if (_liveVoiceState.value is LiveVoiceState.Error) {
      _liveVoiceState.value = LiveVoiceState.Idle
    }
  }

  @OptIn(PublicPreviewAPI::class)
  private suspend fun cleanupLiveSession() {
    val session = liveSession
    liveSession = null
    if (session != null) {
      runCatching { session.stopAudioConversation() }
      runCatching { session.close() }
    }
  }

  private fun appendLiveTranscript(isUser: Boolean, chunk: String) {
    val current = _liveTranscripts.value.toMutableList()
    val last = current.lastOrNull()
    if (last != null && last.isUser == isUser) {
      val joined =
        if (last.text.endsWith(" ") || chunk.startsWith(" ")) last.text + chunk
        else "${last.text} $chunk"
      current[current.lastIndex] = last.copy(text = joined.trim())
    } else {
      current.add(
        LiveTranscriptEntry(
          id = "lt_${System.nanoTime()}",
          isUser = isUser,
          text = chunk.trim(),
        )
      )
    }
    _liveTranscripts.value = current
  }

  private fun upsertModelMessage(
    id: String,
    text: String,
    isStreaming: Boolean,
    role: AiChatRole,
  ) {
    val current = _messages.value.toMutableList()
    val idx = current.indexOfFirst { it.id == id }
    val msg = AiChatMessage(id = id, isUser = false, text = text, isStreaming = isStreaming, role = role)
    if (idx >= 0) {
      current[idx] = msg
    } else {
      current.add(msg)
    }
    _messages.value = current
  }

  private fun removeStreamingPlaceholder(id: String) {
    _messages.value = _messages.value.filterNot { it.id == id && it.isStreaming }
  }

  @OptIn(PublicPreviewAPI::class)
  override fun onCleared() {
    activeJob?.cancel()
    liveJob?.cancel()
    val session = liveSession
    liveSession = null
    if (session != null) {
      runCatching { session.stopAudioConversation() }
    }
    super.onCleared()
  }
}
