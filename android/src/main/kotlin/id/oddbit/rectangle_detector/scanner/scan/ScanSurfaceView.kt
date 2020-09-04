package id.oddbit.rectangle_detector.scanner.scan

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.hardware.Camera.*
import android.media.AudioManager
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import id.oddbit.rectangle_detector.scanner.processor.Corners
import id.oddbit.rectangle_detector.scanner.processor.processPicture
import id.oddbit.rectangle_detector.scanner.utility.Utility
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import java.io.ByteArrayOutputStream
import java.io.IOException

class ScanSurfaceView(
        context: Context?,
        iScanView: IScanView
    ) : FrameLayout(context), SurfaceHolder.Callback {

    private var mSurfaceView: SurfaceView = SurfaceView(context)
    private var vWidth = 0
    private var vHeight = 0

    private var camera: Camera? = null
    private var previewSize: Size? = null
    private var iScanView: IScanView

    init {
        addView(mSurfaceView)
        val surfaceHolder = mSurfaceView.holder
        surfaceHolder.addCallback(this)
        this.iScanView = iScanView
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        try {
            requestLayout()
            openCamera()
            camera!!.setPreviewDisplay(holder)
        } catch (e: IOException) {
            Log.e(TAG, e.message, e)
        }
    }

    private fun openCamera() {
        if (camera == null) {
            val info = CameraInfo()
            var defaultCameraId = 0

            for (i in 0 until getNumberOfCameras()) {
                getCameraInfo(i, info)

                if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
                    defaultCameraId = i
                }
            }

            camera = open(defaultCameraId)
            val cameraParams = camera!!.parameters
            val flashModes = cameraParams.supportedFlashModes

            if (flashModes != null && flashModes.contains(Parameters.FLASH_MODE_AUTO)) {
                cameraParams.flashMode = Parameters.FLASH_MODE_AUTO
            }

            camera!!.parameters = cameraParams
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (vWidth == vHeight) {
            return
        }
        if (previewSize == null) {
            previewSize = Utility.getOptimalPreviewSize(camera, vWidth, vHeight)
        }

        val parameters = camera!!.parameters

        camera!!.setDisplayOrientation(Utility.configureCameraAngle(context as Activity))
        parameters.setPreviewSize(previewSize!!.width, previewSize!!.height)

        if (parameters.supportedFocusModes != null
                && parameters.supportedFocusModes.contains(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.focusMode = Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
        } else if (parameters.supportedFocusModes != null
                && parameters.supportedFocusModes.contains(Parameters.FOCUS_MODE_AUTO)) {
            parameters.focusMode = Parameters.FOCUS_MODE_AUTO
        }

        val size: Size = Utility.determinePictureSize(camera, parameters.previewSize)!!

        parameters.setPictureSize(size.width, size.height)

        parameters.pictureFormat = ImageFormat.JPEG
        camera!!.parameters = parameters

        requestLayout()
        setPreviewCallback()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopPreviewAndFreeCamera()
    }

    private fun stopPreviewAndFreeCamera() {
        if (camera != null) {
            // Call stopPreview() to stop updating the preview surface.
            camera!!.stopPreview()
            camera!!.setPreviewCallback(null)
            // Important: Call release() to release the camera for use by other
            // applications. Applications should release the camera immediately
            // during onPause() and re-open() it during onResume()).
            camera!!.release()
            camera = null
        }
    }

    private fun setPreviewCallback() {
        camera!!.startPreview()
        camera!!.setPreviewCallback(previewCallback)
    }

    private val previewCallback = PreviewCallback { data, camera ->
        if (camera != null) {
            try {
                val parameters = camera.parameters
                val width = parameters?.previewSize?.width
                val height = parameters?.previewSize?.height
                Log.i(TAG, "$width and $height")

                val yuv = YuvImage(data,
                        parameters?.previewFormat ?: 0,
                        width ?: 320,
                        height ?: 480,
                        null)
                val out = ByteArrayOutputStream()

                yuv.compressToJpeg(Rect(0, 0,
                        width ?: 320,
                        height ?: 480),
                        100, out)
                val bytes = out.toByteArray()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                val img = Mat()
                Utils.bitmapToMat(bitmap, img)
                bitmap.recycle()
                Core.rotate(img, img, Core.ROTATE_90_CLOCKWISE)

                try {
                    out.close()
                } catch (e: IOException) {
                    Log.e(TAG, e.message)
                    e.printStackTrace()
                }

                Observable.create<Corners> {
                        val corner = processPicture(img)

                        if (null != corner && corner.corners.size == 4) {
                            it.onNext(corner)
                        } else {
                            it.onError(Throwable("paper not detected"))
                        }
                    }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                        iScanView.onCornersDetected(it)
                    }, {
                        iScanView.onCornersNotDetected()
                    })

            } catch (e: Exception) {
                Log.e(TAG, e.message)
            }
        }
    }

    private val pictureCallback = PictureCallback { data, camera ->
        camera.stopPreview()
        iScanView.onPictureTake(data, camera)
    }

    private val mShutterCallBack = ShutterCallback {
        if (context != null) {
            val mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            mAudioManager.playSoundEffect(AudioManager.FLAG_PLAY_SOUND)
        }
    }

    fun captureImage() {
        try {
            camera!!.takePicture(mShutterCallBack, null, pictureCallback)
            camera!!.setPreviewCallback(null)
        } catch (e: Exception) {
            Log.e(TAG, e.message)
            e.printStackTrace()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.
        vWidth = View.resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        vHeight = View.resolveSize(suggestedMinimumHeight, heightMeasureSpec)
        setMeasuredDimension(vWidth, vHeight)
        previewSize = Utility.getOptimalPreviewSize(camera, vWidth, vHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (childCount > 0) {
            val width = r - l
            val height = b - t
            var previewWidth = width
            var previewHeight = height

            if (previewSize != null) {
                previewWidth = previewSize!!.width
                previewHeight = previewSize!!.height
                val displayOrientation: Int = Utility.configureCameraAngle(context as Activity)

                if (displayOrientation == 90 || displayOrientation == 270) {
                    previewWidth = previewSize!!.height
                    previewHeight = previewSize!!.width
                }

                Log.d(TAG, "previewWidth:$previewWidth previewHeight:$previewHeight")
            }

            val nW: Int
            val nH: Int
            val top: Int
            val left: Int
            val scale = 1.0f

            // Center the child SurfaceView within the parent.
            if (width * previewHeight < height * previewWidth) {
                Log.d(TAG, "center horizontally")
                val scaledChildWidth = (previewWidth * height / previewHeight * scale).toInt()
                nW = (width + scaledChildWidth) / 2
                nH = (height * scale).toInt()
                top = 0
                left = (width - scaledChildWidth) / 2
            } else {
                Log.d(TAG, "center vertically")
                val scaledChildHeight = (previewHeight * width / previewWidth * scale).toInt()
                nW = (width * scale).toInt()
                nH = (height + scaledChildHeight) / 2
                top = (height - scaledChildHeight) / 2
                left = 0
            }

            mSurfaceView.layout(left, top, nW, nH)

            Log.d("layout", "left:$left")
            Log.d("layout", "top:$top")
            Log.d("layout", "right:$nW")
            Log.d("layout", "bottom:$nH")
        }
    }

    companion object {
        private val TAG = ScanSurfaceView::class.java.simpleName
    }
}