package com.paddle.ocr.demo.ui.screen

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paddle.ocr.demo.R
import com.paddle.ocr.demo.ui.camera.CameraCaptureDialog
import com.paddle.ocr.demo.ui.component.*
import com.paddle.ocr.demo.ui.history.HistoryDialog
import com.paddle.ocr.demo.ui.viewmodel.OCRViewModel
import com.paddle.ocr.demo.utils.EntityExtractor

private val SAMPLE_IMAGES = listOf(
    R.drawable.sample_receipt,
    R.drawable.sample_sign,
    R.drawable.sample_document,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: OCRViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val timing by viewModel.timing.collectAsState()
    val isDesensitized by viewModel.isDesensitized.collectAsState()
    val historyRecords by viewModel.historyRecords.collectAsState()

    var showCameraDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var editBitmapTarget by remember { mutableStateOf<Bitmap?>(null) }

    // Single image picker
    val singleGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.onImageSelected(it) } }

    // Multiple images batch picker
    val batchGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            viewModel.onMultipleImagesSelected(uris)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (state is OCRViewModel.UIState.Result || state is OCRViewModel.UIState.BatchResult) {
                        IconButton(
                            onClick = { viewModel.resetToReady() },
                            modifier = Modifier.testTag("nav_back_home_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回主页")
                        }
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "PaddleOCR 离线识别",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.extraSmall,
                        ) {
                            Text(
                                text = "v1.2.0",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                },
                actions = {
                    // History icon with badge
                    IconButton(
                        onClick = { showHistoryDialog = true },
                        modifier = Modifier.testTag("top_history_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (historyRecords.isNotEmpty()) {
                                    Badge { Text("${historyRecords.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.History, contentDescription = "历史记录")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val s = state) {
                is OCRViewModel.UIState.Loading -> {
                    LoadingOverlay("正在加载 OCR 离线模型...")
                }

                is OCRViewModel.UIState.Ready -> {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Spacer(modifier = Modifier.height(24.dp))
                        ImagePicker(
                            onCameraClick = { showCameraDialog = true },
                            onGalleryClick = {
                                singleGalleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onBatchGalleryClick = {
                                batchGalleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onSampleClick = { viewModel.onSampleImageClicked(it) },
                            sampleImages = SAMPLE_IMAGES,
                        )
                    }
                }

                is OCRViewModel.UIState.Processing -> {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Spacer(modifier = Modifier.height(16.dp))
                        ImagePreview(bitmap = s.bitmap, results = emptyList())
                        Spacer(modifier = Modifier.height(16.dp))
                        LoadingOverlay("正在进行端侧离线检测与识别...")
                    }
                }

                is OCRViewModel.UIState.BatchProcessing -> {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Spacer(modifier = Modifier.height(20.dp))
                        BatchProcessingOverlay(
                            currentIndex = s.currentIndex,
                            totalCount = s.totalCount,
                            currentBitmap = s.currentBitmap
                        )
                    }
                }

                is OCRViewModel.UIState.BatchResult -> {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        BatchResultView(
                            items = s.items,
                            isAllSaved = s.isAllSaved,
                            isDesensitized = isDesensitized,
                            onToggleDesensitized = { viewModel.toggleDesensitized() },
                            onSaveAllToHistory = { viewModel.saveAllBatchToHistory() },
                            onCopyAllResults = { viewModel.copyAllBatchResults(s.items, isDesensitized) },
                            onViewSingleItem = { item -> viewModel.viewBatchItemSingle(item) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick selection footer
                        ImagePicker(
                            onCameraClick = { showCameraDialog = true },
                            onGalleryClick = {
                                singleGalleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onBatchGalleryClick = {
                                batchGalleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onSampleClick = { viewModel.onSampleImageClicked(it) },
                            sampleImages = SAMPLE_IMAGES,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                is OCRViewModel.UIState.Result -> {
                    val fullText = remember(s.result.results) {
                        s.result.results.joinToString("\n") { it.text }
                    }
                    val extractedEntities = remember(fullText) {
                        EntityExtractor.extract(fullText)
                    }

                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        ImagePreview(
                            bitmap = s.bitmap,
                            results = s.result.results,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (timing != null) {
                            TimingBar(
                                detectionMs = timing!!.detectionMs,
                                recognitionMs = timing!!.recognitionMs,
                                totalMs = timing!!.totalMs,
                            )
                        }

                        // Toolbar: Image Enhance/Filter, Privacy Desensitization Mode, Multi-format Export
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { editBitmapTarget = s.bitmap },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("edit_image_button")
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("图像调优")
                            }

                            FilterChip(
                                selected = isDesensitized,
                                onClick = { viewModel.toggleDesensitized() },
                                label = { Text(if (isDesensitized) "已脱敏保护" else "脱敏打码") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                modifier = Modifier.testTag("toggle_desensitize_chip")
                            )

                            OutlinedButton(
                                onClick = { showExportDialog = true },
                                modifier = Modifier.testTag("open_export_dialog_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("导出")
                            }
                        }

                        // Extracted Entities Card (Regex Entity Extractor)
                        if (extractedEntities.isNotEmpty()) {
                            EntityExtractCard(
                                entities = extractedEntities,
                                isDesensitized = isDesensitized,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Results List with desensitization support
                        ResultList(
                            results = s.result.results,
                            isDesensitized = isDesensitized,
                            onCopyAll = { viewModel.copyAllResults(s.result.results, isDesensitized) },
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick action to capture or pick another image
                        ImagePicker(
                            onCameraClick = { showCameraDialog = true },
                            onGalleryClick = {
                                singleGalleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onBatchGalleryClick = {
                                batchGalleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onSampleClick = { viewModel.onSampleImageClicked(it) },
                            sampleImages = SAMPLE_IMAGES,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Export Dialog
                    if (showExportDialog) {
                        ExportDialog(
                            bitmap = s.bitmap,
                            results = s.result.results,
                            detectionMs = timing?.detectionMs ?: 0L,
                            recognitionMs = timing?.recognitionMs ?: 0L,
                            totalMs = timing?.totalMs ?: 0L,
                            isInitialDesensitized = isDesensitized,
                            onDismiss = { showExportDialog = false }
                        )
                    }
                }

                is OCRViewModel.UIState.Error -> {
                    LoadingOverlay("发生异常")
                    ErrorDialog(
                        message = s.message,
                        onRetry = { viewModel.retry() },
                        onDismiss = { viewModel.retry() },
                    )
                }
            }
        }
    }

    // Camera Capture Dialog
    if (showCameraDialog) {
        CameraCaptureDialog(
            onDismiss = { showCameraDialog = false },
            onImageCaptured = { bitmap ->
                showCameraDialog = false
                viewModel.processBitmap(bitmap)
            }
        )
    }

    // Image Edit / Filter / Auto Perspective Dialog
    editBitmapTarget?.let { bitmap ->
        ImageEditDialog(
            initialBitmap = bitmap,
            onDismiss = { editBitmapTarget = null },
            onApply = { editedBitmap ->
                editBitmapTarget = null
                viewModel.processBitmap(editedBitmap)
            }
        )
    }

    // History Records Dialog
    if (showHistoryDialog) {
        HistoryDialog(
            records = historyRecords,
            onDismiss = { showHistoryDialog = false },
            onSelectRecord = { record ->
                viewModel.loadHistoryRecord(record)
            },
            onDeleteRecord = { id ->
                viewModel.deleteHistoryRecord(id)
            },
            onClearAll = {
                viewModel.clearAllHistory()
            }
        )
    }
}
