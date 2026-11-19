package com.example.ai

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSheet(
  viewModel: AiAssistantViewModel,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val snapshot by viewModel.shopSnapshot.collectAsStateWithLifecycle()
  val isBn = snapshot.lang != "en"

  // 0 = Multi-turn Chatbot, 1 = Real-Time Voice Live (gemini-3.8-live)
  var activeMode by remember { mutableIntStateOf(0) }

  DisposableEffect(Unit) {
    onDispose { viewModel.stopVoiceConversation() }
  }

  ModalBottomSheet(
    onDismissRequest = {
      viewModel.stopVoiceConversation()
      onDismiss()
    },
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
              imageVector = if (activeMode == 0) Icons.Default.AutoAwesome else Icons.Default.GraphicEq,
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
                if (isBn)
                  "আজ: ₹${snapshot.todaySales.toInt()} | মাস লাভ: ₹${snapshot.monthProfit.toInt()} | বাকি: ₹${snapshot.totalMarketDue.toInt()}"
                else
                  "Today: ₹${snapshot.todaySales.toInt()} | Month Profit: ₹${snapshot.monthProfit.toInt()} | Dues: ₹${snapshot.totalMarketDue.toInt()}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Row {
          IconButton(
            onClick = {
              if (activeMode == 0) viewModel.clearConversation()
              else viewModel.clearVoiceTranscripts()
            },
            modifier = Modifier.testTag("ai_clear_button"),
          ) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = "Clear AI history",
            )
          }
          IconButton(
            onClick = {
              viewModel.stopVoiceConversation()
              onDismiss()
            },
            modifier = Modifier.testTag("ai_close_button"),
          ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close AI assistant")
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Mode Switcher: Multi-turn Chatbot vs Live Voice API
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
          selected = activeMode == 0,
          onClick = {
            if (activeMode != 0) {
              viewModel.stopVoiceConversation()
              activeMode = 0
            }
          },
          shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
          icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp)) },
          modifier = Modifier.testTag("ai_mode_chat"),
        ) {
          Text(if (isBn) "চ্যাটবট (Chat)" else "Multi-turn Chat")
        }
        SegmentedButton(
          selected = activeMode == 1,
          onClick = { activeMode = 1 },
          shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
          icon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp)) },
          modifier = Modifier.testTag("ai_mode_voice"),
        ) {
          Text(if (isBn) "ভয়েস লাইভ (Live)" else "Voice Live")
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      if (activeMode == 0) {
        MultiTurnChatSection(viewModel = viewModel, isBn = isBn)
      } else {
        LiveVoiceSection(viewModel = viewModel, isBn = isBn)
      }
    }
  }
}

@Composable
private fun MultiTurnChatSection(
  viewModel: AiAssistantViewModel,
  isBn: Boolean,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val messages by viewModel.messages.collectAsStateWithLifecycle()
  val selectedRole by viewModel.selectedRole.collectAsStateWithLifecycle()
  var inputText by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  val isBusy = uiState is AiUiState.Loading || uiState is AiUiState.Streaming

  LaunchedEffect(messages.size, (uiState as? AiUiState.Streaming)?.text?.length) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  Column(modifier = Modifier.fillMaxWidth()) {
    // Role & Model Selector Chips
    Text(
      text =
        if (isBn) "সহায়কের ভূমিকা ও মডেল (${selectedRole.modelId})"
        else "Assistant Role & Model (${selectedRole.modelId})",
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(vertical = 4.dp),
    ) {
      items(AiChatRole.entries) { role ->
        val selected = role == selectedRole
        FilterChip(
          selected = selected,
          onClick = { if (!isBusy) viewModel.selectRole(role) },
          label = {
            Text(
              text = if (isBn) role.titleBn else role.titleEn,
              maxLines = 1,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
          },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primary,
              selectedLabelColor = Color.White,
            ),
          enabled = !isBusy,
          modifier = Modifier.testTag("ai_role_${role.name.lowercase()}"),
        )
      }
    }

    // Quick Action Chips tailored to selected role
    val quickPrompts =
      remember(selectedRole, isBn) {
        when (selectedRole) {
          AiChatRole.GENERAL ->
            if (isBn)
              listOf(
                "📊 আজকের ব্যবসার সারাংশ" to "আজকের বিক্রি, নগদ ও খরচ নিয়ে সংক্ষেপে বলো।",
                "👗 আজ কোন পণ্য বেশি দেখাব?" to "স্টকে থাকা পণ্যগুলো থেকে আজ ক্রেতাদের কোনগুলো সাজেশন দেব?",
              )
            else
              listOf(
                "📊 Daily Overview" to "Summarize today's sales, cash collected, and expenses.",
                "👗 Customer Picks" to "Which in-stock saree or garment styles should I recommend today?",
              )
          AiChatRole.DEEP_REASONING ->
            if (isBn)
              listOf(
                "📈 মাসের লাভ ও মার্জিন বিশ্লেষণ" to "এই মাসের মোট বিক্রি, খরচ ও লাভ বিশ্লেষণ করে লাভ বাড়ানোর ৩টি গভীর পরামর্শ দাও।",
                "⚠️ রিস্টক ও বাকি ঝুঁকি" to "কম স্টক এবং বেশি বাকি থাকা কাস্টমারদের ঝুঁকি বিশ্লেষণ করো।",
              )
            else
              listOf(
                "📈 Monthly Profit Analysis" to "Analyze this month's sales, expenses, and profit margin with 3 deep strategic recommendations.",
                "⚠️ Stock & Credit Risk" to "Evaluate low-stock reorder priority and pending Khata credit exposure.",
              )
          AiChatRole.COMPLEX_INTERACTIVE ->
            if (isBn)
              listOf(
                "🎉 উৎসব সেল ক্যাম্পেইন" to "আসন্ন উৎসবের জন্য একটি ধাপে ধাপে সেল ও কম্বো অফার প্ল্যান তৈরি করো এবং আমাকে প্রশ্ন করে কাস্টমাইজ করো।",
                "🤝 বাকি আদায়ের কৌশল" to "বড় বাকি কাস্টমারদের সাথে ফলো-আপ করার ধাপে ধাপে পরিকল্পনা বানাও।",
              )
            else
              listOf(
                "🎉 Festive Sale Planner" to "Design an interactive step-by-step festive sale & bundle promotion plan for our shop.",
                "🤝 Due Recovery Plan" to "Create a multi-step interactive follow-up workflow for overdue Khata customers.",
              )
          AiChatRole.FAST ->
            if (isBn)
              listOf(
                "💬 বাকি তাগাদা SMS" to "বাকি টাকার জন্য ছোট ও ভদ্র একটি বাংলা WhatsApp মেসেজ দাও।",
                "🌐 বিল নোট অনুবাদ" to "Customer receipt thank-you note বাংলা ও ইংরেজিতে লিখে দাও।",
              )
            else
              listOf(
                "💬 Quick Due SMS" to "Write a short, polite Bengali + English WhatsApp due reminder.",
                "🌐 Quick Receipt Note" to "Give a crisp 1-line festive thank-you message for receipts.",
              )
        }
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

    Spacer(modifier = Modifier.height(6.dp))

    // Error banner if any
    val errorState = uiState as? AiUiState.Error
    if (errorState != null) {
      ErrorBanner(message = errorState.message, onDismiss = { viewModel.dismissError() })
    }

    // Scrollable Multi-turn Chat Thread
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .heightIn(min = 190.dp, max = 330.dp)
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
            modifier = Modifier.size(32.dp),
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text =
              if (isBn) "${selectedRole.titleBn} (${selectedRole.taskHintBn}) প্রস্তুত। কথা শুরু করুন!"
              else "${selectedRole.titleEn} (${selectedRole.taskHintEn}) ready. Start chatting!",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(
          state = listState,
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth().testTag("ai_chat_thread"),
        ) {
          items(messages, key = { it.id }) { msg ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
            ) {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (msg.isUser) MaterialTheme.colorScheme.primary else Color.White,
                tonalElevation = if (msg.isUser) 0.dp else 1.dp,
                modifier = Modifier.fillMaxWidth(0.9f),
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  if (!msg.isUser && msg.role != null) {
                    Text(
                      text = "${if (isBn) msg.role.titleBn else msg.role.titleEn} · ${msg.role.modelId}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                  }
                  Text(
                    text = msg.text + if (msg.isStreaming) " ▍" else "",
                    color = if (msg.isUser) Color.White else Color(0xFF1F2937),
                    style = MaterialTheme.typography.bodyMedium,
                  )
                }
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
                  text = if (isBn) "উত্তর তৈরি হচ্ছে..." else "Thinking...",
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

@Composable
private fun LiveVoiceSection(
  viewModel: AiAssistantViewModel,
  isBn: Boolean,
) {
  val context = LocalContext.current
  val liveState by viewModel.liveVoiceState.collectAsStateWithLifecycle()
  val transcripts by viewModel.liveTranscripts.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()

  var hasMicPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var micDenied by remember { mutableStateOf(false) }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasMicPermission = granted
      micDenied = !granted
      if (granted) {
        viewModel.startVoiceConversation()
      }
    }

  LaunchedEffect(transcripts.size) {
    if (transcripts.isNotEmpty()) {
      listState.animateScrollToItem(transcripts.size - 1)
    }
  }

  val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
  val pulseScale by
    infiniteTransition.animateFloat(
      initialValue = 1.0f,
      targetValue = 1.16f,
      animationSpec = infiniteRepeatable(animation = tween(750), repeatMode = RepeatMode.Reverse),
      label = "pulse_scale",
    )

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text =
          if (isBn) "রিয়েল-টাইম ভয়েস কথোপকথন · gemini-3.8-live (Live API)"
          else "Real-Time Voice Conversation · gemini-3.8-live (Live API)",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
      )
    }

    Spacer(modifier = Modifier.height(10.dp))

    if (micDenied) {
      ErrorBanner(
        message =
          if (isBn) "ভয়েস কথোপকথনের জন্য মাইক্রোফোন অনুমতি প্রয়োজন।"
          else "Microphone permission is required for real-time voice conversations.",
        onDismiss = { micDenied = false },
      )
    }

    val liveError = liveState as? LiveVoiceState.Error
    if (liveError != null) {
      ErrorBanner(message = liveError.message, onDismiss = { viewModel.dismissVoiceError() })
    }

    // Transcript Feed
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .heightIn(min = 170.dp, max = 260.dp)
          .background(Color(0xFFF3EFE8), RoundedCornerShape(14.dp))
          .padding(10.dp)
    ) {
      if (transcripts.isEmpty()) {
        Column(
          modifier = Modifier.align(Alignment.Center).padding(12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
            modifier = Modifier.size(34.dp),
          )
          Spacer(modifier = Modifier.height(6.dp))
          val emptyStatusText =
            when (liveState) {
              LiveVoiceState.Active ->
                if (isBn) "শুনছে... বাংলা বা ইংরেজিতে কথা বলুন।"
                else "Live & listening... Speak naturally in Bengali or English."
              LiveVoiceState.Connecting ->
                if (isBn) "gemini-3.8-live এর সাথে সংযোগ হচ্ছে..."
                else "Connecting to gemini-3.8-live..."
              else ->
                if (isBn) {
                  "সরাসরি কথা বলার জন্য নিচের 'ভয়েস শুরু করুন' বোতামে ট্যাপ করুন।"
                } else {
                  "Tap Start Voice Call below to speak directly with Gemini Live."
                }
            }
          Text(
            text = emptyStatusText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(
          state = listState,
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth().testTag("live_transcript_list"),
        ) {
          items(transcripts, key = { it.id }) { entry ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = if (entry.isUser) Arrangement.End else Arrangement.Start,
            ) {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (entry.isUser) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.fillMaxWidth(0.9f),
              ) {
                Text(
                  text = entry.text,
                  color = if (entry.isUser) Color.White else Color(0xFF1F2937),
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.padding(10.dp),
                )
              }
            }
          }
        }
      }
    }

    if (liveState == LiveVoiceState.Active) {
      Spacer(modifier = Modifier.height(8.dp))
      val voiceHints =
        if (isBn)
          listOf(
            "আজকের বিক্রি বলো" to "আজকের মোট বিক্রি ও নগদ কত হয়েছে?",
            "কম স্টক কোনগুলো?" to "কোন কোন পণ্যের স্টক কম আছে?",
          )
        else
          listOf(
            "Today's sales?" to "Give me a quick voice summary of today's sales.",
            "Low stock check" to "Which items need restocking right now?",
          )
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(voiceHints) { (lbl, prompt) ->
          AssistChip(
            onClick = { viewModel.sendVoiceTextPrompt(prompt) },
            label = { Text(lbl) },
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    val isActive = liveState == LiveVoiceState.Active
    val isConnecting = liveState == LiveVoiceState.Connecting

    Button(
      onClick = {
        if (isActive || isConnecting) {
          viewModel.stopVoiceConversation()
        } else if (hasMicPermission) {
          viewModel.startVoiceConversation()
        } else {
          micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
      },
      colors =
        ButtonDefaults.buttonColors(
          containerColor =
            if (isActive || isConnecting) Color(0xFFB91C1C) else MaterialTheme.colorScheme.primary,
          contentColor = Color.White,
        ),
      shape = RoundedCornerShape(999.dp),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
      modifier =
        Modifier
          .then(if (isActive) Modifier.scale(pulseScale) else Modifier)
          .height(54.dp)
          .testTag("live_voice_toggle_button"),
    ) {
      if (isConnecting) {
        CircularProgressIndicator(
          color = Color.White,
          strokeWidth = 2.dp,
          modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(if (isBn) "সংযোগ হচ্ছে... (বাতিল)" else "Connecting... (Cancel)")
      } else if (isActive) {
        Box(
          modifier = Modifier.size(10.dp).background(Color.White, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(Icons.Default.Stop, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (isBn) "ভয়েস কল বন্ধ করুন" else "End Voice Conversation")
      } else {
        Icon(Icons.Default.Mic, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (isBn) "ভয়েস কথোপকথন শুরু করুন" else "Start Live Voice Conversation")
      }
    }
  }
}

@Composable
private fun ErrorBanner(
  message: String,
  onDismiss: () -> Unit,
) {
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
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF991B1B),
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = onDismiss) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Dismiss error",
          tint = Color(0xFF991B1B),
        )
      }
    }
  }
}
