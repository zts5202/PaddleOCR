package com.paddle.ocr.demo.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ocr_records")
data class OcrRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val formattedDate: String,
    val imagePath: String,
    val fullText: String,
    val resultsJson: String,
    val detectionMs: Long,
    val recognitionMs: Long,
    val totalMs: Long,
    val tags: String = "" // 例如 "手机号, 身份证, 金额"
)
