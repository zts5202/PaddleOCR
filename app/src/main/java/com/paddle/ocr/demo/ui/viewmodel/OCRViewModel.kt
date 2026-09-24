package com.paddle.ocr.demo.ui.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paddle.ocr.demo.OCRApplication
import com.paddle.ocr.demo.data.database.AppDatabase
import com.paddle.ocr.demo.data.entity.OcrRecordEntity
import com.paddle.ocr.demo.data.repository.OcrRecordRepository
import com.paddle.ocr.demo.model.BatchItemStatus
import com.paddle.ocr.demo.model.BatchOcrItem
import com.paddle.ocr.demo.utils.DesensitizationUtils
import com.paddle.ocr.demo.utils.EntityExtractor
import com.paddle.ocr.demo.utils.FileStorageUtils
import com.paddle.ocr.model.OCRResult
import com.paddle.ocr.model.OCRRunResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OCRViewModel : ViewModel() {

    sealed class UIState {
        data object Loading : UIState()
        data object Ready : UIState()
        data class Processing(val bitmap: Bitmap) : UIState()
        data class Result(val bitmap: Bitmap, val result: OCRRunResult) : UIState()
        data class BatchProcessing(
            val currentIndex: Int,
            val totalCount: Int,
            val currentBitmap: Bitmap?
        ) : UIState()
        data class BatchResult(
            val items: List<BatchOcrItem>,
            val isAllSaved: Boolean = false
        ) : UIState()
        data class Error(val message: String) : UIState()
    }

    data class TimingInfo(
        val detectionMs: Long,
        val recognitionMs: Long,
        val totalMs: Long,
    )

    private val database = AppDatabase.getDatabase(OCRApplication.instance)
    private val repository = OcrRecordRepository(database.ocrRecordDao())

    val historyRecords: StateFlow<List<OcrRecordEntity>> = repository.allRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow<UIState>(
        uiStateForModelState(OCRApplication.instance.modelState.value)
    )
    val uiState: StateFlow<UIState> = _uiState.asStateFlow()

    private val _timing = MutableStateFlow<TimingInfo?>(null)
    val timing: StateFlow<TimingInfo?> = _timing.asStateFlow()

    // 敏感信息脱敏模式开关
    private val _isDesensitized = MutableStateFlow(false)
    val isDesensitized: StateFlow<Boolean> = _isDesensitized.asStateFlow()

    fun toggleDesensitized() {
        _isDesensitized.value = !_isDesensitized.value
    }

    private fun uiStateForModelState(modelState: OCRApplication.ModelState): UIState {
        return when (modelState) {
            is OCRApplication.ModelState.Loading -> UIState.Loading
            is OCRApplication.ModelState.Ready -> UIState.Ready
            is OCRApplication.ModelState.Error -> UIState.Error(modelState.message)
        }
    }

    init {
        viewModelScope.launch {
            OCRApplication.instance.modelState.collect { modelState ->
                when (modelState) {
                    is OCRApplication.ModelState.Loading -> _uiState.value = UIState.Loading
                    is OCRApplication.ModelState.Ready -> {
                        if (_uiState.value is UIState.Loading || _uiState.value is UIState.Error) {
                            _uiState.value = UIState.Ready
                        }
                    }
                    is OCRApplication.ModelState.Error -> _uiState.value = UIState.Error(modelState.message)
                }
            }
        }
    }

    fun onImageSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    OCRApplication.instance.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes != null) {
                    val bitmap = withContext(Dispatchers.IO) {
                        decodeSampledBitmap(bytes, maxWidth = 2048, maxHeight = 2048)
                    }
                    if (bitmap != null) {
                        processBitmap(bitmap)
                    } else {
                        _uiState.value = UIState.Error("无法解析图片")
                    }
                } else {
                    _uiState.value = UIState.Error("无法打开图片文件")
                }
            } catch (e: Exception) {
                _uiState.value = UIState.Error(e.message ?: "未知异常")
            }
        }
    }

    fun onMultipleImagesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            val total = uris.size
            val items = mutableListOf<BatchOcrItem>()
            val ocr = OCRApplication.instance.ocr

            if (ocr == null) {
                _uiState.value = UIState.Error("OCR 引擎尚未就绪，请稍后重试")
                return@launch
            }

            for (i in uris.indices) {
                val uri = uris[i]
                val itemIndex = i + 1
                val fileName = "图片 $itemIndex"

                _uiState.value = UIState.BatchProcessing(
                    currentIndex = itemIndex,
                    totalCount = total,
                    currentBitmap = null
                )

                try {
                    val bytes = withContext(Dispatchers.IO) {
                        OCRApplication.instance.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    if (bytes == null) {
                        items.add(
                            BatchOcrItem(
                                index = itemIndex,
                                uri = uri,
                                fileName = fileName,
                                status = BatchItemStatus.FAILED,
                                errorMessage = "无法读取文件数据"
                            )
                        )
                        continue
                    }

                    val bitmap = withContext(Dispatchers.IO) {
                        decodeSampledBitmap(bytes, maxWidth = 2048, maxHeight = 2048)
                    }

                    if (bitmap == null) {
                        items.add(
                            BatchOcrItem(
                                index = itemIndex,
                                uri = uri,
                                fileName = fileName,
                                status = BatchItemStatus.FAILED,
                                errorMessage = "无法解析图片内容"
                            )
                        )
                        continue
                    }

                    _uiState.value = UIState.BatchProcessing(
                        currentIndex = itemIndex,
                        totalCount = total,
                        currentBitmap = bitmap
                    )

                    val result = withContext(Dispatchers.Default) {
                        ocr.recognize(bitmap)
                    }

                    items.add(
                        BatchOcrItem(
                            index = itemIndex,
                            uri = uri,
                            fileName = fileName,
                            bitmap = bitmap,
                            results = result.results,
                            detectionMs = result.detectionTimeMs,
                            recognitionMs = result.recognitionTimeMs,
                            totalMs = result.totalTimeMs,
                            status = BatchItemStatus.SUCCESS
                        )
                    )
                } catch (e: Exception) {
                    items.add(
                        BatchOcrItem(
                            index = itemIndex,
                            uri = uri,
                            fileName = fileName,
                            status = BatchItemStatus.FAILED,
                            errorMessage = e.message ?: "识别异常"
                        )
                    )
                }
            }

            _uiState.value = UIState.BatchResult(items = items, isAllSaved = false)
        }
    }

    fun saveAllBatchToHistory() {
        val currentState = _uiState.value
        if (currentState !is UIState.BatchResult) return

        viewModelScope.launch {
            val successfulItems = currentState.items.filter {
                it.status == BatchItemStatus.SUCCESS && it.bitmap != null && !it.isSaved
            }

            if (successfulItems.isEmpty()) {
                val msg = if (currentState.items.any { it.isSaved }) "所有成功识别的结果此前均已存入历史" else "未找到成功识别的图片结果"
                Toast.makeText(OCRApplication.instance, msg, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val recordsToInsert = withContext(Dispatchers.IO) {
                successfulItems.mapNotNull { item ->
                    try {
                        val imagePath = FileStorageUtils.saveBitmapToInternalStorage(OCRApplication.instance, item.bitmap!!)
                        val fullText = item.fullText
                        val entities = EntityExtractor.extract(fullText)
                        val tags = entities.joinToString(", ") { it.label }
                        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

                        OcrRecordEntity(
                            formattedDate = formattedDate,
                            imagePath = imagePath,
                            fullText = fullText,
                            resultsJson = "",
                            detectionMs = item.detectionMs,
                            recognitionMs = item.recognitionMs,
                            totalMs = item.totalMs,
                            tags = tags
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            if (recordsToInsert.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    repository.insertAll(recordsToInsert)
                }
                val updatedItems = currentState.items.map { item ->
                    if (successfulItems.any { it.id == item.id }) {
                        item.copy(isSaved = true)
                    } else {
                        item
                    }
                }
                _uiState.value = UIState.BatchResult(items = updatedItems, isAllSaved = true)
                Toast.makeText(
                    OCRApplication.instance,
                    "已成功将全部 ${recordsToInsert.size} 张图片的识别记录存入历史！",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(OCRApplication.instance, "保存失败，请检查存储权限或空间", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun copyAllBatchResults(items: List<BatchOcrItem>, desensitize: Boolean = false) {
        val sb = StringBuilder()
        items.forEachIndexed { idx, item ->
            sb.append("=== 第 ${idx + 1} 张图片 (${item.fileName}) ===\n")
            if (item.status == BatchItemStatus.SUCCESS) {
                item.results.forEachIndexed { lIdx, res ->
                    val line = if (desensitize) DesensitizationUtils.maskText(res.text) else res.text
                    sb.append("${lIdx + 1}. $line\n")
                }
            } else {
                sb.append("(识别失败: ${item.errorMessage})\n")
            }
            sb.append("\n")
        }
        val clipboard = OCRApplication.instance.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("批量 OCR 识别结果", sb.toString().trim()))
        val msg = if (desensitize) "已复制所有图片脱敏结果到剪贴板" else "已复制所有图片识别文本到剪贴板"
        Toast.makeText(OCRApplication.instance, msg, Toast.LENGTH_SHORT).show()
    }

    fun resetToReady() {
        _uiState.value = UIState.Ready
    }

    fun viewBatchItemSingle(item: BatchOcrItem) {
        if (item.bitmap != null && item.status == BatchItemStatus.SUCCESS) {
            val runResult = OCRRunResult(
                results = item.results,
                detectionTimeMs = item.detectionMs,
                recognitionTimeMs = item.recognitionMs,
                totalTimeMs = item.totalMs,
                lineCount = item.lineCount
            )
            _uiState.value = UIState.Result(item.bitmap, runResult)
            _timing.value = TimingInfo(
                detectionMs = item.detectionMs,
                recognitionMs = item.recognitionMs,
                totalMs = item.totalMs
            )
        }
    }

    fun onSampleImageClicked(resId: Int) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                OCRApplication.instance.resources.openRawResource(resId).use { it.readBytes() }
            }
            val bitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            if (bitmap != null) {
                processBitmap(bitmap)
            } else {
                _uiState.value = UIState.Error("加载内置样例图片失败")
            }
        }
    }

    fun processBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = UIState.Processing(bitmap)

            try {
                val ocr = OCRApplication.instance.ocr
                    ?: throw IllegalStateException("OCR 引擎尚未就绪，请稍后")

                val result = withContext(Dispatchers.Default) {
                    ocr.recognize(bitmap)
                }
                _uiState.value = UIState.Result(bitmap, result)
                _timing.value = TimingInfo(
                    detectionMs = result.detectionTimeMs,
                    recognitionMs = result.recognitionTimeMs,
                    totalMs = result.totalTimeMs,
                )

                // 自动保存至 Room 本地历史数据库
                withContext(Dispatchers.IO) {
                    saveToHistory(bitmap, result)
                }
            } catch (e: Exception) {
                _uiState.value = UIState.Error(e.message ?: "OCR 识别出错")
            }
        }
    }

    private suspend fun saveToHistory(bitmap: Bitmap, result: OCRRunResult) {
        try {
            val imagePath = FileStorageUtils.saveBitmapToInternalStorage(OCRApplication.instance, bitmap)
            val fullText = result.results.joinToString("\n") { it.text }
            val entities = EntityExtractor.extract(fullText)
            val tags = entities.joinToString(", ") { it.label }
            val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

            val record = OcrRecordEntity(
                formattedDate = formattedDate,
                imagePath = imagePath,
                fullText = fullText,
                resultsJson = "",
                detectionMs = result.detectionTimeMs,
                recognitionMs = result.recognitionTimeMs,
                totalMs = result.totalTimeMs,
                tags = tags
            )
            repository.insert(record)
        } catch (t: Throwable) {
            // 忽略历史保存微小异常
        }
    }

    fun loadHistoryRecord(record: OcrRecordEntity) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                FileStorageUtils.loadBitmapFromPath(record.imagePath)
            }
            if (bitmap != null) {
                processBitmap(bitmap)
            } else {
                Toast.makeText(OCRApplication.instance, "无法读取该历史图片缓存", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteHistoryRecord(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
        }
    }

    fun retry() {
        val app = OCRApplication.instance
        if (app.isModelLoaded) {
            _uiState.value = UIState.Ready
        } else {
            app.retryLoadModels()
        }
    }

    fun copyAllResults(results: List<OCRResult>, desensitize: Boolean = false) {
        val text = results.joinToString("\n") {
            if (desensitize) DesensitizationUtils.maskText(it.text) else it.text
        }
        val clipboard = OCRApplication.instance.getSystemService(Context.CLIPBOARD_SERVICE)
                as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OCR 识别结果", text))
        val msg = if (desensitize) "已复制已脱敏保护文本到剪贴板" else "已复制所有识别文本到剪贴板"
        Toast.makeText(OCRApplication.instance, msg, Toast.LENGTH_SHORT).show()
    }

    private fun decodeSampledBitmap(
        bytes: ByteArray,
        maxWidth: Int,
        maxHeight: Int,
    ): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val (origW, origH) = opts.outWidth to opts.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sampleSize = 1
        while (sampleSize * 2 <= maxOf(origW / maxWidth, origH / maxHeight)) {
            sampleSize *= 2
        }

        return BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }.let { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
    }
}
