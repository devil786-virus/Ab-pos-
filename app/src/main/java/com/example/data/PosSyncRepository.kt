package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class PosSyncRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  private fun sanitizeDocId(raw: String, prefix: String): String {
    val clean = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
    return if (clean.isBlank()) "${prefix}_${System.currentTimeMillis()}" else clean.take(120)
  }

  suspend fun ensureUserProfile(
    userId: String,
    displayName: String?,
    email: String?,
  ): Result<Unit> {
    val path = "users/$userId"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
      val docRef = db.collection("users").document(uid)
      val existingSnap = docRef.get().await()
      if (!existingSnap.exists()) {
        val createMap =
          mapOf(
            "userId" to uid,
            "storeName" to "আবরণী বস্ত্রালয় (Avarani Bastralaya)",
            "displayName" to displayName?.take(100),
            "email" to email?.take(150),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ).filterValues { it != null }
        docRef.set(createMap).await()
      } else {
        val updateMap =
          mapOf(
            "displayName" to displayName?.take(100),
            "email" to email?.take(150),
            "updatedAt" to FieldValue.serverTimestamp(),
          ).filterValues { it != null }
        docRef.update(updateMap).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  fun observeProducts(userId: String): Flow<List<StoreProduct>> =
    observeCollection(
      userId = userId,
      collectionName = "products",
      clazz = StoreProduct::class.java,
    )

  fun observeCustomers(userId: String): Flow<List<StoreCustomer>> =
    observeCollection(
      userId = userId,
      collectionName = "customers",
      clazz = StoreCustomer::class.java,
    )

  fun observeBills(userId: String): Flow<List<StoreBill>> =
    observeCollection(
      userId = userId,
      collectionName = "bills",
      clazz = StoreBill::class.java,
    )

  fun observeExpenses(userId: String): Flow<List<StoreExpense>> =
    observeCollection(
      userId = userId,
      collectionName = "expenses",
      clazz = StoreExpense::class.java,
    )

  fun observeDuePayments(userId: String): Flow<List<StoreDuePayment>> =
    observeCollection(
      userId = userId,
      collectionName = "duePayments",
      clazz = StoreDuePayment::class.java,
    )

  private fun <T> observeCollection(
    userId: String,
    collectionName: String,
    clazz: Class<T>,
  ): Flow<List<T>> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Signed-in user changed; refusing cross-user read." }
    val path = "users/$uid/$collectionName"
    emitAll(
      db.collection("users")
        .document(uid)
        .collection(collectionName)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun syncPosSnapshotFromJson(userId: String, rawJson: String): Result<Int> {
    var currentPath = "users/$userId"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Signed-in user changed; refusing cross-user sync." }
      val root = JSONObject(rawJson)
      var syncedCount = 0

      // 1. Products
      val products = root.optJSONArray("products") ?: JSONArray()
      val existingProductIds =
        db.collection("users").document(uid).collection("products").get().await().documents.map {
          it.id
        }.toSet()
      val incomingProductIds = mutableSetOf<String>()
      for (i in 0 until products.length()) {
        val obj = products.getJSONObject(i)
        val docId = sanitizeDocId(obj.optString("id"), "p")
        incomingProductIds.add(docId)
        val docRef = db.collection("users").document(uid).collection("products").document(docId)
        currentPath = docRef.path
        val code = obj.optString("code", "").trim().ifEmpty { docId }.take(30)
        val nameBn = obj.optString("nameBn", "").trim().ifEmpty { obj.optString("nameEn", "Item") }.take(120)
        val nameEn = obj.optString("nameEn", "").trim().ifEmpty { nameBn }.take(120)
        val category = obj.optString("category", "").trim().take(60)
        val price = obj.optDouble("price", 0.0).coerceIn(0.0, 10_000_000.0)
        val stock = obj.optLong("stock", 0L).coerceIn(0L, 1_000_000L)
        if (existingProductIds.contains(docId)) {
          docRef
            .update(
              mapOf(
                "code" to code,
                "nameBn" to nameBn,
                "nameEn" to nameEn,
                "category" to category,
                "price" to price,
                "stock" to stock,
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            )
            .await()
        } else {
          docRef
            .set(
              mapOf(
                "userId" to uid,
                "code" to code,
                "nameBn" to nameBn,
                "nameEn" to nameEn,
                "category" to category,
                "price" to price,
                "stock" to stock,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            )
            .await()
        }
        syncedCount++
      }
      for (staleId in existingProductIds - incomingProductIds) {
        val docRef = db.collection("users").document(uid).collection("products").document(staleId)
        currentPath = docRef.path
        docRef.delete().await()
      }

      // 2. Customers
      val customers = root.optJSONArray("customers") ?: JSONArray()
      val existingCustomerIds =
        db.collection("users").document(uid).collection("customers").get().await().documents.map {
          it.id
        }.toSet()
      val incomingCustomerIds = mutableSetOf<String>()
      for (i in 0 until customers.length()) {
        val obj = customers.getJSONObject(i)
        val docId = sanitizeDocId(obj.optString("id"), "c")
        incomingCustomerIds.add(docId)
        val docRef = db.collection("users").document(uid).collection("customers").document(docId)
        currentPath = docRef.path
        val code = obj.optString("code", "").trim().ifEmpty { docId }.take(30)
        val name = obj.optString("name", "").trim().ifEmpty { "Customer" }.take(120)
        val phone = obj.optString("phone", "").trim().take(30)
        val address = obj.optString("address", "").trim().take(250)
        val due = obj.optDouble("due", 0.0).coerceIn(0.0, 100_000_000.0)
        if (existingCustomerIds.contains(docId)) {
          docRef
            .update(
              mapOf(
                "code" to code,
                "name" to name,
                "phone" to phone,
                "address" to address,
                "due" to due,
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            )
            .await()
        } else {
          docRef
            .set(
              mapOf(
                "userId" to uid,
                "code" to code,
                "name" to name,
                "phone" to phone,
                "address" to address,
                "due" to due,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            )
            .await()
        }
        syncedCount++
      }
      for (staleId in existingCustomerIds - incomingCustomerIds) {
        val docRef = db.collection("users").document(uid).collection("customers").document(staleId)
        currentPath = docRef.path
        docRef.delete().await()
      }

      // 3. Bills
      val bills = root.optJSONArray("bills") ?: JSONArray()
      val existingBillIds =
        db.collection("users").document(uid).collection("bills").get().await().documents.map {
          it.id
        }.toSet()
      for (i in 0 until bills.length()) {
        val obj = bills.getJSONObject(i)
        val docId = sanitizeDocId(obj.optString("id"), "b")
        if (existingBillIds.contains(docId)) continue
        val docRef = db.collection("users").document(uid).collection("bills").document(docId)
        currentPath = docRef.path
        val itemsArr = obj.optJSONArray("items") ?: JSONArray()
        var qtySum = 0L
        for (k in 0 until itemsArr.length()) {
          qtySum += itemsArr.getJSONObject(k).optLong("qty", 1L).coerceAtLeast(0L)
        }
        val customerId = obj.optString("customerId", "").takeIf { it.isNotBlank() && it != "null" }?.take(128)
        val customerCode = obj.optString("customerCode", "").takeIf { it.isNotBlank() && it != "null" }?.take(30)
        val payload =
          mapOf(
            "userId" to uid,
            "billNo" to obj.optLong("billNo", 1L).coerceIn(1L, 100_000_000L),
            "dateIso" to obj.optString("dateIso", "2026-01-01").take(10).padEnd(10, '0'),
            "customerId" to customerId,
            "customerCode" to customerCode,
            "customerName" to obj.optString("customerName", "Walk-in").ifBlank { "Walk-in" }.take(120),
            "itemCount" to qtySum.coerceIn(0L, 100_000L),
            "subtotal" to obj.optDouble("subtotal", 0.0).coerceIn(0.0, 100_000_000.0),
            "discount" to obj.optDouble("discount", 0.0).coerceIn(0.0, 100_000_000.0),
            "netBill" to obj.optDouble("netBill", 0.0).coerceIn(0.0, 100_000_000.0),
            "prevDue" to obj.optDouble("prevDue", 0.0).coerceIn(0.0, 100_000_000.0),
            "grandPayable" to obj.optDouble("grandPayable", 0.0).coerceIn(0.0, 100_000_000.0),
            "cashPaid" to obj.optDouble("cashPaid", 0.0).coerceIn(0.0, 100_000_000.0),
            "remainingDue" to obj.optDouble("remainingDue", 0.0).coerceIn(0.0, 100_000_000.0),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ).filterValues { it != null }
        docRef.set(payload).await()
        syncedCount++
      }

      // 4. Expenses
      val expenses = root.optJSONArray("expenses") ?: JSONArray()
      val existingExpenseIds =
        db.collection("users").document(uid).collection("expenses").get().await().documents.map {
          it.id
        }.toSet()
      val incomingExpenseIds = mutableSetOf<String>()
      for (i in 0 until expenses.length()) {
        val obj = expenses.getJSONObject(i)
        val docId = sanitizeDocId(obj.optString("id"), "ex")
        incomingExpenseIds.add(docId)
        if (existingExpenseIds.contains(docId)) continue
        val amt = obj.optDouble("amount", 0.0)
        if (amt <= 0.0) continue
        val docRef = db.collection("users").document(uid).collection("expenses").document(docId)
        currentPath = docRef.path
        docRef
          .set(
            mapOf(
              "userId" to uid,
              "dateIso" to obj.optString("dateIso", "2026-01-01").take(10).padEnd(10, '0'),
              "desc" to obj.optString("desc", "Expense").ifBlank { "Expense" }.take(200),
              "amount" to amt.coerceIn(0.01, 100_000_000.0),
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
        syncedCount++
      }
      for (staleId in existingExpenseIds - incomingExpenseIds) {
        val docRef = db.collection("users").document(uid).collection("expenses").document(staleId)
        currentPath = docRef.path
        docRef.delete().await()
      }

      // 5. Due Payments
      val duePayments = root.optJSONArray("duePayments") ?: JSONArray()
      val existingDueIds =
        db.collection("users").document(uid).collection("duePayments").get().await().documents.map {
          it.id
        }.toSet()
      for (i in 0 until duePayments.length()) {
        val obj = duePayments.getJSONObject(i)
        val docId = sanitizeDocId(obj.optString("id"), "dp")
        if (existingDueIds.contains(docId)) continue
        val amt = obj.optDouble("amount", 0.0)
        if (amt <= 0.0) continue
        val docRef = db.collection("users").document(uid).collection("duePayments").document(docId)
        currentPath = docRef.path
        val note = obj.optString("note", "").takeIf { it.isNotBlank() && it != "null" }?.take(200)
        val payload =
          mapOf(
            "userId" to uid,
            "dateIso" to obj.optString("dateIso", "2026-01-01").take(10).padEnd(10, '0'),
            "customerId" to obj.optString("customerId", "unknown").ifBlank { "unknown" }.take(128),
            "customerCode" to obj.optString("customerCode", "").take(30),
            "customerName" to obj.optString("customerName", "Customer").ifBlank { "Customer" }.take(120),
            "oldDue" to obj.optDouble("oldDue", 0.0).coerceIn(0.0, 100_000_000.0),
            "amount" to amt.coerceIn(0.01, 100_000_000.0),
            "newDue" to obj.optDouble("newDue", 0.0).coerceIn(0.0, 100_000_000.0),
            "note" to note,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ).filterValues { it != null }
        docRef.set(payload).await()
        syncedCount++
      }

      Result.success(syncedCount)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, currentPath)
      Result.failure(e)
    }
  }
}
