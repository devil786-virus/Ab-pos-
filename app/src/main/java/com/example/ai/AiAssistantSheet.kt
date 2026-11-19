package com.example.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSheet(
  viewModel: AiAssistantViewModel,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val messages by viewModel.messages.collectAsStateWithLifecycle()
  val snapshot by viewModel.shopSnapshot.collectAsStateWithLifecycle()
  var inputText by remember { mutableStateOf("") }
  val listState = rememberLazyListState()

  val isBn = snapshot.lang != "en"
  val isBusy = uiState is AiUiState.Loading || uiState is AiUiState.Streaming

  LaunchedEffect(messages.size, (uiState as? AiUiState.Streaming)?.text?.length) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.background,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
          ) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = "Gemini AI",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(8.dp).size(22.dp),
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = if (isBn) "আবরণী AI সহায়ক" else "Avarani AI Assistant",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text =
                if (isBn) "আজকের বিক্রি: ₹${snapshot.todaySales.toInt()} | বাকি: ₹${snapshot.totalMarketDue.toInt()}"
                else "Today Sales: ₹${snapshot.todaySales.toInt()} | Dues: ₹${snapshot.totalMarketDue.toInt()}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Row {
          IconButton(
            onClick = { viewModel.clearConversation() },
            modifier = Modifier.testTag("ai_clear_button"),
          ) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = "Clear AI chat",
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("ai_close_button"),
          ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close AI assistant")
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Quick Action Chips
      val quickPrompts =
        if (isBn) {
          listOf(
            "📊 আজকের হিসাব বিশ্লেষণ" to "আজকের বিক্রি, নগদ, খরচ ও কম স্টক পণ্যের সংক্ষিপ্ত বিশ্লেষণ ও পরামর্শ দাও।",
            "💬 বাকি তাগাদা মেসেজ" to "যাদের বাকি টাকা আছে তাদের জন্য ভদ্র ও সুন্দর একটি বাংলা WhatsApp/SMS মেসেজ লিখে দাও।",
            "👗 স্টক ও উৎসবের টিপস" to "খেজুরী ও নিমতলা বাজারের ক্রেতাদের জন্য সামনে কোন শাড়ি বা পোশাকের স্টক বাড়ানো ভালো হবে?",
          )
        } else {
          listOf(
            "📊 Daily Business Insights" to "Analyze today's sales, cash received, expenses, and low-stock items and give concise actionable tips.",
            "💬 Due Reminder SMS" to "Draft a polite WhatsApp/SMS payment reminder in English and Bengali for customers with pending dues.",
            "👗 Stock & Season Advice" to "Suggest trending sarees and garments to restock for Nimtala Bazar retail customers.",
          )
        }

      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
      ) {
        items(quickPrompts) { (label, prompt) ->
          AssistChip(
            onClick = { if (!isBusy) viewModel.sendPrompt(prompt) },
            label = { Text(label, maxLines = 1) },
            enabled = !isBusy,
            modifier = Modifier.testTag("ai_quick_chip"),
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Error banner if any
      val errorState = uiState as? AiUiState.Error
      if (errorState != null) {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("ai_error_banner"),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.ErrorOutline,
              contentDescription = "Error",
              tint = Color(0xFFB91C1C),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = errorState.message,
              style = MaterialTheme.typography.bodySmall,
              color = Color(0xFF991B1B),
              modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.dismissError() }) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss error",
                tint = Color(0xFF991B1B),
              )
            }
          }
        }
      }

      // Chat Messages Area
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = 200.dp, max = 360.dp)
            .background(Color(0xFFF3EFE8), RoundedCornerShape(14.dp))
            .padding(10.dp)
      ) {
        if (messages.isEmpty() && uiState !is AiUiState.Loading) {
          Column(
            modifier = Modifier.align(Alignment.Center).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
              modifier = Modifier.size(34.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text =
                if (isBn)
                  "উপরের বাটনে চাপ দিন অথবা আপনার দোকানের হিসাব, বাকি বা স্টক নিয়ে প্রশ্ন লিখুন।"
                else
                  "Tap a quick suggestion above or ask anything about today's sales, customer dues, or stock.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            items(messages, key = { it.id }) { msg ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
              ) {
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color =
                    if (msg.isUser) MaterialTheme.colorScheme.primary else Color.White,
                  tonalElevation = if (msg.isUser) 0.dp else 1.dp,
                  modifier = Modifier.fillMaxWidth(0.9f),
                ) {
                  Text(
                    text = msg.text + if (msg.isStreaming) " ▍" else "",
                    color = if (msg.isUser) Color.White else Color(0xFF1F2937),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(10.dp),
                  )
                }
              }
            }
            if (uiState is AiUiState.Loading) {
              item {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(4.dp),
                ) {
                  CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = if (isBn) "চিন্তা করছে..." else "Thinking...",
                    style = MaterialTheme.typography.bodySmall,
                  )
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Input Bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = inputText,
          onValueChange = { inputText = it },
          placeholder = {
            Text(if (isBn) "প্রশ্ন লিখুন (বাংলা / English)..." else "Ask Gemini in Bengali or English...")
          },
          maxLines = 3,
          enabled = !isBusy,
          modifier = Modifier.weight(1f).testTag("ai_prompt_input"),
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilledIconButton(
          onClick = {
            val q = inputText.trim()
            if (q.isNotEmpty() && !isBusy) {
              inputText = ""
              viewModel.sendPrompt(q)
            }
          },
          enabled = inputText.isNotBlank() && !isBusy,
          colors =
            IconButtonDefaults.filledIconButtonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = Color.White,
            ),
          modifier = Modifier.size(50.dp).testTag("ai_send_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Send prompt",
          )
        }
      }
    }
  }
}
