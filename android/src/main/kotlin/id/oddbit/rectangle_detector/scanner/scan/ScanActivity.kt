package id.oddbit.rectangle_detector.scanner.scan

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import id.oddbit.rectangle_detector.scanner.SourceManager
import id.oddbit.rectangle_detector.scanner.base.BaseActivity
import id.oddbit.rectangle_detector.scanner.camera_preview.CameraPreviewViewModel
import id.oddbit.rectangle_detector.scanner.crop.CropActivity
import id.oddbit.rectangle_detector.scanner.processor.Corners
import id.oddbit.rectangle_detector.scanner.processor.processPicture
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_scan.*
import oddbit.rectangle_detector.rectangle_detector.R
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

class ScanActivity : BaseActivity() , Camera.PreviewCallback, Camera.PictureCallback{
    private var busy: Boolean = false
    private var mCamera: Camera? = null
    private lateinit var mSurfaceHolder: SurfaceHolder
    private lateinit var executor: ExecutorService
    private lateinit var proxySchedule: Scheduler
    private lateinit var cameraPreviewViewModel: CameraPreviewViewModel

    override fun provideContentViewId(): Int = R.layout.activity_scan

    override fun initPresenter() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        } else {
            mCamera = getCameraInstance()
            mCamera!!.setPreviewCallback(this)
            cameraPreviewViewModel = CameraPreviewViewModel(this,  fl_camera_preview, shut, paper_rect, mCamera, this)
            cameraPreviewViewModel.startCameraPreview()
            cameraPreviewViewModel.captureImage()
        }

    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 0
        private const val REQUEST_CROPPED_PATH = 1
        private const val EXIT_TIME_THRESHOLD = 2000
    }

    private fun getCameraInstance(): Camera? {
        if (mCamera == null) // mCamera = Camera.open(useBackCamera ? 0 : 1);
            mCamera = Camera.open(0)
        return mCamera
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CROPPED_PATH && resultCode == Activity.RESULT_OK) {
            val intent = Intent()
            intent.putExtra("CroppedPath", data?.getStringExtra("CroppedPath") ?: "")
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }


}