package com.paddle.ocr.demo.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paddle.ocr.demo.utils.DesensitizationUtils
import com.paddle.ocr.demo.utils.EntityType
import com.paddle.ocr.demo.utils.ExtractedEntity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntityExtractCard(
    entities: List<ExtractedEntity>,
    isDesensitized: Boolean,
    modifier: Modifier = Modifier
) {
    if (entities.isEmpty()) return

    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🎯 关键要素智能提取 (${entities.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entities.forEach { entity ->
                    val displayValue = if (isDesensitized) {
                        DesensitizationUtils.maskText(entity.value)
                    } else {
                        entity.value
                    }

                    AssistChip(
                        onClick = {
                            when (entity.type) {
                                EntityType.PHONE -> {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${entity.value}")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        copyToClipboard(context, entity.value, "手机号")
                                    }
                                }
                                EntityType.EMAIL -> {
                                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${entity.value}")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        copyToClipboard(context, entity.value, "电子邮箱")
                                    }
                                }
                                else -> {
                                    copyToClipboard(context, entity.value, entity.label)
                                }
                            }
                        },
                        label = {
                            Text("${entity.label}: $displayValue", style = MaterialTheme.typography.bodySmall)
                        },
                        leadingIcon = {
                            val icon = when (entity.type) {
                                EntityType.PHONE -> Icons.Default.Call
                                EntityType.ID_CARD -> Icons.Default.Pin
                                EntityType.EMAIL -> Icons.Default.Email
                                EntityType.AMOUNT -> Icons.Default.Paid
                                EntityType.TRACKING_NUMBER -> Icons.Default.LocalShipping
                                EntityType.BANK_CARD -> Icons.Default.CreditCard
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(14.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "已复制 $label 到剪贴板", Toast.LENGTH_SHORT).show()
}
