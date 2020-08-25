package id.oddbit.rectangle_detector.scanner.processor

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

const val TAG: String = "PaperProcessor"

fun processPicture(previewFrame: Mat): Corners? {
    val contours = findContours(previewFrame)

    return getCorners(contours, previewFrame.size())
}

fun cropPicture(picture: Mat, pts: List<Point>): Mat {
    pts.forEach { Log.i(TAG, "point: $it") }
    val tl = pts[0]
    val tr = pts[1]
    val br = pts[2]
    val bl = pts[3]

    val widthA = sqrt((br.x - bl.x).pow(2.0) + (br.y - bl.y).pow(2.0))
    val widthB = sqrt((tr.x - tl.x).pow(2.0) + (tr.y - tl.y).pow(2.0))

    val dw = max(widthA, widthB)
    val maxWidth = java.lang.Double.valueOf(dw)!!.toInt()


    val heightA = sqrt((tr.x - br.x).pow(2.0) + (tr.y - br.y).pow(2.0))
    val heightB = sqrt((tl.x - bl.x).pow(2.0) + (tl.y - bl.y).pow(2.0))

    val dh = max(heightA, heightB)
    val maxHeight = java.lang.Double.valueOf(dh)!!.toInt()

    val croppedPic = Mat(maxHeight, maxWidth, CvType.CV_8UC4)

    val srcMat = Mat(4, 1, CvType.CV_32FC2)
    val dstMat = Mat(4, 1, CvType.CV_32FC2)

    srcMat.put(0, 0, tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
    dstMat.put(0, 0, 0.0, 0.0, dw, 0.0, dw, dh, 0.0, dh)

    val perspectiveMat = Imgproc.getPerspectiveTransform(srcMat, dstMat)

    Imgproc.warpPerspective(picture, croppedPic, perspectiveMat, croppedPic.size())

    perspectiveMat.release()
    srcMat.release()
    dstMat.release()

    Log.i(TAG, "crop finish")

    return croppedPic
}

fun enhancePicture(src: Bitmap?): Bitmap {
    val srcMat = Mat()

    Utils.bitmapToMat(src, srcMat)
    Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_RGBA2GRAY)
    Imgproc.adaptiveThreshold(srcMat, srcMat, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 15, 15.0)

    val result = Bitmap.createBitmap(src?.width ?: 1080, src?.height ?: 1920, Bitmap.Config.RGB_565)

    Utils.matToBitmap(srcMat, result, true)
    srcMat.release()

    return result
}

private fun findContours(src: Mat): ArrayList<MatOfPoint> {
    val kSizeClose = 10.0
    val kernel = Mat(Size(kSizeClose, kSizeClose), CvType.CV_8UC1, Scalar(255.0))
    val grayImage: Mat
    val cannedImage: Mat
    val dilate: Mat

    val size = Size(src.size().width, src.size().height)

    grayImage = Mat(size, CvType.CV_8UC4)
    cannedImage = Mat(size, CvType.CV_8UC1)
    dilate = Mat(size, CvType.CV_8UC1)

    Imgproc.cvtColor(src, grayImage, Imgproc.COLOR_BGR2GRAY, 4)
    Imgproc.GaussianBlur(grayImage, grayImage, Size(5.0, 5.0), 0.0)
    Imgproc.threshold(grayImage, grayImage, 20.0, 255.0, Imgproc.THRESH_TRIANGLE)
    Imgproc.Canny(grayImage, cannedImage, 75.0, 200.0)
    Imgproc.dilate(cannedImage, dilate, kernel)

    // TODO: ORIGINAL
    /*
        var contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(dilate, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE)
        contours.sortByDescending { p: MatOfPoint -> Imgproc.contourArea(p) }
        hierarchy.release()
    */

    val contours = findLargestContours(dilate, 15)

    grayImage.release()
    cannedImage.release()
    kernel.release()
    dilate.release()

    val result = ArrayList<MatOfPoint>()
    for (item in contours!!) {
        result.add(item)
    }

    return result
}

private fun getCorners(contours: ArrayList<MatOfPoint>, size: Size): Corners? {
    val indexTo: Int
    when (contours.size) {
        in 0..5 -> indexTo = contours.size - 1
        else -> indexTo = 4
    }
    for (index in 0..contours.size) {
        if (index in 0..indexTo) {
            val c2f = MatOfPoint2f(*contours[index].toArray())
            val peri = Imgproc.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.03 * peri, true)
            //val area = Imgproc.contourArea(approx)
            val points = approx.toArray().asList()
            var convex = MatOfPoint()
            approx.convertTo(convex, CvType.CV_32S);
            // select biggest 4 angles polygon
            if (points.size == 4 && Imgproc.isContourConvex(convex)){
                val foundPoints = sortPoints(points)
                return Corners(foundPoints, size)
            }
        } else {
            return null
        }
    }

    return null
}

private fun sortPoints(points: List<Point>): List<Point> {
    val p0 = points.minBy { point -> point.x + point.y } ?: Point()
    val p1 = points.minBy { point -> point.y - point.x } ?: Point()
    val p2 = points.maxBy { point -> point.x + point.y } ?: Point()
    val p3 = points.maxBy { point -> point.y - point.x } ?: Point()

    return listOf(p0, p1, p2, p3)
}

private fun findLargestContours(inputMat: Mat, NUM_TOP_CONTOURS: Int): List<MatOfPoint>? {
    val mHierarchy = Mat()
    val mContourList: List<MatOfPoint> = ArrayList()

    //finding contours - as we are sorting by area anyway, we can use RETR_LIST - faster than RETR_EXTERNAL.
    Imgproc.findContours(inputMat, mContourList, mHierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

    // Convert the contours to their Convex Hulls i.e. removes minor nuances in the contour
    val mHullList: MutableList<MatOfPoint> = ArrayList()
    val tempHullIndices = MatOfInt()

    for (i in mContourList.indices) {
        Imgproc.convexHull(mContourList[i], tempHullIndices)
        mHullList.add(hull2Points(tempHullIndices, mContourList[i]))
    }

    // Release mContourList as its job is done
    for (c in mContourList) {
        c.release()
    }
    tempHullIndices.release()
    mHierarchy.release()

    if (mHullList.size != 0) {
        mHullList.sortByDescending { Imgproc.contourArea(it) }
        return mHullList.subList(0, min(mHullList.size, NUM_TOP_CONTOURS))
    }

    return null
}

private fun hull2Points(hull: MatOfInt, contour: MatOfPoint): MatOfPoint {
    val indexes = hull.toList()
    val points: MutableList<Point> = java.util.ArrayList()
    val ctrList = contour.toList()
    for (index in indexes) {
        points.add(ctrList[index])
    }
    val point = MatOfPoint()
    point.fromList(points)
    return point
}
