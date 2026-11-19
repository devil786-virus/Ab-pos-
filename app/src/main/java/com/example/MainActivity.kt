package com.example

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.AiAssistantSheet
import com.example.ai.AiAssistantViewModel
import com.example.ai.configureAppCheck
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.CloudAccountSheet
import com.example.data.PosSyncRepository
import com.example.data.PosSyncViewModel
import com.example.ui.theme.CrimsonDark
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Configure Firebase App Check with launch intent before any UI, ViewModels, or Firebase calls
    configureAppCheck(this, intent)

    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(CrimsonDark.toArgb()),
      navigationBarStyle = SystemBarStyle.light(Color.White.toArgb(), Color.White.toArgb()),
    )
    setContent {
      MyApplicationTheme(darkTheme = false) {
        AppNavigation(onExitApp = { finish() })
      }
    }
  }
}

@Composable
fun AppNavigation(
  onExitApp: () -> Unit,
  auth: FirebaseAuth = Firebase.auth,
) {
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    key(user.uid) {
      val syncViewModel: PosSyncViewModel =
        viewModel(
          key = user.uid,
          factory =
            viewModelFactory {
              initializer {
                val app =
                  checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                  }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                PosSyncViewModel(PosSyncRepository(db), user.uid)
              }
            },
        )
      val aiViewModel: AiAssistantViewModel = viewModel(key = "ai_${user.uid}")

      LaunchedEffect(user.uid) {
        syncViewModel.ensureOwnerProfile(user.displayName, user.email)
      }

      PosWebHostScreen(
        userEmail = user.email,
        userName = user.displayName,
        syncViewModel = syncViewModel,
        aiViewModel = aiViewModel,
        onExitApp = onExitApp,
      )
    }
  }
}

class AndroidPosBridge(
  private val context: Context,
  private val getWebView: () -> WebView?,
  private val onRequestSaveBackup: (String, String) -> Unit,
  private val onRequestOpenRestore: () -> Unit,
  private val onRequestExportHtml: (String) -> Unit,
  private val onOpenAiAssistant: (String) -> Unit,
  private val onSyncPosData: (String) -> Unit,
  private val onOpenCloudSync: () -> Unit,
) {
  @JavascriptInterface
  fun printReceipt(jobName: String) {
    val webView = getWebView() ?: return
    webView.post {
      val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
      if (printManager == null) {
        Toast.makeText(context, "Print service unavailable", Toast.LENGTH_SHORT).show()
        return@post
      }
      val safeJob = jobName.ifBlank { "Avarani_Receipt" }
      val adapter = webView.createPrintDocumentAdapter(safeJob)
      val attrs =
        PrintAttributes.Builder()
          .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
          .build()
      printManager.print(safeJob, adapter, attrs)
    }
  }

  @JavascriptInterface
  fun saveJsonBackup(jsonContent: String, suggestedName: String) {
    onRequestSaveBackup(jsonContent, suggestedName)
  }

  @JavascriptInterface
  fun openJsonRestore() {
    onRequestOpenRestore()
  }

  @JavascriptInterface
  fun exportHtmlFile(suggestedName: String) {
    onRequestExportHtml(suggestedName)
  }

  @JavascriptInterface
  fun openAiAssistant(shopSnapshotJson: String) {
    onOpenAiAssistant(shopSnapshotJson)
  }

  @JavascriptInterface
  fun syncPosData(fullAppDataJson: String) {
    onSyncPosData(fullAppDataJson)
  }

  @JavascriptInterface
  fun openCloudSync() {
    onOpenCloudSync()
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PosWebHostScreen(
  userEmail: String?,
  userName: String?,
  syncViewModel: PosSyncViewModel,
  aiViewModel: AiAssistantViewModel,
  onExitApp: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var pendingJsonBackup by remember { mutableStateOf<String?>(null) }
  var showAiSheet by remember { mutableStateOf(false) }
  var showCloudSheet by remember { mutableStateOf(false) }

  val saveBackupLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
      val content = pendingJsonBackup
      pendingJsonBackup = null
      if (uri != null && content != null) {
        try {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
          }
          Toast.makeText(context, "Backup saved successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
          Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
      }
    }

  val openRestoreLauncher =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        try {
          val text =
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
              it.readText()
            }
          if (!text.isNullOrBlank()) {
            val quoted = JSONObject.quote(text)
            webViewRef?.post {
              webViewRef?.evaluateJavascript("window.__onAndroidJsonLoaded($quoted);", null)
            }
          }
        } catch (e: Exception) {
          Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
      }
    }

  val exportHtmlLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
      if (uri != null) {
        try {
          context.assets.open("index.html").use { input ->
            context.contentResolver.openOutputStream(uri)?.use { out -> input.copyTo(out) }
          }
          Toast.makeText(context, "Offline HTML saved successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
          Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
      }
    }

  BackHandler {
    if (showCloudSheet) {
      showCloudSheet = false
      return@BackHandler
    }
    if (showAiSheet) {
      showAiSheet = false
      return@BackHandler
    }
    val wv = webViewRef
    if (wv == null) {
      onExitApp()
    } else {
      wv.evaluateJavascript(
        "(function(){return window.__handleAndroidBack ? window.__handleAndroidBack() : false;})()"
      ) { result ->
        if (result != "true") {
          onExitApp()
        }
      }
    }
  }

  Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
    Spacer(
      modifier =
        Modifier.fillMaxWidth()
          .windowInsetsTopHeight(WindowInsets.statusBars)
          .background(CrimsonDark)
    )
    Box(modifier = Modifier.weight(1f).fillMaxWidth().imePadding()) {
      AndroidView(
        modifier = Modifier.fillMaxSize().testTag("pos_webview"),
        factory = { ctx ->
          WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            webViewClient = WebViewClient()
            webChromeClient =
              object : WebChromeClient() {
                override fun onJsConfirm(
                  view: WebView?,
                  url: String?,
                  message: String?,
                  result: JsResult?,
                ): Boolean {
                  AlertDialog.Builder(ctx)
                    .setMessage(message ?: "")
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                  return true
                }

                override fun onJsAlert(
                  view: WebView?,
                  url: String?,
                  message: String?,
                  result: JsResult?,
                ): Boolean {
                  AlertDialog.Builder(ctx)
                    .setMessage(message ?: "")
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                  return true
                }
              }

            addJavascriptInterface(
              AndroidPosBridge(
                context = ctx,
                getWebView = { webViewRef },
                onRequestSaveBackup = { json, name ->
                  post {
                    pendingJsonBackup = json
                    saveBackupLauncher.launch(name)
                  }
                },
                onRequestOpenRestore = {
                  post {
                    openRestoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                  }
                },
                onRequestExportHtml = { name ->
                  post { exportHtmlLauncher.launch(name) }
                },
                onOpenAiAssistant = { snapshotJson ->
                  post {
                    aiViewModel.updateShopSnapshot(snapshotJson)
                    showAiSheet = true
                  }
                },
                onSyncPosData = { fullJson ->
                  post { syncViewModel.pushLocalPosDataToCloud(fullJson, quiet = true) }
                },
                onOpenCloudSync = {
                  post {
                    syncViewModel.clearSyncStatus()
                    showCloudSheet = true
                  }
                },
              ),
              "AndroidPOS",
            )

            loadUrl("file:///android_asset/index.html")
            webViewRef = this
          }
        },
      )
    }
    Spacer(
      modifier =
        Modifier.fillMaxWidth()
          .windowInsetsBottomHeight(WindowInsets.navigationBars)
          .background(Color.White)
    )
  }

  if (showAiSheet) {
    AiAssistantSheet(
      viewModel = aiViewModel,
      onDismiss = { showAiSheet = false },
    )
  }

  if (showCloudSheet) {
    CloudAccountSheet(
      userEmail = userEmail,
      userName = userName,
      viewModel = syncViewModel,
      onPushLocalToCloud = {
        webViewRef?.evaluateJavascript("(function(){return JSON.stringify(appData);})();") { raw ->
          if (!raw.isNullOrBlank() && raw != "null") {
            val unquoted =
              try {
                org.json.JSONTokener(raw).nextValue()?.toString() ?: raw
              } catch (_: Exception) {
                raw
              }
            syncViewModel.pushLocalPosDataToCloud(unquoted, quiet = false)
          }
        }
      },
      onRestoreCloudToLocal = { cloudJson ->
        val quoted = JSONObject.quote(cloudJson)
        webViewRef?.evaluateJavascript("window.__applyCloudSync($quoted);", null)
        showCloudSheet = false
      },
      onSignOut = {
        showCloudSheet = false
        signOut(credentialManager = credentialManager, scope = scope)
      },
      onDismiss = { showCloudSheet = false },
    )
  }
}
