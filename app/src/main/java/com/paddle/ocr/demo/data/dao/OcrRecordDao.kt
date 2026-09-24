package com.paddle.ocr.demo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.paddle.ocr.demo.data.entity.OcrRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrRecordDao {
    @Query("SELECT * FROM ocr_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<OcrRecordEntity>>

    @Query("SELECT * FROM ocr_records WHERE fullText LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchRecords(query: String): Flow<List<OcrRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: OcrRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllRecords(records: List<OcrRecordEntity>): List<Long>

    @Query("DELETE FROM ocr_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ocr_records")
    suspend fun clearAll()
}
