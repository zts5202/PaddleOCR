package com.paddle.ocr.demo.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.paddle.ocr.model.OCRResult

object DesensitizationUtils {

    private val ID_CARD_PATTERN = Regex("([1-9]\\d{5})(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}([\\dXx])")
    private val PHONE_PATTERN = Regex("(1[3-9]\\d)\\d{4}(\\d{4})")
    private val BANK_CARD_PATTERN = Regex("(\\d{4})\\d{8,11}(\\d{4})")

    /**
     * 对字符串进行敏感数据打码脱敏
     */
    fun maskText(text: String): String {
        var masked = text
        // 身份证打码: 保留前6位和末4位，中间8位用星号代替
        masked = ID_CARD_PATTERN.replace(masked) { m ->
            val prefix = m.groupValues[1]
            val suffix = m.groupValues[2]
            "${prefix}********$suffix"
        }
        // 手机号打码: 保留前3位和末4位，中间4位用星号代替
        masked = PHONE_PATTERN.replace(masked) { m ->
            val prefix = m.groupValues[1]
            val suffix = m.groupValues[2]
            "${prefix}****$suffix"
        }
        // 银行卡打码: 保留前4位和末4位
        masked = BANK_CARD_PATTERN.replace(masked) { m ->
            val prefix = m.groupValues[1]
            val suffix = m.groupValues[2]
            "${prefix}********$suffix"
        }
        return masked
    }

    /**
     * 判断某个文本是否包含敏感信息（手机号、身份证、银行卡）
     */
    fun containsSensitiveInfo(text: String): Boolean {
        return PHONE_PATTERN.containsMatchIn(text) ||
                ID_CARD_PATTERN.containsMatchIn(text) ||
                BANK_CARD_PATTERN.containsMatchIn(text)
    }

    /**
     * 在 Bitmap 图像上对检测到敏感文本的多边形区域打马赛克/黑色遮罩，生成安全脱敏图
     */
    fun createMaskedBitmap(original: Bitmap, results: List<OCRResult>): Bitmap {
        val output = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            color = Color.argb(230, 20, 20, 20) // 半透明深色遮罩打码
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        for (item in results) {
            val pts = item.box.points
            if (containsSensitiveInfo(item.text) && pts.size >= 4) {
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    lineTo(pts[1].x, pts[1].y)
                    lineTo(pts[2].x, pts[2].y)
                    lineTo(pts[3].x, pts[3].y)
                    close()
                }
                canvas.drawPath(path, paint)

                // 绘制【已脱敏】文字标签
                val centerX = (pts[0].x + pts[2].x) / 2f
                val centerY = (pts[0].y + pts[2].y) / 2f + 10f
                canvas.drawText("【已脱敏】", centerX, centerY, textPaint)
            }
        }

        return output
    }
}
