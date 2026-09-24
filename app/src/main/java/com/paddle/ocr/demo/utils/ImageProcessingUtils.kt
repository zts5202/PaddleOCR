package com.paddle.ocr.demo.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.sqrt

enum class ImageFilterType(val displayName: String) {
    ORIGINAL("原图"),
    ENHANCE("增强锐化"),
    BLACK_WHITE("黑白文档"),
    REMOVE_SHADOW("增白去阴影")
}

object ImageProcessingUtils {

    /**
     * 图像旋转
     */
    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360 == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * 手动矩形裁切
     */
    fun cropBitmap(source: Bitmap, cropRect: Rect): Bitmap {
        val left = cropRect.left.coerceIn(0, source.width - 1)
        val top = cropRect.top.coerceIn(0, source.height - 1)
        val width = cropRect.width().coerceIn(1, source.width - left)
        val height = cropRect.height().coerceIn(1, source.height - top)
        return Bitmap.createBitmap(source, left, top, width, height)
    }

    /**
     * 应用滤镜
     */
    fun applyFilter(source: Bitmap, filter: ImageFilterType): Bitmap {
        if (filter == ImageFilterType.ORIGINAL) return source

        val mat = Mat()
        Utils.bitmapToMat(source, mat)

        val resultMat = Mat()

        try {
            when (filter) {
                ImageFilterType.ENHANCE -> {
                    val lab = Mat()
                    Imgproc.cvtColor(mat, lab, Imgproc.COLOR_RGBA2RGB)
                    Imgproc.cvtColor(lab, lab, Imgproc.COLOR_RGB2Lab)
                    val channels = ArrayList<Mat>()
                    Core.split(lab, channels)

                    val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
                    clahe.apply(channels[0], channels[0])
                    Core.merge(channels, lab)

                    Imgproc.cvtColor(lab, resultMat, Imgproc.COLOR_Lab2RGB)
                    Imgproc.cvtColor(resultMat, resultMat, Imgproc.COLOR_RGB2RGBA)
                    lab.release()
                    channels.forEach { m -> m.release() }
                }

                ImageFilterType.BLACK_WHITE -> {
                    val gray = Mat()
                    Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
                    Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)
                    Imgproc.adaptiveThreshold(
                        gray,
                        resultMat,
                        255.0,
                        Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                        Imgproc.THRESH_BINARY,
                        15,
                        10.0
                    )
                    Imgproc.cvtColor(resultMat, resultMat, Imgproc.COLOR_GRAY2RGBA)
                    gray.release()
                }

                ImageFilterType.REMOVE_SHADOW -> {
                    val gray = Mat()
                    Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)

                    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(25.0, 25.0))
                    val background = Mat()
                    Imgproc.morphologyEx(gray, background, Imgproc.MORPH_DILATE, kernel)
                    Imgproc.medianBlur(background, background, 21)

                    val diff = Mat()
                    Core.absdiff(gray, background, diff)
                    Core.bitwise_not(diff, diff)

                    val norm = Mat()
                    Core.normalize(diff, norm, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8UC1)
                    Imgproc.cvtColor(norm, resultMat, Imgproc.COLOR_GRAY2RGBA)

                    gray.release()
                    kernel.release()
                    background.release()
                    diff.release()
                    norm.release()
                }

                else -> {
                    mat.copyTo(resultMat)
                }
            }

            val outBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultMat, outBitmap)
            return outBitmap
        } catch (e: Exception) {
            return source
        } finally {
            mat.release()
            resultMat.release()
        }
    }

    /**
     * 自动检测文档纸张边缘，并进行透视变换矫正（拉平梯形畸变）
     */
    fun autoPerspectiveCorrection(source: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(source, mat)

        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        val edged = Mat()
        Imgproc.Canny(gray, edged, 50.0, 150.0)

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(edged, edged, kernel)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edged, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        var docContour: MatOfPoint2f? = null
        var maxArea = (source.width.toDouble() * source.height.toDouble() * 0.1)

        for (c in contours) {
            val area = Imgproc.contourArea(c)
            if (area > maxArea) {
                val pts = c.toArray()
                val c2f = MatOfPoint2f(*pts)
                val peri = Imgproc.arcLength(c2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
                if (approx.total() == 4L) {
                    docContour = approx
                    maxArea = area
                }
            }
        }

        if (docContour == null) {
            mat.release()
            gray.release()
            edged.release()
            kernel.release()
            hierarchy.release()
            contours.forEach { m -> m.release() }
            return source
        }

        val points = docContour.toArray()
        val ordered = orderPoints(points)

        val tl = ordered[0]
        val tr = ordered[1]
        val br = ordered[2]
        val bl = ordered[3]

        val widthA = distance(br, bl)
        val widthB = distance(tr, tl)
        val maxWidth = max(widthA, widthB).toInt().coerceAtLeast(100)

        val heightA = distance(tr, br)
        val heightB = distance(tl, bl)
        val maxHeight = max(heightA, heightB).toInt().coerceAtLeast(100)

        val srcMat = MatOfPoint2f(tl, tr, br, bl)
        val dstMat = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((maxWidth - 1).toDouble(), 0.0),
            Point((maxWidth - 1).toDouble(), (maxHeight - 1).toDouble()),
            Point(0.0, (maxHeight - 1).toDouble())
        )

        val transform = Imgproc.getPerspectiveTransform(srcMat, dstMat)
        val warped = Mat()
        Imgproc.warpPerspective(mat, warped, transform, Size(maxWidth.toDouble(), maxHeight.toDouble()))

        val outBitmap = Bitmap.createBitmap(maxWidth, maxHeight, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(warped, outBitmap)

        mat.release()
        gray.release()
        edged.release()
        kernel.release()
        hierarchy.release()
        contours.forEach { m -> m.release() }
        srcMat.release()
        dstMat.release()
        transform.release()
        warped.release()

        return outBitmap
    }

    private fun distance(p1: Point, p2: Point): Double {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun orderPoints(pts: Array<Point>): Array<Point> {
        var tl = pts[0]
        var br = pts[0]
        var tr = pts[0]
        var bl = pts[0]

        var minSum = pts[0].x + pts[0].y
        var maxSum = minSum
        var minDiff = pts[0].y - pts[0].x
        var maxDiff = minDiff

        for (p in pts) {
            val sum = p.x + p.y
            if (sum < minSum) {
                minSum = sum
                tl = p
            }
            if (sum > maxSum) {
                maxSum = sum
                br = p
            }

            val diff = p.y - p.x
            if (diff < minDiff) {
                minDiff = diff
                tr = p
            }
            if (diff > maxDiff) {
                maxDiff = diff
                bl = p
            }
        }

        return arrayOf(tl, tr, br, bl)
    }
}
