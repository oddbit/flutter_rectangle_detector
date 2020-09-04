package id.oddbit.rectangle_detector.scanner.scan

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import id.oddbit.rectangle_detector.scanner.SourceManager
import id.oddbit.rectangle_detector.scanner.base.BaseActivity
import id.oddbit.rectangle_detector.scanner.crop.CropActivity
import id.oddbit.rectangle_detector.scanner.processor.Corners
import id.oddbit.rectangle_detector.scanner.processor.processPicture
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_scan.*
import oddbit.rectangle_detector.rectangle_detector.R
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanActivity : BaseActivity(), IScanView {
    private var mImageSurfaceView: ScanSurfaceView? = null
    private lateinit var executor: ExecutorService
    private lateinit var proxySchedule: Scheduler

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

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE), Companion.REQUEST_CAMERA_PERMISSION)
        } else if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), Companion.REQUEST_CAMERA_PERMISSION)
        } else if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), Companion.REQUEST_CAMERA_PERMISSION)
        } else {
            mImageSurfaceView = ScanSurfaceView(this, this)
            fl_camera_preview.addView(mImageSurfaceView)
        }

        shut.setOnClickListener {
            mImageSurfaceView?.captureImage()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == Companion.REQUEST_CAMERA_PERMISSION
                && (grantResults[permissions.indexOf(android.Manifest.permission.CAMERA)] == PackageManager.PERMISSION_GRANTED)) {
            showMessage(R.string.camera_grant)
            mImageSurfaceView = ScanSurfaceView(this, this)
            fl_camera_preview.addView(mImageSurfaceView)
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CROPPED_PATH && resultCode == Activity.RESULT_OK) {
            val intent = Intent()

            intent.putExtra("CroppedPath", data?.getStringExtra("CroppedPath") ?: "")
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }

    override fun onCornersDetected(corner: Corners) {
        paper_rect.onCornersDetected(corner)
    }

    override fun onCornersNotDetected() {
        paper_rect.onCornersNotDetected()
    }

    override fun onPictureTake(data: ByteArray?, camera: Camera?) {
        Log.i(TAG, "on picture take")
        val result = Observable.just(data)
                .subscribeOn(proxySchedule)
                .subscribe {
                    val pictureSize = camera?.parameters?.pictureSize
                    Log.i(TAG, "picture size: " + pictureSize.toString())

                    val size = Size(pictureSize?.width?.toDouble() ?: 1920.toDouble(),
                            pictureSize?.height?.toDouble() ?: 1080.toDouble())
                    val mat = Mat(size, CvType.CV_8U)

                    mat.put(0, 0, data)

                    val pic = Imgcodecs.imdecode(mat, Imgcodecs.CV_LOAD_IMAGE_UNCHANGED)

                    Core.rotate(pic, pic, Core.ROTATE_90_CLOCKWISE)
                    mat.release()

                    SourceManager.corners = processPicture(pic)
                    Imgproc.cvtColor(pic, pic, Imgproc.COLOR_RGB2BGRA)
                    SourceManager.pic = pic

                    startActivityForResult(Intent(this, CropActivity::class.java),
                            REQUEST_CROPPED_PATH)
                }
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 0
        private const val REQUEST_CROPPED_PATH = 1
    }
}