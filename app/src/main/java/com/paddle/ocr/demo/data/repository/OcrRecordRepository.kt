package com.paddle.ocr.demo.data.repository

import com.paddle.ocr.demo.data.dao.OcrRecordDao
import com.paddle.ocr.demo.data.entity.OcrRecordEntity
import kotlinx.coroutines.flow.Flow

class OcrRecordRepository(private val dao: OcrRecordDao) {

    val allRecords: Flow<List<OcrRecordEntity>> = dao.getAllRecords()

    fun search(query: String): Flow<List<OcrRecordEntity>> {
        return if (query.isBlank()) {
            dao.getAllRecords()
        } else {
            dao.searchRecords(query.trim())
        }
    }

    suspend fun insert(record: OcrRecordEntity): Long = dao.insertRecord(record)

    suspend fun insertAll(records: List<OcrRecordEntity>): List<Long> = dao.insertAllRecords(records)

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()
}
