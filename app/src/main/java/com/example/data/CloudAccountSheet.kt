package com.example.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudAccountSheet(
  userEmail: String?,
  userName: String?,
  viewModel: PosSyncViewModel,
  onPushLocalToCloud: () -> Unit,
  onRestoreCloudToLocal: (String) -> Unit,
  onSignOut: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val cloudState by viewModel.cloudState.collectAsStateWithLifecycle()
  val syncAction by viewModel.syncActionState.collectAsStateWithLifecycle()

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.background,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.VerifiedUser,
            contentDescription = "Signed in account",
            tint = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = userName ?: "Store Owner",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = userEmail ?: "Google Account",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        IconButton(onClick = onDismiss, modifier = Modifier.testTag("cloud_sheet_close")) {
          Icon(imageVector = Icons.Default.Close, contentDescription = "Close cloud sync")
        }
      }

      when (val state = cloudState) {
        is CloudUiState.Loading -> {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Connecting to Firestore...", style = MaterialTheme.typography.bodySmall)
          }
        }
        is CloudUiState.Error -> {
          Surface(
            color = Color(0xFFFEF2F2),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("cloud_error_banner"),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Firestore error",
                tint = Color(0xFFB91C1C),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF991B1B),
              )
            }
          }
        }
        is CloudUiState.Success -> {
          val b = state.data
          Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF7ED)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = "Cloud Firestore Snapshot",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text =
                  "Products: ${b.products.size}  |  Customers: ${b.customers.size}  |  Bills: ${b.bills.size}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = "Expenses: ${b.expenses.size}  |  Due Payments: ${b.duePayments.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = onPushLocalToCloud,
              enabled = syncAction !is SyncActionState.Syncing,
              modifier = Modifier.weight(1f).height(48.dp).testTag("cloud_push_button"),
            ) {
              Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Push to Cloud")
            }
            OutlinedButton(
              onClick = { onRestoreCloudToLocal(b.toPosRestoreJson()) },
              enabled = syncAction !is SyncActionState.Syncing && (b.products.isNotEmpty() || b.customers.isNotEmpty()),
              modifier = Modifier.weight(1f).height(48.dp).testTag("cloud_pull_button"),
            ) {
              Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Load Cloud")
            }
          }
        }
      }

      when (val act = syncAction) {
        is SyncActionState.Syncing -> {
          Text("Syncing records to Firestore...", style = MaterialTheme.typography.bodySmall)
        }
        is SyncActionState.Done -> {
          Text(
            text = act.message,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF047857),
            fontWeight = FontWeight.Bold,
          )
        }
        is SyncActionState.Failed -> {
          Text(
            text = act.error,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFB91C1C),
          )
        }
        SyncActionState.Idle -> Unit
      }

      Spacer(modifier = Modifier.height(4.dp))

      OutlinedButton(
        onClick = onSignOut,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB91C1C)),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_button"),
      ) {
        Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
        Spacer(modifier = Modifier.width(8.dp))
        Text("Sign out", fontWeight = FontWeight.Bold)
      }
    }
  }
}
