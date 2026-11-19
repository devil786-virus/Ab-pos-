package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context and verify offline pos asset`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Avarani POS", appName)
    val html = context.assets.open("index.html").bufferedReader().use { it.readText() }
    org.junit.Assert.assertTrue(html.contains("আবরণী বস্ত্রালয়"))
    org.junit.Assert.assertTrue(html.contains("8145990958@okbizaxis"))
  }
}
