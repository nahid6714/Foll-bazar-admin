package com.folbazar.admin.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Robust receipt exporter. Files are written inside a FileProvider-approved cache path. */
enum class ReceiptPaperSize(val label: String, val widthPx: Int, val pageHeightPx: Int) {
    A4("A4", 794, 1123),
    A5("A5", 559, 794),
    THERMAL_80("থার্মাল ৮০মিমি", 384, 900),
    THERMAL_58("থার্মাল ৫৮মিমি", 280, 900)
}

object OrderReceiptExporter {
    fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    private fun receiptDir(context: Context): File = File(context.cacheDir, "receipts").apply { mkdirs() }

    private fun paint(size: Float, bold: Boolean = false): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun lines(order: Order, items: List<OrderItem>): List<String> {
        val out = mutableListOf<String>()
        out += "ফল বাজার"
        out += "অর্ডার #${order.orderNumber}"
        out += "কাস্টমার: ${order.customer}"
        out += "ফোন: ${order.phone}"
        out += "তারিখ: ${order.createdAt ?: "-"}"
        out += "--------------------------------"
        items.forEach { item ->
            val variant = item.variantLabel?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
            out += "${item.productName}$variant x${item.quantity}"
            out += "৳ ${money(item.lineTotal)}"
        }
        out += "--------------------------------"
        out += "Subtotal: ৳ ${money(order.subtotal)}"
        out += "Delivery: ৳ ${money(order.deliveryCharge)}"
        out += "Discount: ৳ ${money(order.discount)}"
        out += "TOTAL: ৳ ${money(order.total)}"
        out += "Payment: ${order.paymentMethod}"
        out += "Status: ${order.status}"
        return out
    }

    fun exportImage(activity: Activity, order: Order, items: List<OrderItem>, size: ReceiptPaperSize): File {
        val width = size.widthPx
        val textSize = if (width < 350) 22f else 24f
        val rowHeight = (textSize * 1.65f).toInt()
        val content = lines(order, items)
        val height = maxOf(size.pageHeightPx, 80 + content.size * rowHeight)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val normal = paint(textSize)
        val bold = paint(textSize, true)
        var y = 45f
        content.forEachIndexed { index, line ->
            canvas.drawText(line, 24f, y, if (index == 0 || line.startsWith("TOTAL:")) bold else normal)
            y += rowHeight
        }
        val file = File(receiptDir(activity), "receipt_${safe(order.orderNumber)}_${stamp()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    fun exportPdf(activity: Activity, order: Order, items: List<OrderItem>, size: ReceiptPaperSize): File {
        val document = PdfDocument()
        val pageWidth = size.widthPx
        val pageHeight = maxOf(size.pageHeightPx, 80 + lines(order, items).size * 34)
        val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        val content = lines(order, items)
        val normal = paint(if (pageWidth < 350) 18f else 20f)
        val bold = paint(if (pageWidth < 350) 18f else 20f, true)
        var y = 36f
        content.forEachIndexed { index, line ->
            canvas.drawText(line, 18f, y, if (index == 0 || line.startsWith("TOTAL:")) bold else normal)
            y += 30f
        }
        document.finishPage(page)
        val file = File(receiptDir(activity), "receipt_${safe(order.orderNumber)}_${stamp()}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    fun shareFile(context: Context, file: File, mime: String) {
        require(file.exists()) { "Receipt file was not created" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "রশিদ সেভ / শেয়ার করুন"))
    }

    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9_-]"), "_")
    private fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    private fun money(v: Double): String = String.format(Locale.US, "%.2f", v)
}
