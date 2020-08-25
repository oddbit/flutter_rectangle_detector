package id.oddbit.rectangle_detector.scanner.scan

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.media.MediaActionSound
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import id.oddbit.rectangle_detector.scanner.SourceManager
import id.oddbit.rectangle_detector.scanner.base.BaseActivity
import id.oddbit.rectangle_detector.scanner.processor.Corners
import id.oddbit.rectangle_detector.scanner.processor.processPicture
import id.oddbit.rectangle_detector.scanner.crop.CropActivity
import oddbit.rectangle_detector.rectangle_detector.R
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_scan.*
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanActivity : BaseActivity(), SurfaceHolder.Callback, Camera.PictureCallback, Camera.PreviewCallback {
    private var busy: Boolean = false
    private var mCamera: Camera? = null
    private lateinit var mSurfaceHolder: SurfaceHolder
    private lateinit var executor: ExecutorService
    private lateinit var proxySchedule: Scheduler

    override fun provideContentViewId(): Int = R.layout.activity_scan

    override fun initPresenter() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mSurfaceHolder = surface.holder
        mSurfaceHolder.addCallback(this)
        executor = Executors.newSingleThreadExecutor()
        proxySchedule = Schedulers.from(executor)
    }

    override fun prepare() {
        if (!OpenCVLoader.initDebug()) {
            Log.i(TAG, "Loading OpenCV error, EXIT!!!")
            finish()
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE), Companion.REQUEST_CAMERA_PERMISSION)
        } else if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), Companion.REQUEST_CAMERA_PERMISSION)
        } else if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), Companion.REQUEST_CAMERA_PERMISSION)
        }

        shut.setOnClickListener {
            takePicture()
        }
    }

    private fun start() {
        mCamera?.startPreview() ?: Log.i(TAG, "camera null")
    }

    private fun stop() {
        mCamera?.stopPreview() ?: Log.i(TAG, "camera null")
    }

    private fun takePicture() {
        busy = true
        Log.i(TAG, "try to focus")

        mCamera?.autoFocus { b, _ ->
            Log.i(TAG, "focus result: $b")

            mCamera?.takePicture(null, null, this)
            MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
        }
    }

    private fun updateCamera() {
        if (null == mCamera) {
            return
        }
        mCamera?.stopPreview()
        try {
            mCamera?.setPreviewDisplay(mSurfaceHolder)
        } catch (e: IOException) {
            e.printStackTrace()
            return
        }
        mCamera?.setPreviewCallback(this)
        mCamera?.startPreview()
    }

    private fun initCamera() {
        try {
            mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK)
        } catch (e: RuntimeException) {
            e.stackTrace
            Toast.makeText(this, "cannot open camera, please grant camera", Toast.LENGTH_SHORT).show()
            return
        }

        // supported preview sizes
        val supportedPreviewSizes = mCamera?.parameters?.supportedPreviewSizes

        val param = mCamera?.parameters
        val size = firstSupportedPreviewSize()

        val width = mCamera?.parameters?.previewSize?.width ?: 0
        val height = mCamera?.parameters?.previewSize?.height ?: 0

        val surfaceParams = this.surface.layoutParams
        surfaceParams.width = width
        surfaceParams.height = height
        surface.layoutParams = surfaceParams
        paper_rect.layoutParams = surfaceParams

        val supportPicSize = mCamera?.parameters?.supportedPictureSizes
        supportPicSize?.sortByDescending { it.width.times(it.height) }

        val firstPictureSize = supportPicSize?.first()
        firstPictureSize?.let {
            param?.setPictureSize(it.width, it.height)
        }

        val pm = packageManager

        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS)) {
            param?.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            Log.d(TAG, "AutoFocus enabled")
        } else {
            Log.d(TAG, "AutoFocus disabled")
        }

        param?.flashMode = Camera.Parameters.FLASH_MODE_AUTO

        mCamera?.parameters = param
        mCamera?.setDisplayOrientation(90)
    }

    override fun onStart() {
        super.onStart()
        start()
    }

    override fun onStop() {
        super.onStop()
        stop()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == Companion.REQUEST_CAMERA_PERMISSION
                && (grantResults[permissions.indexOf(android.Manifest.permission.CAMERA)] == PackageManager.PERMISSION_GRANTED)) {
            showMessage(R.string.camera_grant)
            initCamera()
            updateCamera()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun surfaceCreated(p0: SurfaceHolder?) {
        initCamera()
    }

    override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
        Log.i(TAG, "surface changed")
        updateCamera()
    }

    override fun surfaceDestroyed(p0: SurfaceHolder?) {
        synchronized(this) {
            mCamera?.stopPreview()
            mCamera?.setPreviewCallback(null)
            mCamera?.release()
            mCamera = null
        }
    }

    override fun onPictureTaken(p0: ByteArray?, p1: Camera?) {
        Log.i(TAG, "on picture taken")
        val result = Observable.just(p0)
                .subscribeOn(proxySchedule)
                .subscribe {
                    val pictureSize = p1?.parameters?.pictureSize
                    Log.i(TAG, "picture size: " + pictureSize.toString())

                    val mat = Mat(Size(pictureSize?.width?.toDouble() ?: 1920.toDouble(),
                            pictureSize?.height?.toDouble() ?: 1080.toDouble()), CvType.CV_8U)
                    mat.put(0, 0, p0)
                    val pic = Imgcodecs.imdecode(mat, Imgcodecs.CV_LOAD_IMAGE_UNCHANGED)

                    Core.rotate(pic, pic, Core.ROTATE_90_CLOCKWISE)
                    mat.release()
                    SourceManager.corners = processPicture(pic)
                    Imgproc.cvtColor(pic, pic, Imgproc.COLOR_RGB2BGRA)
                    SourceManager.pic = pic
                    startActivityForResult(Intent(this, CropActivity::class.java), REQUEST_CROPPED_PATH)
                    busy = false
                }
    }


    override fun onPreviewFrame(p0: ByteArray?, p1: Camera?) {
        if (busy) {
            return
        }

        Log.i(TAG, "on process start")
        busy = true

        val result = Observable.just(p0)
                .observeOn(proxySchedule)
                .subscribe {
                    Log.i(TAG, "start prepare paper")

                    val parameters = p1?.parameters
                    val width = parameters?.previewSize?.width
                    val height = parameters?.previewSize?.height
                    Log.i(TAG, "$width and $height")

                    val yuv = YuvImage(p0,
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
                        e.printStackTrace()
                    }

                    Observable.create<Corners> {
                        val corner = processPicture(img)
                        busy = false

                        if (null != corner && corner.corners.size == 4) {
                            it.onNext(corner)
                        } else {
                            it.onError(Throwable("paper not detected"))
                        }
                    }.observeOn(AndroidSchedulers.mainThread())
                            .subscribe({
                                paper_rect.onCornersDetected(it)
                            }, {
                                paper_rect.onCornersNotDetected()
                            })
                }
    }

    private fun firstSupportedPreviewSize(): Camera.Size? = mCamera?.parameters?.supportedPreviewSizes?.maxBy { it.width }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CROPPED_PATH && resultCode == Activity.RESULT_OK) {
            val intent = Intent()

            intent.putExtra("CroppedPath", data?.getStringExtra("CroppedPath") ?: "")
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 0
        private const val REQUEST_CROPPED_PATH = 1
        private const val EXIT_TIME_THRESHOLD = 2000
    }
}