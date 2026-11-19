package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val storeName: String = "আবরণী বস্ত্রালয় (Avarani Bastralaya)",
  val displayName: String? = null,
  val email: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class StoreProduct(
  @DocumentId val id: String = "",
  val userId: String = "",
  val code: String = "",
  val nameBn: String = "",
  val nameEn: String = "",
  val category: String = "",
  val price: Double = 0.0,
  val stock: Long = 0L,
  val reorderPoint: Long? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class StoreCustomer(
  @DocumentId val id: String = "",
  val userId: String = "",
  val code: String = "",
  val name: String = "",
  val phone: String = "",
  val address: String = "",
  val due: Double = 0.0,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class StoreBill(
  @DocumentId val id: String = "",
  val userId: String = "",
  val billNo: Long = 0L,
  val dateIso: String = "",
  val customerId: String? = null,
  val customerCode: String? = null,
  val customerName: String = "",
  val itemCount: Long = 0L,
  val subtotal: Double = 0.0,
  val discount: Double = 0.0,
  val netBill: Double = 0.0,
  val prevDue: Double = 0.0,
  val grandPayable: Double = 0.0,
  val cashPaid: Double = 0.0,
  val remainingDue: Double = 0.0,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class StoreExpense(
  @DocumentId val id: String = "",
  val userId: String = "",
  val dateIso: String = "",
  val desc: String = "",
  val amount: Double = 0.0,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class StoreDuePayment(
  @DocumentId val id: String = "",
  val userId: String = "",
  val dateIso: String = "",
  val customerId: String = "",
  val customerCode: String = "",
  val customerName: String = "",
  val oldDue: Double = 0.0,
  val amount: Double = 0.0,
  val newDue: Double = 0.0,
  val note: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)
