package com.paddle.ocr.demo.model

import android.graphics.Bitmap
import android.net.Uri
import com.paddle.ocr.model.OCRResult
import java.util.UUID

enum class BatchItemStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED
}

data class BatchOcrItem(
    val id: String = UUID.randomUUID().toString(),
    val index: Int,
    val uri: Uri,
    val fileName: String = "图片 $index",
    val bitmap: Bitmap? = null,
    val results: List<OCRResult> = emptyList(),
    val detectionMs: Long = 0L,
    val recognitionMs: Long = 0L,
    val totalMs: Long = 0L,
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val errorMessage: String? = null,
    val isSaved: Boolean = false
) {
    val fullText: String
        get() = results.joinToString("\n") { it.text }

    val lineCount: Int
        get() = results.size
}
