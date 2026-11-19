package com.example.ai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
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

data class AiChatMessage(
  val id: String,
  val isUser: Boolean,
  val text: String,
  val isStreaming: Boolean = false,
)

sealed interface AiUiState {
  data object Idle : AiUiState

  data object Loading : AiUiState

  data class Streaming(val text: String) : AiUiState

  data class Success(val text: String) : AiUiState

  data class Error(val message: String) : AiUiState
}

data class ShopSnapshot(
  val lang: String = "bn",
  val todaySales: Double = 0.0,
  val todayCashIn: Double = 0.0,
  val todayExpenses: Double = 0.0,
  val totalMarketDue: Double = 0.0,
  val lowStockSummary: String = "",
  val topDueSummary: String = "",
)

class AiAssistantViewModel : ViewModel() {
  private val model by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      systemInstruction =
        content {
          text(
            "You are a helpful, practical retail store assistant for 'আবরণী বস্ত্রালয়' (Avarani Bastralaya), " +
              "a traditional and modern clothing shop at Nimtala Bazar, Khejuri, Purba Medinipur (PIN: 721431). " +
              "Respond clearly, concisely, and warmly in the user's requested language (Bengali or English). " +
              "Use Indian Rupee (₹) formatting and practical retail advice suitable for rural/semi-urban West Bengal shopkeepers."
          )
        },
    )
  }

  private val _uiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
  val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

  private val _messages = MutableStateFlow<List<AiChatMessage>>(emptyList())
  val messages: StateFlow<List<AiChatMessage>> = _messages.asStateFlow()

  private val _shopSnapshot = MutableStateFlow(ShopSnapshot())
  val shopSnapshot: StateFlow<ShopSnapshot> = _shopSnapshot.asStateFlow()

  private var activeJob: Job? = null

  fun updateShopSnapshot(rawJson: String) {
    try {
      val obj = JSONObject(rawJson)
      _shopSnapshot.value =
        ShopSnapshot(
          lang = obj.optString("lang", "bn"),
          todaySales = obj.optDouble("todaySales", 0.0),
          todayCashIn = obj.optDouble("todayCashIn", 0.0),
          todayExpenses = obj.optDouble("todayExpenses", 0.0),
          totalMarketDue = obj.optDouble("totalMarketDue", 0.0),
          lowStockSummary = obj.optString("lowStockSummary", ""),
          topDueSummary = obj.optString("topDueSummary", ""),
        )
    } catch (e: Exception) {
      Log.w(TAG, "Failed to parse shop snapshot JSON", e)
    }
  }

  fun sendPrompt(userPrompt: String, includeShopContext: Boolean = true) {
    val cleanPrompt = userPrompt.trim()
    if (cleanPrompt.isEmpty()) return

    activeJob?.cancel()
    val userMsgId = "u_${System.currentTimeMillis()}"
    val modelMsgId = "m_${System.currentTimeMillis() + 1}"

    _messages.value = _messages.value + AiChatMessage(id = userMsgId, isUser = true, text = cleanPrompt)

    val fullPrompt =
      if (includeShopContext) {
        val snap = _shopSnapshot.value
        val preferredLang = if (snap.lang == "en") "English" else "Bengali (বাংলা)"
        buildString {
          appendLine("Store Snapshot:")
          appendLine("- Preferred UI Language: $preferredLang")
          appendLine("- Today's Sales: ₹${snap.todaySales}")
          appendLine("- Today's Cash Received: ₹${snap.todayCashIn}")
          appendLine("- Today's Shop Expenses: ₹${snap.todayExpenses}")
          appendLine("- Total Customer Dues Pending: ₹${snap.totalMarketDue}")
          if (snap.lowStockSummary.isNotBlank()) {
            appendLine("- Low Stock Items: ${snap.lowStockSummary}")
          }
          if (snap.topDueSummary.isNotBlank()) {
            appendLine("- Customers with Dues: ${snap.topDueSummary}")
          }
          appendLine()
          append("Shopkeeper Request: ")
          append(cleanPrompt)
        }
      } else {
        cleanPrompt
      }

    activeJob =
      viewModelScope.launch {
        _uiState.value = AiUiState.Loading
        val accumulated = StringBuilder()
        try {
          withTimeout(60_000L) {
            model.generateContentStream(fullPrompt).collect { chunk ->
              val chunkText = chunk.text.orEmpty()
              if (chunkText.isNotEmpty()) {
                accumulated.append(chunkText)
                val currentText = accumulated.toString()
                _uiState.value = AiUiState.Streaming(currentText)
                upsertModelMessage(modelMsgId, currentText, isStreaming = true)
              }
            }
          }
          val finalText = accumulated.toString()
          if (finalText.isNotBlank()) {
            upsertModelMessage(modelMsgId, finalText, isStreaming = false)
            _uiState.value = AiUiState.Success(finalText)
          } else {
            _uiState.value = AiUiState.Error("Gemini returned an empty response. Please try again.")
          }
        } catch (e: TimeoutCancellationException) {
          Log.e(TAG, "Gemini streaming timed out", e)
          removeStreamingPlaceholder(modelMsgId)
          _uiState.value =
            AiUiState.Error("Request timed out after 60 seconds. Check your internet connection and retry.")
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "Gemini generation failed", e)
          removeStreamingPlaceholder(modelMsgId)
          _uiState.value =
            AiUiState.Error(e.localizedMessage ?: "Failed to connect to Gemini AI.")
        }
      }
  }

  fun clearConversation() {
    activeJob?.cancel()
    _messages.value = emptyList()
    _uiState.value = AiUiState.Idle
  }

  fun dismissError() {
    if (_uiState.value is AiUiState.Error) {
      _uiState.value = AiUiState.Idle
    }
  }

  private fun upsertModelMessage(id: String, text: String, isStreaming: Boolean) {
    val current = _messages.value.toMutableList()
    val idx = current.indexOfFirst { it.id == id }
    val msg = AiChatMessage(id = id, isUser = false, text = text, isStreaming = isStreaming)
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
}
