package com.example.data

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

sealed interface CloudUiState<out T> {
  data object Loading : CloudUiState<Nothing>

  data class Success<T>(val data: T) : CloudUiState<T>

  data class Error(val message: String) : CloudUiState<Nothing>
}

data class CloudPosBundle(
  val products: List<StoreProduct> = emptyList(),
  val customers: List<StoreCustomer> = emptyList(),
  val bills: List<StoreBill> = emptyList(),
  val expenses: List<StoreExpense> = emptyList(),
  val duePayments: List<StoreDuePayment> = emptyList(),
) {
  fun toPosRestoreJson(): String {
    val root = JSONObject()
    val prodArr = JSONArray()
    products.forEach { p ->
      prodArr.put(
        JSONObject().apply {
          put("id", p.id)
          put("code", p.code)
          put("nameBn", p.nameBn)
          put("nameEn", p.nameEn)
          put("category", p.category)
          put("price", p.price)
          put("stock", p.stock)
        }
      )
    }
    val custArr = JSONArray()
    customers.forEach { c ->
      custArr.put(
        JSONObject().apply {
          put("id", c.id)
          put("code", c.code)
          put("name", c.name)
          put("phone", c.phone)
          put("address", c.address)
          put("due", c.due)
        }
      )
    }
    val billArr = JSONArray()
    bills.forEach { b ->
      billArr.put(
        JSONObject().apply {
          put("id", b.id)
          put("billNo", b.billNo)
          put("dateIso", b.dateIso)
          put("timestamp", b.createdAt?.toDate()?.time ?: System.currentTimeMillis())
          put("customerId", b.customerId)
          put("customerCode", b.customerCode ?: "")
          put("customerName", b.customerName)
          put("items", JSONArray())
          put("subtotal", b.subtotal)
          put("discount", b.discount)
          put("netBill", b.netBill)
          put("prevDue", b.prevDue)
          put("grandPayable", b.grandPayable)
          put("cashPaid", b.cashPaid)
          put("remainingDue", b.remainingDue)
        }
      )
    }
    val expArr = JSONArray()
    expenses.forEach { ex ->
      expArr.put(
        JSONObject().apply {
          put("id", ex.id)
          put("dateIso", ex.dateIso)
          put("timestamp", ex.createdAt?.toDate()?.time ?: System.currentTimeMillis())
          put("desc", ex.desc)
          put("amount", ex.amount)
        }
      )
    }
    val dpArr = JSONArray()
    duePayments.forEach { dp ->
      dpArr.put(
        JSONObject().apply {
          put("id", dp.id)
          put("dateIso", dp.dateIso)
          put("timestamp", dp.createdAt?.toDate()?.time ?: System.currentTimeMillis())
          put("customerId", dp.customerId)
          put("customerCode", dp.customerCode)
          put("customerName", dp.customerName)
          put("oldDue", dp.oldDue)
          put("amount", dp.amount)
          put("newDue", dp.newDue)
          put("note", dp.note ?: "")
        }
      )
    }
    root.put("products", prodArr)
    root.put("customers", custArr)
    root.put("bills", billArr)
    root.put("expenses", expArr)
    root.put("duePayments", dpArr)
    return root.toString()
  }
}

sealed interface SyncActionState {
  data object Idle : SyncActionState

  data object Syncing : SyncActionState

  data class Done(val message: String) : SyncActionState

  data class Failed(val error: String) : SyncActionState
}

class PosSyncViewModel(
  private val repository: PosSyncRepository,
  private val currentUserId: String,
) : ViewModel() {

  val cloudState: StateFlow<CloudUiState<CloudPosBundle>> =
    combine(
        repository.observeProducts(currentUserId),
        repository.observeCustomers(currentUserId),
        repository.observeBills(currentUserId),
        repository.observeExpenses(currentUserId),
        repository.observeDuePayments(currentUserId),
      ) { products, customers, bills, expenses, duePayments ->
        CloudPosBundle(
          products = products,
          customers = customers,
          bills = bills,
          expenses = expenses,
          duePayments = duePayments,
        )
      }
      .map<CloudPosBundle, CloudUiState<CloudPosBundle>> { CloudUiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing Firestore store data", rootError)
        emit(CloudUiState.Error(rootError.message ?: "Failed to sync store data from Firestore"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = CloudUiState.Loading,
      )

  private val _syncActionState = MutableStateFlow<SyncActionState>(SyncActionState.Idle)
  val syncActionState: StateFlow<SyncActionState> = _syncActionState.asStateFlow()

  fun ensureOwnerProfile(displayName: String?, email: String?) {
    viewModelScope.launch {
      repository.ensureUserProfile(currentUserId, displayName, email)
    }
  }

  fun pushLocalPosDataToCloud(rawJson: String, quiet: Boolean = false) {
    viewModelScope.launch {
      if (!quiet) _syncActionState.value = SyncActionState.Syncing
      val result = repository.syncPosSnapshotFromJson(currentUserId, rawJson)
      result
        .onSuccess { count ->
          if (!quiet || count > 0) {
            _syncActionState.value = SyncActionState.Done("Synced $count records to cloud")
          }
        }
        .onFailure { err ->
          _syncActionState.value =
            SyncActionState.Failed(err.localizedMessage ?: "Cloud sync failed")
        }
    }
  }

  fun clearSyncStatus() {
    _syncActionState.value = SyncActionState.Idle
  }

  private companion object {
    const val TAG = "PosSyncVM"
  }
}
