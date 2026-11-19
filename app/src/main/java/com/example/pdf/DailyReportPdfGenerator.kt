package com.example.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream
import java.text.DecimalFormat
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject

object DailyReportPdfGenerator {
  private const val PAGE_WIDTH = 595
  private const val PAGE_HEIGHT = 842
  private const val MARGIN_H = 32f
  private const val MARGIN_TOP = 32f
  private const val MARGIN_BOTTOM = 42f
  private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_H * 2f)

  private val colorCrimson = Color.rgb(139, 30, 36)
  private val colorCrimsonDark = Color.rgb(104, 18, 23)
  private val colorGold = Color.rgb(251, 191, 36)
  private val colorEmerald = Color.rgb(4, 120, 87)
  private val colorEmeraldBg = Color.rgb(236, 253, 245)
  private val colorDanger = Color.rgb(185, 28, 28)
  private val colorAmber = Color.rgb(180, 83, 9)
  private val colorText = Color.rgb(31, 41, 55)
  private val colorMuted = Color.rgb(75, 85, 99)
  private val colorBorder = Color.rgb(229, 224, 216)
  private val colorWarmBg = Color.rgb(253, 247, 237)
  private val colorZebra = Color.rgb(249, 250, 251)

  private val moneyFormat = DecimalFormat("#,##0.##")

  data class ReportBillRow(
    val billNo: Long,
    val time: String,
    val customerDisplay: String,
    val itemCount: Long,
    val netBill: Double,
    val cashPaid: Double,
    val remainingDue: Double,
  )

  data class ReportDueRow(
    val time: String,
    val customerDisplay: String,
    val oldDue: Double,
    val amount: Double,
    val newDue: Double,
    val note: String,
  )

  data class ReportExpenseRow(
    val time: String,
    val desc: String,
    val amount: Double,
  )

  data class ParsedDailyReport(
    val isBn: Boolean,
    val dateIso: String,
    val generatedAt: String,
    val storeName: String,
    val storeAddress: String,
    val storePhone: String,
    val totalSales: Double,
    val cashFromBills: Double,
    val cashFromDues: Double,
    val totalCashIn: Double,
    val newDues: Double,
    val totalExpenses: Double,
    val netCash: Double,
    val bills: List<ReportBillRow>,
    val duePayments: List<ReportDueRow>,
    val expenses: List<ReportExpenseRow>,
  )

  data class MonthlyDayRow(
    val dateIso: String,
    val billCount: Long,
    val sales: Double,
    val cashIn: Double,
    val dues: Double,
    val expenses: Double,
    val profit: Double,
  )

  data class ParsedMonthlyReport(
    val isBn: Boolean,
    val monthIso: String,
    val monthLabel: String,
    val generatedAt: String,
    val storeName: String,
    val storeAddress: String,
    val storePhone: String,
    val totalSales: Double,
    val totalExpenses: Double,
    val profit: Double,
    val profitMarginPct: Double,
    val cashFromBills: Double,
    val cashFromDues: Double,
    val totalCashIn: Double,
    val newDues: Double,
    val billCount: Long,
    val expenseCount: Long,
    val activeDays: Long,
    val days: List<MonthlyDayRow>,
  )

  internal fun formatRs(value: Double): String {
    val safe = if (value.isNaN() || value.isInfinite()) 0.0 else value
    return "₹${moneyFormat.format(safe)}"
  }

  internal fun parseReport(reportJson: String): ParsedDailyReport {
    val root = JSONObject(reportJson)
    val isBn = root.optString("lang", "bn") == "bn"
    val summary = root.optJSONObject("summary") ?: JSONObject()

    val billsArr = root.optJSONArray("bills") ?: JSONArray()
    val bills = buildList {
      for (i in 0 until billsArr.length()) {
        val b = billsArr.getJSONObject(i)
        val custCode = b.optString("customerCode", "").trim()
        val custName = b.optString("customerName", "Walk-in")
        add(
          ReportBillRow(
            billNo = b.optLong("billNo", 0L),
            time = b.optString("time", "-"),
            customerDisplay = if (custCode.isNotBlank()) "[$custCode] $custName" else custName,
            itemCount = b.optLong("itemCount", 0L),
            netBill = b.optDouble("netBill", 0.0),
            cashPaid = b.optDouble("cashPaid", 0.0),
            remainingDue = b.optDouble("remainingDue", 0.0),
          )
        )
      }
    }

    val duesArr = root.optJSONArray("duePayments") ?: JSONArray()
    val duePayments = buildList {
      for (i in 0 until duesArr.length()) {
        val dp = duesArr.getJSONObject(i)
        val code = dp.optString("customerCode", "").trim()
        val name = dp.optString("customerName", "Customer")
        add(
          ReportDueRow(
            time = dp.optString("time", "-"),
            customerDisplay = if (code.isNotBlank()) "[$code] $name" else name,
            oldDue = dp.optDouble("oldDue", 0.0),
            amount = dp.optDouble("amount", 0.0),
            newDue = dp.optDouble("newDue", 0.0),
            note = dp.optString("note", "").ifBlank { "-" },
          )
        )
      }
    }

    val expArr = root.optJSONArray("expenses") ?: JSONArray()
    val expenses = buildList {
      for (i in 0 until expArr.length()) {
        val ex = expArr.getJSONObject(i)
        add(
          ReportExpenseRow(
            time = ex.optString("time", "-"),
            desc = ex.optString("desc", "Expense"),
            amount = ex.optDouble("amount", 0.0),
          )
        )
      }
    }

    return ParsedDailyReport(
      isBn = isBn,
      dateIso = root.optString("dateIso", ""),
      generatedAt = root.optString("generatedAt", ""),
      storeName = root.optString("storeName", "আবরণী বস্ত্রালয় (Avarani Bastralaya)"),
      storeAddress =
        root.optString("storeAddress", "Nimtala Bazar, Khejuri, Purba Medinipur - 721431"),
      storePhone = root.optString("storePhone", "+91 81459 90958"),
      totalSales = summary.optDouble("totalSales", 0.0),
      cashFromBills = summary.optDouble("cashFromBills", 0.0),
      cashFromDues = summary.optDouble("cashFromDues", 0.0),
      totalCashIn = summary.optDouble("totalCashIn", 0.0),
      newDues = summary.optDouble("newDues", 0.0),
      totalExpenses = summary.optDouble("totalExpenses", 0.0),
      netCash = summary.optDouble("netCash", 0.0),
      bills = bills,
      duePayments = duePayments,
      expenses = expenses,
    )
  }

  internal fun parseMonthlyReport(reportJson: String): ParsedMonthlyReport {
    val root = JSONObject(reportJson)
    val isBn = root.optString("lang", "bn") == "bn"
    val summary = root.optJSONObject("summary") ?: JSONObject()
    val daysArr = root.optJSONArray("days") ?: JSONArray()
    val days = buildList {
      for (i in 0 until daysArr.length()) {
        val d = daysArr.getJSONObject(i)
        val sales = d.optDouble("sales", 0.0)
        val expenses = d.optDouble("expenses", 0.0)
        add(
          MonthlyDayRow(
            dateIso = d.optString("dateIso", "-"),
            billCount = d.optLong("billCount", 0L),
            sales = sales,
            cashIn = d.optDouble("cashIn", 0.0),
            dues = d.optDouble("dues", 0.0),
            expenses = expenses,
            profit = d.optDouble("profit", sales - expenses),
          )
        )
      }
    }

    val totalSales = summary.optDouble("totalSales", 0.0)
    val totalExpenses = summary.optDouble("totalExpenses", 0.0)
    val profit = summary.optDouble("profit", totalSales - totalExpenses)

    return ParsedMonthlyReport(
      isBn = isBn,
      monthIso = root.optString("monthIso", ""),
      monthLabel = root.optString("monthLabel", root.optString("monthIso", "")),
      generatedAt = root.optString("generatedAt", ""),
      storeName = root.optString("storeName", "আবরণী বস্ত্রালয় (Avarani Bastralaya)"),
      storeAddress =
        root.optString("storeAddress", "Nimtala Bazar, Khejuri, Purba Medinipur - 721431"),
      storePhone = root.optString("storePhone", "+91 81459 90958"),
      totalSales = totalSales,
      totalExpenses = totalExpenses,
      profit = profit,
      profitMarginPct =
        summary.optDouble(
          "profitMarginPct",
          if (totalSales > 0.0) (profit / totalSales) * 100.0 else 0.0,
        ),
      cashFromBills = summary.optDouble("cashFromBills", 0.0),
      cashFromDues = summary.optDouble("cashFromDues", 0.0),
      totalCashIn = summary.optDouble("totalCashIn", 0.0),
      newDues = summary.optDouble("newDues", 0.0),
      billCount = summary.optLong("billCount", 0L),
      expenseCount = summary.optLong("expenseCount", 0L),
      activeDays = summary.optLong("activeDays", days.size.toLong()),
      days = days,
    )
  }

  private class PageComposer(private val document: PdfDocument, private val footerTitle: String) {
    var pageNumber = 0
      private set
    private var currentPage: PdfDocument.Page? = null
    lateinit var canvas: Canvas
      private set
    var y = MARGIN_TOP

    fun startNewPage() {
      finishCurrentPage()
      pageNumber++
      val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
      val page = document.startPage(pageInfo)
      currentPage = page
      canvas = page.canvas
      canvas.drawColor(Color.WHITE)
      y = MARGIN_TOP
    }

    fun ensureSpace(neededHeight: Float, onNewPage: (() -> Unit)? = null) {
      if (y + neededHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
        startNewPage()
        onNewPage?.invoke()
      }
    }

    fun finishCurrentPage() {
      val page = currentPage ?: return
      val footerPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = colorMuted
          textSize = 8.5f
        }
      val linePaint =
        Paint().apply {
          color = colorBorder
          strokeWidth = 0.8f
        }
      val footerY = PAGE_HEIGHT - 20f
      canvas.drawLine(MARGIN_H, footerY - 12f, PAGE_WIDTH - MARGIN_H, footerY - 12f, linePaint)
      canvas.drawText(footerTitle, MARGIN_H, footerY, footerPaint)
      val pageLabel = "Page $pageNumber"
      val labelW = footerPaint.measureText(pageLabel)
      canvas.drawText(pageLabel, PAGE_WIDTH - MARGIN_H - labelW, footerY, footerPaint)
      document.finishPage(page)
      currentPage = null
    }
  }

  fun generate(reportJson: String, outputStream: OutputStream) {
    val root = JSONObject(reportJson)
    if (root.optString("reportType", "daily") == "monthly") {
      generateMonthlyPdf(parseMonthlyReport(reportJson), outputStream)
      return
    }
    val lang = root.optString("lang", "bn")
    val isBn = lang == "bn"
    val dateIso = root.optString("dateIso", "")
    val generatedAt = root.optString("generatedAt", "")
    val storeName = root.optString("storeName", "আবরণী বস্ত্রালয় (Avarani Bastralaya)")
    val storeAddress =
      root.optString("storeAddress", "Nimtala Bazar, Khejuri, Purba Medinipur - 721431")
    val storePhone = root.optString("storePhone", "+91 81459 90958")
    val summary = root.optJSONObject("summary") ?: JSONObject()

    val bills = root.optJSONArray("bills") ?: JSONArray()
    val duePayments = root.optJSONArray("duePayments") ?: JSONArray()
    val expenses = root.optJSONArray("expenses") ?: JSONArray()

    val document = PdfDocument()
    val footerText = "$storeName  |  Daily Report: $dateIso"
    val composer = PageComposer(document, footerText)

    try {
      composer.startNewPage()

      drawHeaderBanner(
        composer = composer,
        storeName = storeName,
        storeAddress = storeAddress,
        storePhone = storePhone,
        reportTitle = if (isBn) "দৈনিক ব্যবসা ও হিসাব রিপোর্ট (Daily Report)" else "Daily Business & Account Report",
        dateIso = dateIso,
        generatedAt = generatedAt,
      )

      drawSummarySection(composer, summary, isBn)

      drawBillsSection(composer, bills, isBn)

      drawDuePaymentsSection(composer, duePayments, isBn)

      drawExpensesSection(composer, expenses, isBn)

      composer.finishCurrentPage()
      document.writeTo(outputStream)
    } finally {
      document.close()
    }
  }

  private fun generateMonthlyPdf(report: ParsedMonthlyReport, outputStream: OutputStream) {
    val document = PdfDocument()
    val footerText = "${report.storeName}  |  Monthly Summary: ${report.monthIso}"
    val composer = PageComposer(document, footerText)

    try {
      composer.startNewPage()

      drawHeaderBanner(
        composer = composer,
        storeName = report.storeName,
        storeAddress = report.storeAddress,
        storePhone = report.storePhone,
        reportTitle =
          if (report.isBn) "মাসিক আর্থিক সারসংক্ষেপ (Monthly Financial Summary)"
          else "Monthly Financial Summary",
        dateIso = report.monthLabel.ifBlank { report.monthIso },
        generatedAt = report.generatedAt,
        periodLabelPrefix = "Month: ",
      )

      drawMonthlySummarySection(composer, report)

      drawMonthlyDaysSection(composer, report)

      composer.finishCurrentPage()
      document.writeTo(outputStream)
    } finally {
      document.close()
    }
  }

  private fun drawHeaderBanner(
    composer: PageComposer,
    storeName: String,
    storeAddress: String,
    storePhone: String,
    reportTitle: String,
    dateIso: String,
    generatedAt: String,
    periodLabelPrefix: String = "Date: ",
  ) {
    val bannerHeight = 82f
    val rect = RectF(MARGIN_H, composer.y, PAGE_WIDTH - MARGIN_H, composer.y + bannerHeight)
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorCrimson }
    composer.canvas.drawRoundRect(rect, 10f, 10f, bgPaint)

    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorGold }
    composer.canvas.drawRect(MARGIN_H, composer.y + bannerHeight - 5f, PAGE_WIDTH - MARGIN_H, composer.y + bannerHeight, accentPaint)

    val titlePaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
    val subPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 255, 255, 255)
        textSize = 9.5f
      }
    val badgePaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGold
        textSize = 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }

    composer.canvas.drawText(storeName, MARGIN_H + 14f, composer.y + 24f, titlePaint)
    composer.canvas.drawText("$storeAddress | $storePhone", MARGIN_H + 14f, composer.y + 40f, subPaint)
    composer.canvas.drawText(reportTitle, MARGIN_H + 14f, composer.y + 60f, badgePaint)

    val rightX = PAGE_WIDTH - MARGIN_H - 14f
    val dateLabel = "$periodLabelPrefix$dateIso"
    val dateW = badgePaint.measureText(dateLabel)
    composer.canvas.drawText(dateLabel, rightX - dateW, composer.y + 24f, badgePaint)

    if (generatedAt.isNotBlank()) {
      val genLabel = "Generated: $generatedAt"
      val genW = subPaint.measureText(genLabel)
      composer.canvas.drawText(genLabel, rightX - genW, composer.y + 40f, subPaint)
    }

    composer.y += bannerHeight + 14f
  }

  private fun drawMonthlySummarySection(composer: PageComposer, report: ParsedMonthlyReport) {
    composer.ensureSpace(132f)
    val isBn = report.isBn
    drawSectionTitle(
      composer,
      if (isBn) "মাসিক আর্থিক সারসংক্ষেপ (${report.monthLabel})" else "Monthly Financial Summary (${report.monthLabel})",
    )

    val profitStr =
      if (report.profit >= 0.0) formatRs(report.profit) else "-${formatRs(kotlin.math.abs(report.profit))}"
    val profitColor = if (report.profit >= 0.0) colorEmerald else colorDanger
    val gap = 8f
    val boxW = (CONTENT_WIDTH - gap * 3f) / 4f
    val boxH = 52f
    val topY = composer.y

    val kpis =
      listOf(
        Triple(if (isBn) "মোট বিক্রি (Sales)" else "Monthly Sales", formatRs(report.totalSales), colorCrimson),
        Triple(if (isBn) "মোট খরচ (Exp.)" else "Monthly Expenses", formatRs(report.totalExpenses), colorAmber),
        Triple(
          if (report.profit >= 0.0) (if (isBn) "মাসের লাভ (Profit)" else "Monthly Profit")
          else (if (isBn) "মাসের ক্ষতি (Loss)" else "Monthly Loss"),
          profitStr,
          profitColor,
        ),
        Triple(if (isBn) "মোট নগদ আদায়" else "Cash Collected", formatRs(report.totalCashIn), colorEmerald),
      )

    kpis.forEachIndexed { idx, (label, value, accentColor) ->
      val left = MARGIN_H + idx * (boxW + gap)
      val boxRect = RectF(left, topY, left + boxW, topY + boxH)
      val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorWarmBg }
      val borderPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = colorBorder
          style = Paint.Style.STROKE
          strokeWidth = 1f
        }
      composer.canvas.drawRoundRect(boxRect, 8f, 8f, fillPaint)
      composer.canvas.drawRoundRect(boxRect, 8f, 8f, borderPaint)

      val lblPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
          color = colorMuted
          textSize = 8.5f
          typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
      val valPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
          color = accentColor
          textSize = 13f
          typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
      composer.canvas.drawText(label, left + 8f, topY + 18f, lblPaint)
      composer.canvas.drawText(value, left + 8f, topY + 39f, valPaint)
    }

    composer.y = topY + boxH + 8f

    val barH = 38f
    val barRect = RectF(MARGIN_H, composer.y, PAGE_WIDTH - MARGIN_H, composer.y + barH)
    val barBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorEmeraldBg }
    val barBorder =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorEmerald
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
      }
    composer.canvas.drawRoundRect(barRect, 8f, 8f, barBg)
    composer.canvas.drawRoundRect(barRect, 8f, 8f, barBorder)

    val titlePaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorEmerald
        textSize = 10f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
    val subPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorMuted
        textSize = 8.5f
      }
    val marginStr = String.format(java.util.Locale.US, "%.1f%%", report.profitMarginPct)
    val headline =
      if (isBn) "লাভ = বিক্রি − খরচ (লাভের হার: $marginStr)"
      else "Profit = Total Sales − Total Expenses (Margin: $marginStr)"
    val subLine =
      if (isBn)
        "বিল: ${report.billCount}  |  খরচ: ${report.expenseCount}  |  নতুন বাকি: ${formatRs(report.newDues)}  |  সক্রিয় দিন: ${report.activeDays}"
      else
        "Bills: ${report.billCount}  |  Expenses: ${report.expenseCount}  |  New Dues: ${formatRs(report.newDues)}  |  Active Days: ${report.activeDays}"
    composer.canvas.drawText(headline, MARGIN_H + 10f, composer.y + 16f, titlePaint)
    composer.canvas.drawText(subLine, MARGIN_H + 10f, composer.y + 30f, subPaint)
    composer.y += barH + 16f
  }

  private fun drawMonthlyDaysSection(composer: PageComposer, report: ParsedMonthlyReport) {
    val isBn = report.isBn
    val title =
      if (isBn) "দৈনিক হিসাব তালিকা (${report.days.size} দিন)"
      else "Daily Breakdown (${report.days.size} days)"
    composer.ensureSpace(64f)
    drawSectionTitle(composer, title)

    if (report.days.isEmpty()) {
      drawEmptyTableNote(
        composer,
        if (isBn) "এই মাসে কোনো লেনদেন পাওয়া যায়নি।" else "No transactions recorded for this month.",
      )
      return
    }

    val colWidths = floatArrayOf(92f, 54f, 96f, 96f, 96f, 97f) // sum = 531
    val headers =
      if (isBn) listOf("Date", "Bills", "Sales", "Cash In", "Expenses", "Profit")
      else listOf("Date", "Bills", "Sales", "Cash In", "Expenses", "Profit")
    val aligns =
      listOf(
        Layout.Alignment.ALIGN_NORMAL,
        Layout.Alignment.ALIGN_OPPOSITE,
        Layout.Alignment.ALIGN_OPPOSITE,
        Layout.Alignment.ALIGN_OPPOSITE,
        Layout.Alignment.ALIGN_OPPOSITE,
        Layout.Alignment.ALIGN_OPPOSITE,
      )

    drawTableHeader(composer, headers, colWidths, aligns)

    report.days.forEachIndexed { i, day ->
      val pStr =
        if (day.profit >= 0.0) formatRs(day.profit) else "-${formatRs(kotlin.math.abs(day.profit))}"
      drawTableRow(
        composer = composer,
        cells =
          listOf(
            day.dateIso,
            day.billCount.toString(),
            formatRs(day.sales),
            formatRs(day.cashIn),
            formatRs(day.expenses),
            pStr,
          ),
        colWidths = colWidths,
        aligns = aligns,
        isZebra = i % 2 == 1,
        onNewPageHeader = { drawTableHeader(composer, headers, colWidths, aligns) },
      )
    }

    val totalProfitStr =
      if (report.profit >= 0.0) formatRs(report.profit) else "-${formatRs(kotlin.math.abs(report.profit))}"
    drawTableTotalsRow(
      composer = composer,
      cells =
        listOf(
          if (isBn) "মাসিক মোট" else "Monthly Total",
          report.billCount.toString(),
          formatRs(report.totalSales),
          formatRs(report.totalCashIn),
          formatRs(report.totalExpenses),
          totalProfitStr,
        ),
      colWidths = colWidths,
      aligns = aligns,
    )
    composer.y += 12f
  }

  private fun drawSummarySection(composer: PageComposer, summary: JSONObject, isBn: Boolean) {
    composer.ensureSpace(128f)

    val totalSales = summary.optDouble("totalSales", 0.0)
    val cashFromBills = summary.optDouble("cashFromBills", 0.0)
    val cashFromDues = summary.optDouble("cashFromDues", 0.0)
    val totalCashIn = summary.optDouble("totalCashIn", 0.0)
    val newDues = summary.optDouble("newDues", 0.0)
    val totalExpenses = summary.optDouble("totalExpenses", 0.0)
    val netCash = summary.optDouble("netCash", 0.0)

    drawSectionTitle(
      composer,
      if (isBn) "আর্থিক সারসংক্ষেপ (Financial Summary)" else "Financial Summary",
    )

    val gap = 8f
    val boxW = (CONTENT_WIDTH - gap * 3f) / 4f
    val boxH = 52f
    val topY = composer.y

    val kpis =
      listOf(
        Triple(if (isBn) "মোট বিক্রি (Sales)" else "Total Sales", formatRs(totalSales), colorCrimson),
        Triple(if (isBn) "মোট নগদ জমা (Cash)" else "Cash Received", formatRs(totalCashIn), colorEmerald),
        Triple(if (isBn) "নতুন বাকি (New Dues)" else "New Dues", formatRs(newDues), colorDanger),
        Triple(if (isBn) "দোকানের খরচ (Exp.)" else "Expenses", formatRs(totalExpenses), colorAmber),
      )

    kpis.forEachIndexed { idx, (label, value, accentColor) ->
      val left = MARGIN_H + idx * (boxW + gap)
      val boxRect = RectF(left, topY, left + boxW, topY + boxH)
      val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorWarmBg }
      val borderPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = colorBorder
          style = Paint.Style.STROKE
          strokeWidth = 1f
        }
      composer.canvas.drawRoundRect(boxRect, 8f, 8f, fillPaint)
      composer.canvas.drawRoundRect(boxRect, 8f, 8f, borderPaint)

      val lblPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
          color = colorMuted
          textSize = 8.5f
          typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
      val valPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
          color = accentColor
          textSize = 13.5f
          typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
      composer.canvas.drawText(label, left + 8f, topY + 18f, lblPaint)
      composer.canvas.drawText(value, left + 8f, topY + 39f, valPaint)
    }

    composer.y = topY + boxH + 8f

    // Net Cash in Hand highlight bar
    val netBoxH = 38f
    val netRect = RectF(MARGIN_H, composer.y, PAGE_WIDTH - MARGIN_H, composer.y + netBoxH)
    val netBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorEmeraldBg }
    val netBorder =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorEmerald
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
      }
    composer.canvas.drawRoundRect(netRect, 8f, 8f, netBg)
    composer.canvas.drawRoundRect(netRect, 8f, 8f, netBorder)

    val netTitlePaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorEmerald
        textSize = 10.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
    val subBreakPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorMuted
        textSize = 8.5f
      }
    val netValuePaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorEmerald
        textSize = 15f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }

    val netLabel =
      if (isBn) "হাতে থাকা নিট নগদ (Net Cash in Hand = Cash In - Expenses)"
      else "Net Cash in Hand (Cash Received - Expenses)"
    val breakdown =
      if (isBn) "বিল থেকে নগদ: ${formatRs(cashFromBills)}  |  বাকি আদায়: ${formatRs(cashFromDues)}"
      else "Cash from Bills: ${formatRs(cashFromBills)}  |  Due Collections: ${formatRs(cashFromDues)}"

    composer.canvas.drawText(netLabel, MARGIN_H + 10f, composer.y + 16f, netTitlePaint)
    composer.canvas.drawText(breakdown, MARGIN_H + 10f, composer.y + 30f, subBreakPaint)

    val netText = formatRs(netCash)
    val netW = netValuePaint.measureText(netText)
    composer.canvas.drawText(netText, PAGE_WIDTH - MARGIN_H - 12f - netW, composer.y + 24f, netValuePaint)

    composer.y += netBoxH + 16f
  }

  private fun drawBillsSection(composer: PageComposer, bills: JSONArray, isBn: Boolean) {
    val title =
      if (isBn) "বিল ও বিক্রির তালিকা (${bills.length()})"
      else "Bills & Sales (${bills.length()})"
    composer.ensureSpace(64f)
    drawSectionTitle(composer, title)

    val colWidths = floatArrayOf(52f, 82f, 155f, 44f, 66f, 66f, 66f) // sum = 531
    val headers =
      if (isBn) listOf("Bill #", "Time", "Customer", "Qty", "Net Bill", "Cash In", "Due")
      else listOf("Bill #", "Time", "Customer", "Qty", "Net Bill", "Cash Paid", "Due")
    val aligns =
      listOf(
        Layout.Alignment.ALIGN_NORMAL,
        Layout.Alignment.ALIGN_NORMAL,
        Layout.Alignment.ALIGN_NORMAL,
        Layout.Alignment.ALIGN_OPPOSITE,
        Layout.Alignment.ALIGN_OPPOSITE,
        Layout.Alignment.ALIGN_OPPOSITE,
        Layout.Alignment.ALIGN_OPPOSITE,
      )

    if (bills.length() == 0) {
      drawEmptyTableNote(composer, if (isBn) "এই তারিখে কোনো বিল নেই।" else "No bills recorded for this date.")
      return
    }

    drawTableHeader(composer, headers, colWidths, aligns)

    var sumNet = 0.0
    var sumCash = 0.0
    var sumDue = 0.0
    var sumItems = 0L

    for (i in 0 until bills.length()) {
      val b = bills.getJSONObject(i)
      val billNo = "#${b.optLong("billNo", 0L)}"
      val time = b.optString("time", "-")
      val custCode = b.optString("customerCode", "").trim()
      val custName = b.optString("customerName", "Walk-in")
      val custDisplay = if (custCode.isNotBlank()) "[$custCode] $custName" else custName
      val itemCount = b.optLong("itemCount", 0L)
      val netBill = b.optDouble("netBill", 0.0)
      val cashPaid = b.optDouble("cashPaid", 0.0)
      val remainingDue = b.optDouble("remainingDue", 0.0)

      sumNet += netBill
      sumCash += cashPaid
      sumDue += remainingDue
      sumItems += itemCount

      drawTableRow(
        composer = composer,
        cells =
          listOf(
            billNo,
            time,
            custDisplay,
            itemCount.toString(),
            formatRs(netBill),
            formatRs(cashPaid),
            formatRs(remainingDue),
          ),
        colWidths = colWidths,
        aligns = aligns,
        isZebra = i % 2 == 1,
        onNewPageHeader = { drawTableHeader(composer, headers, colWidths, aligns) },
      )
    }

    drawTableTotalsRow(
      composer = composer,
      cells =
        listOf(
          if (isBn) "মোট" else "Total",
          "${bills.length()} bills",
          "",
          sumItems.toString(),
          formatRs(sumNet),
          formatRs(sumCash),
          formatRs(sumDue),
        ),
      colWidths = colWidths,
      aligns = aligns,
    )
    composer.y += 14f
  }

  private fun drawDuePaymentsSection(composer: PageComposer, duePayments: JSONArray, isBn: Boolean) {
    val title =
      if (isBn) "বাকি টাকা আদায় (${duePayments.length()})"
      else "Khata Due Collections (${duePayments.length()})"
    composer.ensureSpace(64f)
    drawSectionTitle(composer, title)

    if (duePayments.length() == 0) {
      drawEmptyTableNote(
        composer,
        if (isBn) "এই তারিখে কোনো বাকি আদায় নেই।" else "No due collections recorded for this date.",
      )
      return
    }

    val colWidths = floatArrayOf(82f, 155f, 68f, 68f, 68f, 90f) // sum = 531
    val headers = listOf("Time", "Customer", "Old Due", "Paid", "New Due", "Note")
    val aligns =
      listOf(
        Layout.Alignment.ALIGN_NORMAL,
        Layout.Alignment.ALIGN_NORMAL,
        Layout.Alignment.ALIGN_OPPOSITE,
        Layout.Alignment.ALIGN_OPPOSITE,
        Layout.Alignment.ALIGN_OPPOSITE,
        Layout.Alignment.ALIGN_NORMAL,
      )

    drawTableHeader(composer, headers, colWidths, aligns)

    var sumPaid = 0.0
    for (i in 0 until duePayments.length()) {
      val dp = duePayments.getJSONObject(i)
      val time = dp.optString("time", "-")
      val code = dp.optString("customerCode", "").trim()
      val name = dp.optString("customerName", "Customer")
      val custDisplay = if (code.isNotBlank()) "[$code] $name" else name
      val oldDue = dp.optDouble("oldDue", 0.0)
      val amount = dp.optDouble("amount", 0.0)
      val newDue = dp.optDouble("newDue", 0.0)
      val note = dp.optString("note", "").ifBlank { "-" }
      sumPaid += amount

      drawTableRow(
        composer = composer,
        cells =
          listOf(
            time,
            custDisplay,
            formatRs(oldDue),
            formatRs(amount),
            formatRs(newDue),
            note,
          ),
        colWidths = colWidths,
        aligns = aligns,
        isZebra = i % 2 == 1,
        onNewPageHeader = { drawTableHeader(composer, headers, colWidths, aligns) },
      )
    }

    drawTableTotalsRow(
      composer = composer,
      cells = listOf(if (isBn) "মোট আদায়" else "Total Collected", "${duePayments.length()} entries", "", formatRs(sumPaid), "", ""),
      colWidths = colWidths,
      aligns = aligns,
    )
    composer.y += 14f
  }

  private fun drawExpensesSection(composer: PageComposer, expenses: JSONArray, isBn: Boolean) {
    val title =
      if (isBn) "দোকানের খরচ (${expenses.length()})"
      else "Shop Expenses (${expenses.length()})"
    composer.ensureSpace(64f)
    drawSectionTitle(composer, title)

    if (expenses.length() == 0) {
      drawEmptyTableNote(
        composer,
        if (isBn) "এই তারিখে কোনো খরচ নেই।" else "No expenses recorded for this date.",
      )
      return
    }

    val colWidths = floatArrayOf(110f, 321f, 100f) // sum = 531
    val headers = listOf("Time", "Description", "Amount (₹)")
    val aligns =
      listOf(
        Layout.Alignment.ALIGN_NORMAL,
        Layout.Alignment.ALIGN_NORMAL,
        Layout.Alignment.ALIGN_OPPOSITE,
      )

    drawTableHeader(composer, headers, colWidths, aligns)

    var sumExp = 0.0
    for (i in 0 until expenses.length()) {
      val ex = expenses.getJSONObject(i)
      val time = ex.optString("time", "-")
      val desc = ex.optString("desc", "Expense")
      val amount = ex.optDouble("amount", 0.0)
      sumExp += amount

      drawTableRow(
        composer = composer,
        cells = listOf(time, desc, formatRs(amount)),
        colWidths = colWidths,
        aligns = aligns,
        isZebra = i % 2 == 1,
        onNewPageHeader = { drawTableHeader(composer, headers, colWidths, aligns) },
      )
    }

    drawTableTotalsRow(
      composer = composer,
      cells = listOf(if (isBn) "মোট খরচ" else "Total Expenses", "${expenses.length()} items", formatRs(sumExp)),
      colWidths = colWidths,
      aligns = aligns,
    )
    composer.y += 12f
  }

  private fun drawSectionTitle(composer: PageComposer, title: String) {
    val paint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorCrimsonDark
        textSize = 11.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
    composer.canvas.drawText(title, MARGIN_H, composer.y + 12f, paint)
    val linePaint =
      Paint().apply {
        color = colorCrimson
        strokeWidth = 1.2f
      }
    composer.canvas.drawLine(MARGIN_H, composer.y + 17f, PAGE_WIDTH - MARGIN_H, composer.y + 17f, linePaint)
    composer.y += 23f
  }

  private fun drawEmptyTableNote(composer: PageComposer, message: String) {
    val boxH = 26f
    composer.ensureSpace(boxH + 10f)
    val rect = RectF(MARGIN_H, composer.y, PAGE_WIDTH - MARGIN_H, composer.y + boxH)
    val bgPaint = Paint().apply { color = colorZebra }
    val borderPaint =
      Paint().apply {
        color = colorBorder
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
      }
    composer.canvas.drawRoundRect(rect, 6f, 6f, bgPaint)
    composer.canvas.drawRoundRect(rect, 6f, 6f, borderPaint)

    val textPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorMuted
        textSize = 9f
      }
    composer.canvas.drawText(message, MARGIN_H + 10f, composer.y + 16f, textPaint)
    composer.y += boxH + 12f
  }

  private fun drawTableHeader(
    composer: PageComposer,
    headers: List<String>,
    colWidths: FloatArray,
    aligns: List<Layout.Alignment>,
  ) {
    val rowH = 22f
    composer.ensureSpace(rowH)
    val rect = RectF(MARGIN_H, composer.y, PAGE_WIDTH - MARGIN_H, composer.y + rowH)
    val bgPaint = Paint().apply { color = colorWarmBg }
    val borderPaint =
      Paint().apply {
        color = colorBorder
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
      }
    composer.canvas.drawRect(rect, bgPaint)
    composer.canvas.drawRect(rect, borderPaint)

    val textPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorCrimsonDark
        textSize = 8.8f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }

    var x = MARGIN_H
    headers.forEachIndexed { idx, label ->
      val w = colWidths[idx]
      drawCellLayout(composer.canvas, label, x + 4f, composer.y + 5f, max(10f, w - 8f), textPaint, aligns[idx])
      x += w
    }
    composer.y += rowH
  }

  private fun drawTableRow(
    composer: PageComposer,
    cells: List<String>,
    colWidths: FloatArray,
    aligns: List<Layout.Alignment>,
    isZebra: Boolean,
    onNewPageHeader: () -> Unit,
  ) {
    val textPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorText
        textSize = 8.5f
      }

    val layouts =
      cells.mapIndexed { idx, text ->
        val innerW = max(10, (colWidths[idx] - 8f).toInt())
        StaticLayout.Builder.obtain(text, 0, text.length, textPaint, innerW)
          .setAlignment(aligns[idx])
          .setIncludePad(false)
          .build()
      }
    val maxTextH = layouts.maxOfOrNull { it.height.toFloat() } ?: 11f
    val rowH = max(20f, maxTextH + 9f)

    composer.ensureSpace(rowH) { onNewPageHeader() }

    val rect = RectF(MARGIN_H, composer.y, PAGE_WIDTH - MARGIN_H, composer.y + rowH)
    if (isZebra) {
      composer.canvas.drawRect(rect, Paint().apply { color = colorZebra })
    }
    val borderPaint =
      Paint().apply {
        color = colorBorder
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
      }
    composer.canvas.drawLine(MARGIN_H, composer.y + rowH, PAGE_WIDTH - MARGIN_H, composer.y + rowH, borderPaint)

    var x = MARGIN_H
    layouts.forEachIndexed { idx, layout ->
      composer.canvas.save()
      composer.canvas.translate(x + 4f, composer.y + 4.5f)
      layout.draw(composer.canvas)
      composer.canvas.restore()
      x += colWidths[idx]
    }
    composer.y += rowH
  }

  private fun drawTableTotalsRow(
    composer: PageComposer,
    cells: List<String>,
    colWidths: FloatArray,
    aligns: List<Layout.Alignment>,
  ) {
    val rowH = 22f
    composer.ensureSpace(rowH)
    val rect = RectF(MARGIN_H, composer.y, PAGE_WIDTH - MARGIN_H, composer.y + rowH)
    val bgPaint = Paint().apply { color = colorWarmBg }
    val borderPaint =
      Paint().apply {
        color = colorCrimson
        style = Paint.Style.STROKE
        strokeWidth = 0.9f
      }
    composer.canvas.drawRect(rect, bgPaint)
    composer.canvas.drawLine(MARGIN_H, composer.y + rowH, PAGE_WIDTH - MARGIN_H, composer.y + rowH, borderPaint)

    val textPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorCrimsonDark
        textSize = 8.8f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }

    var x = MARGIN_H
    cells.forEachIndexed { idx, label ->
      val w = colWidths[idx]
      if (label.isNotEmpty()) {
        drawCellLayout(composer.canvas, label, x + 4f, composer.y + 5f, max(10f, w - 8f), textPaint, aligns[idx])
      }
      x += w
    }
    composer.y += rowH
  }

  private fun drawCellLayout(
    canvas: Canvas,
    text: String,
    x: Float,
    y: Float,
    width: Float,
    paint: TextPaint,
    align: Layout.Alignment,
  ) {
    val layout =
      StaticLayout.Builder.obtain(text, 0, text.length, paint, max(10, width.toInt()))
        .setAlignment(align)
        .setIncludePad(false)
        .build()
    canvas.save()
    canvas.translate(x, y)
    layout.draw(canvas)
    canvas.restore()
  }
}
