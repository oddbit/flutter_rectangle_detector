package id.oddbit.rectangle_detector.scanner.utility

import android.app.Activity
import android.hardware.Camera
import android.view.Surface
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

object Utility {
    private val TAG = Utility::class.java.simpleName
    private fun compareFloats(left: Double, right: Double): Boolean {
        val epsilon = 0.00000001
        return abs(left - right) < epsilon
    }

    fun determinePictureSize(camera: Camera?, previewSize: Camera.Size): Camera.Size? {
        if (camera == null) return null
        val cameraParams = camera.parameters
        val pictureSizeList = cameraParams.supportedPictureSizes

        pictureSizeList.sortWith(Comparator { size1, size2 ->
            val h1 = sqrt(size1.width * size1.width + size1.height * size1.height.toDouble())
            val h2 = sqrt(size2.width * size2.width + size2.height * size2.height.toDouble())

            h2.compareTo(h1)
        })
        var retSize: Camera.Size? = null

        // if the preview size is not supported as a picture size
        val reqRatio = previewSize.width.toFloat() / previewSize.height
        var curRatio: Float
        var deltaRatio: Float
        var deltaRatioMin = Float.MAX_VALUE
        for (size in pictureSizeList) {
            curRatio = size.width.toFloat() / size.height
            deltaRatio = Math.abs(reqRatio - curRatio)
            if (deltaRatio < deltaRatioMin) {
                deltaRatioMin = deltaRatio
                retSize = size
            }
            if (compareFloats(deltaRatio.toDouble(), 0.0)) {
                break
            }
        }
        return retSize
    }

    fun getOptimalPreviewSize(camera: Camera?, w: Int, h: Int): Camera.Size? {
        if (camera == null) return null
        val targetRatio = h.toDouble() / w
        val cameraParams = camera.parameters
        val previewSizeList = cameraParams.supportedPreviewSizes
        Collections.sort(previewSizeList, Comparator { size1, size2 ->
            val ratio1 = size1.width.toDouble() / size1.height
            val ratio2 = size2.width.toDouble() / size2.height
            val ratioDiff1 = Math.abs(ratio1 - targetRatio)
            val ratioDiff2 = Math.abs(ratio2 - targetRatio)
            if (compareFloats(ratioDiff1, ratioDiff2)) {
                val h1 = Math.sqrt(size1.width * size1.width + size1.height * size1.height.toDouble())
                val h2 = Math.sqrt(size2.width * size2.width + size2.height * size2.height.toDouble())
                return@Comparator h2.compareTo(h1)
            }
            ratioDiff1.compareTo(ratioDiff2)
        })
        return previewSizeList[0]
    }

    fun configureCameraAngle(activity: Activity): Int {
        val angle: Int
        val display = activity.windowManager.defaultDisplay
        angle = when (display.rotation) {
            Surface.ROTATION_0 -> 90 // This is camera orientation
            Surface.ROTATION_90 -> 0
            Surface.ROTATION_180 -> 270
            Surface.ROTATION_270 -> 180
            else -> 90
        }
        return angle
    }
}
