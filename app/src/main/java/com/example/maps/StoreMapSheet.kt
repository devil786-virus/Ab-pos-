package com.example.maps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.text.DecimalFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray

// Avarani Bastralaya, Nimtala Bazar, Khejuri, Purba Medinipur - 721431
val STORE_LAT_LNG = LatLng(21.8698, 87.9154)

data class CustomerMapPin(
  val id: String,
  val code: String,
  val name: String,
  val phone: String,
  val address: String,
  val due: Double,
  val position: LatLng,
  val isApproximate: Boolean,
)

object StoreMapHelper {
  private val moneyFormat = DecimalFormat("#,##0.##")

  fun hasValidMapsApiKey(apiKey: String = BuildConfig.MAPS_API_KEY): Boolean {
    val trimmed = apiKey.trim()
    return trimmed.isNotEmpty() && !trimmed.startsWith("YOUR_")
  }

  fun parseCustomersJson(customersJson: String): List<CustomerMapPin> {
    if (customersJson.isBlank()) return emptyList()
    val arr =
      try {
        JSONArray(customersJson)
      } catch (_: Exception) {
        return emptyList()
      }
    return buildList {
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val id = obj.optString("id", "c_$i")
        val code = obj.optString("code", "").trim()
        val name = obj.optString("name", "Customer").trim()
        val phone = obj.optString("phone", "").trim()
        val address = obj.optString("address", "").trim()
        val due = obj.optDouble("due", 0.0)
        val fallback = fallbackLatLngForAddress(address, i)
        add(
          CustomerMapPin(
            id = id,
            code = code,
            name = name,
            phone = phone,
            address = address.ifBlank { "Nimtala Bazar, Khejuri" },
            due = due,
            position = fallback,
            isApproximate = true,
          )
        )
      }
    }
  }

  fun fallbackLatLngForAddress(address: String, index: Int): LatLng {
    val lower = address.lowercase(Locale.US)
    return when {
      "nimtala" in lower -> LatLng(21.8712, 87.9168)
      "bus stand" in lower -> LatLng(21.8665, 87.9122)
      "heroya" in lower -> LatLng(21.8835, 87.9340)
      "khejuri" in lower -> LatLng(21.8620, 87.9210)
      "janka" in lower -> LatLng(21.8940, 87.9020)
      else -> {
        val angle = (index + 1) * 1.15
        val radiusDeg = 0.008 + ((index % 4) * 0.004)
        LatLng(
          STORE_LAT_LNG.latitude + radiusDeg * sin(angle),
          STORE_LAT_LNG.longitude + radiusDeg * cos(angle),
        )
      }
    }
  }

  fun distanceKm(from: LatLng, to: LatLng): Double {
    val results = FloatArray(1)
    Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
    return results[0] / 1000.0
  }

  fun formatMoney(amount: Double): String = "₹${moneyFormat.format(amount)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreMapSheet(
  customersJson: String,
  lang: String,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val isBn = lang != "en"
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var pins by remember(customersJson) { mutableStateOf(StoreMapHelper.parseCustomersJson(customersJson)) }
  var dueOnlyFilter by remember { mutableStateOf(false) }
  var mapType by remember { mutableStateOf(MapType.NORMAL) }
  var selectedPin by remember(customersJson) { mutableStateOf<CustomerMapPin?>(null) }

  var hasLocationPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
          PackageManager.PERMISSION_GRANTED
    )
  }

  val locationLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
      hasLocationPermission =
        result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
          result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

  // Geocode customer addresses asynchronously on IO
  LaunchedEffect(customersJson) {
    val initial = StoreMapHelper.parseCustomersJson(customersJson)
    val resolved =
      withContext(Dispatchers.IO) {
        initial.map { pin ->
          val geo = geocodeAddressInWestBengal(context, pin.address)
          if (geo != null) pin.copy(position = geo, isApproximate = false) else pin
        }
      }
    pins = resolved
    if (selectedPin == null) {
      selectedPin = resolved.firstOrNull { it.due > 0.0 } ?: resolved.firstOrNull()
    }
  }

  val visiblePins =
    remember(pins, dueOnlyFilter) {
      if (dueOnlyFilter) pins.filter { it.due > 0.0 } else pins
    }

  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(STORE_LAT_LNG, 13.5f)
  }

  val hasApiKey = remember { StoreMapHelper.hasValidMapsApiKey() }

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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
          ) {
            Icon(
              imageVector = Icons.Default.LocationOn,
              contentDescription = "Google Maps",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(8.dp).size(22.dp),
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = if (isBn) "দোকান ও কাস্টমার ম্যাপ" else "Store & Customer Map",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "Nimtala Bazar, Khejuri - 721431",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Row {
          IconButton(
            onClick = {
              scope.launch {
                cameraPositionState.animate(
                  CameraUpdateFactory.newLatLngZoom(STORE_LAT_LNG, 14f)
                )
              }
            },
            modifier = Modifier.testTag("map_center_store_button"),
          ) {
            Icon(Icons.Default.Storefront, contentDescription = "Center on store")
          }
          IconButton(
            onClick = {
              if (!hasLocationPermission) {
                locationLauncher.launch(
                  arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                  )
                )
              }
            },
            modifier = Modifier.testTag("map_my_location_button"),
          ) {
            Icon(
              Icons.Default.MyLocation,
              contentDescription = "My location",
              tint =
                if (hasLocationPermission) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("map_close_button"),
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close map")
          }
        }
      }

      if (!hasApiKey) {
        Spacer(modifier = Modifier.height(6.dp))
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().testTag("map_missing_key_banner"),
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.WarningAmber,
              contentDescription = null,
              tint = Color(0xFF92400E),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text =
                if (isBn)
                  "মানচিত্র টাইলস লোড করতে AI Studio Secrets-এ MAPS_API_KEY যোগ করুন। নিচের ডিরেকশন বাটন সবসময় কাজ করবে।"
                else
                  "Set MAPS_API_KEY in AI Studio Secrets to render Google Maps tiles. Customer list & Directions work immediately.",
              style = MaterialTheme.typography.bodySmall,
              color = Color(0xFF78350F),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Filters & Map Type Chips
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
          FilterChip(
            selected = !dueOnlyFilter,
            onClick = { dueOnlyFilter = false },
            label = { Text(if (isBn) "সবাই (${pins.size})" else "All (${pins.size})") },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = Color.White,
              ),
          )
        }
        item {
          val dueCount = pins.count { it.due > 0.0 }
          FilterChip(
            selected = dueOnlyFilter,
            onClick = { dueOnlyFilter = true },
            label = { Text(if (isBn) "শুধু বাকি ($dueCount)" else "Due Only ($dueCount)") },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFFB91C1C),
                selectedLabelColor = Color.White,
              ),
          )
        }
        item {
          FilterChip(
            selected = mapType == MapType.NORMAL,
            onClick = { mapType = MapType.NORMAL },
            label = { Text(if (isBn) "নর্মাল" else "Normal") },
          )
        }
        item {
          FilterChip(
            selected = mapType == MapType.HYBRID,
            onClick = { mapType = MapType.HYBRID },
            label = { Text(if (isBn) "স্যাটেলাইট" else "Hybrid") },
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Embedded GoogleMap Compose View
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(14.dp))
            .testTag("google_map_container")
      ) {
        GoogleMap(
          modifier = Modifier.matchParentSize(),
          cameraPositionState = cameraPositionState,
          properties =
            MapProperties(
              mapType = mapType,
              isMyLocationEnabled = hasLocationPermission,
            ),
          uiSettings =
            MapUiSettings(
              zoomControlsEnabled = true,
              compassEnabled = true,
              myLocationButtonEnabled = hasLocationPermission,
              mapToolbarEnabled = true,
            ),
        ) {
          // Shop Marker & 5 km delivery circle
          Marker(
            state = MarkerState(position = STORE_LAT_LNG),
            title = "আবরণী বস্ত্রালয় (Avarani Bastralaya)",
            snippet = "Nimtala Bazar, Khejuri - 721431",
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE),
          )
          Circle(
            center = STORE_LAT_LNG,
            radius = 3500.0,
            fillColor = Color(0x188B1E24),
            strokeColor = Color(0x668B1E24),
            strokeWidth = 2f,
          )

          visiblePins.forEach { pin ->
            val hue =
              if (pin.due > 0.0) BitmapDescriptorFactory.HUE_RED
              else BitmapDescriptorFactory.HUE_GREEN
            Marker(
              state = MarkerState(position = pin.position),
              title = "#${pin.code} ${pin.name}",
              snippet =
                if (pin.due > 0.0) "Due: ${StoreMapHelper.formatMoney(pin.due)} · ${pin.address}"
                else pin.address,
              icon = BitmapDescriptorFactory.defaultMarker(hue),
              onClick = {
                selectedPin = pin
                false
              },
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Customer Quick-Select Strip
      if (visiblePins.isNotEmpty()) {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(vertical = 2.dp),
        ) {
          items(visiblePins, key = { it.id }) { pin ->
            val isSelected = selectedPin?.id == pin.id
            Card(
              onClick = {
                selectedPin = pin
                scope.launch {
                  cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(pin.position, 14.5f)
                  )
                }
              },
              shape = RoundedCornerShape(12.dp),
              colors =
                CardDefaults.cardColors(
                  containerColor =
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else Color.White
                ),
              border =
                BorderStroke(
                  width = if (isSelected) 1.5.dp else 1.dp,
                  color =
                    if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE5E0D8),
                ),
              modifier = Modifier.width(205.dp),
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "#${pin.code}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    text =
                      if (pin.due > 0.0) StoreMapHelper.formatMoney(pin.due)
                      else (if (isBn) "পরিশোধিত" else "Settled"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (pin.due > 0.0) Color(0xFFB91C1C) else Color(0xFF047857),
                  )
                }
                Text(
                  text = pin.name,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = pin.address,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }

      // Selected Customer Action Card
      val activePin = selectedPin
      if (activePin != null) {
        Spacer(modifier = Modifier.height(8.dp))
        val distKm =
          String.format(Locale.US, "%.1f km", StoreMapHelper.distanceKm(STORE_LAT_LNG, activePin.position))
        Card(
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, Color(0xFFE5E0D8)),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "#${activePin.code} · ${activePin.name}",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "${activePin.address} ($distKm)",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Text(
                text = StoreMapHelper.formatMoney(activePin.due),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (activePin.due > 0.0) Color(0xFFB91C1C) else Color(0xFF047857),
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (activePin.phone.isNotBlank()) {
                OutlinedButton(
                  onClick = {
                    val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${activePin.phone}"))
                    context.startActivity(dial)
                  },
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (isBn) "কল করুন" else "Call")
                }
              }
              Button(
                onClick = {
                  val dest =
                    if (!activePin.isApproximate) {
                      "${activePin.position.latitude},${activePin.position.longitude}"
                    } else {
                      Uri.encode("${activePin.address}, Purba Medinipur, West Bengal")
                    }
                  val uri =
                    Uri.parse(
                      "https://www.google.com/maps/dir/?api=1&origin=${STORE_LAT_LNG.latitude},${STORE_LAT_LNG.longitude}&destination=$dest"
                    )
                  context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                },
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                  ),
                modifier = Modifier.weight(1f).testTag("map_directions_button"),
              ) {
                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isBn) "রুট দেখুন" else "Directions")
              }
            }
          }
        }
      }
    }
  }
}

private suspend fun geocodeAddressInWestBengal(context: Context, address: String): LatLng? {
  if (address.isBlank() || !Geocoder.isPresent()) return null
  val query = "$address, Purba Medinipur, West Bengal, India"
  val geocoder = Geocoder(context, Locale.getDefault())
  return try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      suspendCancellableCoroutine { cont ->
        geocoder.getFromLocationName(query, 1, 21.5, 87.3, 22.3, 88.3) { addresses ->
          val first = addresses.firstOrNull()
          cont.resume(first?.let { LatLng(it.latitude, it.longitude) })
        }
      }
    } else {
      @Suppress("DEPRECATION")
      val list = geocoder.getFromLocationName(query, 1, 21.5, 87.3, 22.3, 88.3)
      list?.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
    }
  } catch (_: Exception) {
    null
  }
}
