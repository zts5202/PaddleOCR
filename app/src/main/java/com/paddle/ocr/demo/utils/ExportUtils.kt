package com.paddle.ocr.demo.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.paddle.ocr.model.OCRResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtils {

    /**
     * 导出为纯文本 .txt 文件并分享
     */
    fun exportAndShareTxt(
        context: Context,
        results: List<OCRResult>,
        isDesensitized: Boolean = false
    ) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "PaddleOCR_$timestamp.txt"
        val file = File(context.cacheDir, fileName)

        val content = StringBuilder().apply {
            append("=== PaddleOCR 离线文字识别结果 ===\n")
            append("生成时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            if (isDesensitized) {
                append("【已开启敏感信息脱敏保护】\n")
            }
            append("------------------------------------\n\n")
            results.forEachIndexed { index, res ->
                val txt = if (isDesensitized) DesensitizationUtils.maskText(res.text) else res.text
                append("${index + 1}. $txt\n")
            }
        }.toString()

        file.writeText(content)
        shareFile(context, file, "text/plain", "分享 OCR 文本")
    }

    /**
     * 导出为 Markdown .md 格式并分享
     */
    fun exportAndShareMarkdown(
        context: Context,
        results: List<OCRResult>,
        detectionMs: Long,
        recognitionMs: Long,
        totalMs: Long,
        isDesensitized: Boolean = false
    ) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "PaddleOCR_$timestamp.md"
        val file = File(context.cacheDir, fileName)

        val fullText = results.joinToString("\n") { it.text }
        val entities = EntityExtractor.extract(fullText)

        val content = StringBuilder().apply {
            append("# PaddleOCR 离线文字识别报告\n\n")
            append("- **识别时间**：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            append("- **耗时统计**：检测 ${detectionMs}ms | 识别 ${recognitionMs}ms | 总计 ${totalMs}ms\n")
            append("- **识别文本项**：共 ${results.size} 条\n")
            if (isDesensitized) {
                append("- **状态**：`已脱敏安全模式`\n")
            }
            append("\n---\n\n")

            if (entities.isNotEmpty()) {
                append("## 📌 智能提取的关键信息\n\n")
                entities.forEach { ent ->
                    val valStr = if (isDesensitized) DesensitizationUtils.maskText(ent.value) else ent.value
                    append("- **${ent.label}**: `${valStr}`\n")
                }
                append("\n---\n\n")
            }

            append("## 📝 详细文本列表\n\n")
            results.forEachIndexed { index, res ->
                val txt = if (isDesensitized) DesensitizationUtils.maskText(res.text) else res.text
                append("${index + 1}. ${txt}\n")
            }
        }.toString()

        file.writeText(content)
        shareFile(context, file, "text/markdown", "分享 OCR Markdown 报告")
    }

    /**
     * 导出为 PDF 文档 (.pdf) 并分享
     */
    fun exportAndSharePdf(
        context: Context,
        bitmap: Bitmap?,
        results: List<OCRResult>,
        isDesensitized: Boolean = false
    ) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "PaddleOCR_$timestamp.pdf"
        val file = File(context.cacheDir, fileName)

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 尺寸
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(30, 30, 30)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val metaPaint = Paint().apply {
            color = Color.rgb(100, 100, 100)
            textSize = 10f
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.rgb(40, 40, 40)
            textSize = 11f
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.rgb(220, 220, 220)
            strokeWidth = 1f
        }

        var currentY = 40f
        canvas.drawText("PaddleOCR 离线文字识别扫描报告", 40f, currentY, titlePaint)
        currentY += 18f

        val timeStr = "导出时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}"
        val modeStr = if (isDesensitized) " (已脱敏保护)" else ""
        canvas.drawText(timeStr + modeStr, 40f, currentY, metaPaint)
        currentY += 15f

        canvas.drawLine(40f, currentY, 555f, currentY, linePaint)
        currentY += 20f

        // 如果有图片，在 PDF 上半部分绘制缩放预览图（如果脱敏模式则绘制脱敏图）
        if (bitmap != null) {
            val displayBitmap = if (isDesensitized) {
                DesensitizationUtils.createMaskedBitmap(bitmap, results)
            } else {
                bitmap
            }
            val maxImgW = 515f
            val maxImgH = 200f
            val scale = minOf(maxImgW / displayBitmap.width, maxImgH / displayBitmap.height, 1f)
            val scaledW = (displayBitmap.width * scale).toInt()
            val scaledH = (displayBitmap.height * scale).toInt()
            val scaledBmp = Bitmap.createScaledBitmap(displayBitmap, scaledW, scaledH, true)

            canvas.drawBitmap(scaledBmp, 40f, currentY, null)
            currentY += scaledH + 20f
            canvas.drawLine(40f, currentY, 555f, currentY, linePaint)
            currentY += 20f
        }

        canvas.drawText("识别文本内容：", 40f, currentY, titlePaint.apply { textSize = 13f })
        currentY += 18f

        for ((index, item) in results.withIndex()) {
            if (currentY > 800f) break // 避免超出单页高度
            val line = "${index + 1}. " + if (isDesensitized) DesensitizationUtils.maskText(item.text) else item.text
            // 简单的文本截断防止超宽
            val displayLine = if (line.length > 55) line.substring(0, 52) + "..." else line
            canvas.drawText(displayLine, 40f, currentY, textPaint)
            currentY += 16f
        }

        pdfDocument.finishPage(page)

        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        shareFile(context, file, "application/pdf", "分享 OCR PDF 文档")
    }

    /**
     * 批量导出所有图片的识别结果为 .txt 纯文本
     */
    fun exportBatchTxt(
        context: Context,
        items: List<com.paddle.ocr.demo.model.BatchOcrItem>,
        isDesensitized: Boolean = false
    ) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "PaddleOCR_Batch_$timestamp.txt"
        val file = File(context.cacheDir, fileName)

        val content = StringBuilder().apply {
            append("=== PaddleOCR 批量连续识别结果 ===\n")
            append("生成时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            append("图片总数: ${items.size}\n")
            if (isDesensitized) {
                append("【已开启敏感信息脱敏保护】\n")
            }
            append("====================================\n\n")

            items.forEachIndexed { idx, item ->
                append("【第 ${idx + 1} 张 - ${item.fileName}】\n")
                if (item.status == com.paddle.ocr.demo.model.BatchItemStatus.SUCCESS) {
                    append("识别耗时: 检 ${item.detectionMs}ms / 识 ${item.recognitionMs}ms / 共 ${item.totalMs}ms\n")
                    append("识别内容:\n")
                    item.results.forEachIndexed { lineIdx, res ->
                        val txt = if (isDesensitized) DesensitizationUtils.maskText(res.text) else res.text
                        append("${lineIdx + 1}. $txt\n")
                    }
                } else {
                    append("状态: 识别失败 (${item.errorMessage ?: "未知错误"})\n")
                }
                append("\n------------------------------------\n\n")
            }
        }.toString()

        file.writeText(content)
        shareFile(context, file, "text/plain", "批量分享 OCR 识别结果")
    }

    /**
     * 批量导出所有图片的识别结果为 Markdown 格式报告
     */
    fun exportBatchMarkdown(
        context: Context,
        items: List<com.paddle.ocr.demo.model.BatchOcrItem>,
        isDesensitized: Boolean = false
    ) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "PaddleOCR_Batch_$timestamp.md"
        val file = File(context.cacheDir, fileName)

        val totalLines = items.sumOf { it.lineCount }
        val successCount = items.count { it.status == com.paddle.ocr.demo.model.BatchItemStatus.SUCCESS }

        val md = StringBuilder().apply {
            append("# 📄 PaddleOCR 批量连续识别汇总报告\n\n")
            append("> 生成时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}  \n")
            append("> 处理总数: **${items.size}** 张图片 (成功: **$successCount** 张)  \n")
            append("> 识别总文本行: **$totalLines** 行  \n")
            if (isDesensitized) {
                append("> **安全提示**: 已启用敏感个人信息（身份证/手机号/银行卡）打码脱敏保护  \n")
            }
            append("\n---\n\n")

            items.forEachIndexed { idx, item ->
                append("### 🖼️ 第 ${idx + 1} 张图片: ${item.fileName}\n\n")
                if (item.status == com.paddle.ocr.demo.model.BatchItemStatus.SUCCESS) {
                    append("- **文本行数**: ${item.results.size}\n")
                    append("- **性能耗时**: 文本检测 ${item.detectionMs}ms | 字符识别 ${item.recognitionMs}ms | 总计 ${item.totalMs}ms\n\n")
                    append("| 序号 | 识别文字 | 置信度 |\n")
                    append("| :--- | :--- | :--- |\n")
                    item.results.forEachIndexed { lineIdx, r ->
                        val txt = if (isDesensitized) DesensitizationUtils.maskText(r.text) else r.text
                        val conf = "%.2f%%".format(r.confidence * 100)
                        append("| ${lineIdx + 1} | $txt | $conf |\n")
                    }
                } else {
                    append("> ❌ 识别失败: ${item.errorMessage ?: "未知错误"}\n")
                }
                append("\n---\n\n")
            }
        }.toString()

        file.writeText(md)
        shareFile(context, file, "text/markdown", "批量分享 OCR Markdown 报告")
    }

    private fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "com.aistudio.paddleocr.kxmpzq.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
