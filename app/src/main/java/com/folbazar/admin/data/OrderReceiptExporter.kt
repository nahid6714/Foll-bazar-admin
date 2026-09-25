package com.folbazar.admin.data

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import android.content.Intent
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Receipt generator. Files are generated in an app cache directory first, then copied to Downloads on Android 10+. */
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
    private fun paint(size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    private fun money(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun safe(v: String) = v.replace(Regex("[^A-Za-z0-9_-]"), "_")
    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun lines(order: Order, items: List<OrderItem>): List<String> = buildList {
        add("ফল বাজার")
        add("অর্ডার #${order.orderNumber}")
        add("কাস্টমার: ${order.customer}")
        add("ফোন: ${order.phone}")
        add("তারিখ: ${order.createdAt ?: "-"}")
        add("--------------------------------")
        items.forEach { item ->
            val variant = item.variantLabel?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
            add("${item.productName}$variant x${item.quantity}")
            add("৳ ${money(item.lineTotal)}")
        }
        add("--------------------------------")
        add("Subtotal: ৳ ${money(order.subtotal)}")
        add("Delivery: ৳ ${money(order.deliveryCharge)}")
        add("Discount: ৳ ${money(order.discount)}")
        add("TOTAL: ৳ ${money(order.total)}")
        add("Payment: ${order.paymentMethod}")
        add("Status: ${order.status}")
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
        val content = lines(order, items)
        val pageWidth = size.widthPx
        val pageHeight = maxOf(size.pageHeightPx, 80 + content.size * 34)
        val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)
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

    fun saveImageToDownloads(context: Context, source: File) = saveToDownloads(context, source, "image/png", "image/png")
    fun savePdfToDownloads(context: Context, source: File) = saveToDownloads(context, source, "application/pdf", "application/pdf")

    private fun saveToDownloads(context: Context, source: File, mime: String, contentType: String) {
        require(source.exists()) { "Receipt file was not created" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, source.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Fol Bazar Receipts")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Downloads folder-এ ফাইল তৈরি করা যায়নি")
            try {
                val outStream = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Downloads output stream পাওয়া যায়নি")
                outStream.use { out ->
                    source.inputStream().use { input -> input.copyTo(out) }
                }
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }.also {
                    context.contentResolver.update(uri, it, null, null)
                }
                Toast.makeText(context, "রশিদ Downloads/Fol Bazar Receipts-এ সেভ হয়েছে", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw e
            }
        } else {
            // Android 8/9 fallback: share from the approved FileProvider cache path.
            shareFile(context, source, mime)
        }
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
}
