package com.paddle.ocr.demo.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileStorageUtils {

    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap): String {
        val dir = File(context.filesDir, "ocr_history").apply { if (!exists()) mkdirs() }
        val filename = "img_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())}.jpg"
        val file = File(dir, filename)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }

    fun loadBitmapFromPath(path: String): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(path)
        } catch (t: Throwable) {
            null
        }
    }
}
