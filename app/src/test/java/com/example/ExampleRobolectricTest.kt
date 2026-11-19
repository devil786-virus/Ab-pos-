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
    org.junit.Assert.assertTrue(html.contains("nav-stock-badge"))
    org.junit.Assert.assertTrue(html.contains("exportDailyReportPdf"))
    org.junit.Assert.assertTrue(html.contains("monthly-summary-card"))
    org.junit.Assert.assertTrue(html.contains("report-month"))
    org.junit.Assert.assertTrue(html.contains("computeMonthlySummaryData"))
    org.junit.Assert.assertTrue(html.contains("exportMonthlyReportPdf"))
    org.junit.Assert.assertTrue(html.contains("openStoreMap"))
    org.junit.Assert.assertTrue(html.contains("btn-store-map"))
  }

  @Test
  fun `daily report parser and formatter process populated and empty reports accurately`() {
    val populatedJson =
      """
      {
        "lang": "en",
        "dateIso": "2026-11-18",
        "generatedAt": "18/11/2026 08:55 PM",
        "storeName": "আবরণী বস্ত্রালয় (Avarani Bastralaya)",
        "storeAddress": "Nimtala Bazar, Khejuri, Purba Medinipur - 721431",
        "storePhone": "+91 81459 90958",
        "summary": {
          "totalSales": 1250.0,
          "cashFromBills": 1000.0,
          "cashFromDues": 200.0,
          "totalCashIn": 1200.0,
          "newDues": 250.0,
          "totalExpenses": 60.0,
          "netCash": 1140.0
        },
        "bills": [
          {
            "billNo": 1001,
            "time": "18/11/2026 10:30 AM",
            "customerCode": "503",
            "customerName": "Arun Maity",
            "itemCount": 2,
            "netBill": 1250.0,
            "cashPaid": 1000.0,
            "remainingDue": 250.0
          }
        ],
        "duePayments": [
          {
            "time": "18/11/2026 04:00 PM",
            "customerCode": "502",
            "customerName": "Mousumi Das",
            "oldDue": 1200.0,
            "amount": 200.0,
            "newDue": 1000.0,
            "note": "UPI"
          }
        ],
        "expenses": [
          {
            "time": "18/11/2026 02:15 PM",
            "desc": "Tea & Snacks",
            "amount": 60.0
          }
        ]
      }
      """.trimIndent()

    val parsed = com.example.pdf.DailyReportPdfGenerator.parseReport(populatedJson)
    assertEquals("2026-11-18", parsed.dateIso)
    assertEquals(1, parsed.bills.size)
    assertEquals("[503] Arun Maity", parsed.bills[0].customerDisplay)
    assertEquals(1, parsed.duePayments.size)
    assertEquals(1, parsed.expenses.size)
    assertEquals("₹1,140", com.example.pdf.DailyReportPdfGenerator.formatRs(parsed.netCash))

    val emptyJson = """{"lang":"bn","dateIso":"2026-11-18","summary":{},"bills":[],"duePayments":[],"expenses":[]}"""
    val emptyParsed = com.example.pdf.DailyReportPdfGenerator.parseReport(emptyJson)
    assertEquals(true, emptyParsed.isBn)
    assertEquals(0, emptyParsed.bills.size)
    assertEquals(0, emptyParsed.duePayments.size)
    assertEquals(0, emptyParsed.expenses.size)
    assertEquals("₹0", com.example.pdf.DailyReportPdfGenerator.formatRs(emptyParsed.netCash))
  }

  @Test
  fun `monthly report parser computes sales expenses profit and daily breakdown accurately`() {
    val monthlyJson =
      """
      {
        "reportType": "monthly",
        "lang": "en",
        "monthIso": "2026-11",
        "monthLabel": "November 2026",
        "generatedAt": "18/11/2026 09:00 PM",
        "summary": {
          "totalSales": 8500.0,
          "totalExpenses": 1200.0,
          "profit": 7300.0,
          "cashFromBills": 7000.0,
          "cashFromDues": 500.0,
          "totalCashIn": 7500.0,
          "newDues": 1500.0,
          "billCount": 6,
          "expenseCount": 3,
          "activeDays": 2
        },
        "days": [
          {
            "dateIso": "2026-11-18",
            "billCount": 4,
            "sales": 5500.0,
            "cashIn": 5000.0,
            "dues": 500.0,
            "expenses": 700.0,
            "profit": 4800.0
          },
          {
            "dateIso": "2026-11-15",
            "billCount": 2,
            "sales": 3000.0,
            "cashIn": 2500.0,
            "dues": 1000.0,
            "expenses": 500.0,
            "profit": 2500.0
          }
        ]
      }
      """.trimIndent()

    val parsed = com.example.pdf.DailyReportPdfGenerator.parseMonthlyReport(monthlyJson)
    assertEquals("2026-11", parsed.monthIso)
    assertEquals("November 2026", parsed.monthLabel)
    assertEquals(8500.0, parsed.totalSales, 0.001)
    assertEquals(1200.0, parsed.totalExpenses, 0.001)
    assertEquals(7300.0, parsed.profit, 0.001)
    assertEquals(2, parsed.days.size)
    assertEquals("2026-11-18", parsed.days[0].dateIso)
    assertEquals(4800.0, parsed.days[0].profit, 0.001)

    val emptyMonthlyJson = """{"reportType":"monthly","lang":"bn","monthIso":"2026-10","summary":{},"days":[]}"""
    val emptyParsed = com.example.pdf.DailyReportPdfGenerator.parseMonthlyReport(emptyMonthlyJson)
    assertEquals(true, emptyParsed.isBn)
    assertEquals(0.0, emptyParsed.totalSales, 0.001)
    assertEquals(0.0, emptyParsed.totalExpenses, 0.001)
    assertEquals(0.0, emptyParsed.profit, 0.001)
    assertEquals(0, emptyParsed.days.size)
  }

  @Test
  fun `ai chat roles map to exact configured gemini models`() {
    assertEquals("gemini-4-hydrogen", com.example.ai.AiChatRole.GENERAL.modelId)
    assertEquals("gemini-4-carbon", com.example.ai.AiChatRole.DEEP_REASONING.modelId)
    assertEquals("gemini-4-neon", com.example.ai.AiChatRole.COMPLEX_INTERACTIVE.modelId)
    assertEquals("gemini-3.1-flash-lite", com.example.ai.AiChatRole.FAST.modelId)
  }

  @Test
  fun `store map helper parses customer pins and validates maps api key`() {
    assertEquals(false, com.example.maps.StoreMapHelper.hasValidMapsApiKey(""))
    assertEquals(false, com.example.maps.StoreMapHelper.hasValidMapsApiKey("YOUR_GOOGLE_MAPS_API_KEY"))
    assertEquals(true, com.example.maps.StoreMapHelper.hasValidMapsApiKey("AIzaSyDummySampleKey123456"))

    val json =
      """
      [
        {"id":"c1","code":"501","name":"Ramesh Jana","phone":"9830011122","address":"Khejuri Bazar","due":450.0},
        {"id":"c2","code":"502","name":"Mousumi Das","phone":"9830033344","address":"","due":0.0}
      ]
      """.trimIndent()
    val pins = com.example.maps.StoreMapHelper.parseCustomersJson(json)
    assertEquals(2, pins.size)
    assertEquals("501", pins[0].code)
    assertEquals("Ramesh Jana", pins[0].name)
    assertEquals(450.0, pins[0].due, 0.001)
    assertEquals(true, pins[1].address.contains("Khejuri"))
    assertEquals(0, com.example.maps.StoreMapHelper.parseCustomersJson("[]").size)
  }
}
