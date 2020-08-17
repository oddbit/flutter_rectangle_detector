package oddbit.rectangle_detector.rectangle_detector_example

import android.app.Activity
import android.content.Intent
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val GET_SCANNED_DOCUMENT = 1
    private val CHANNEL = "rectangle_detector"
    private var croppedPath = ""

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GET_SCANNED_DOCUMENT && resultCode == Activity.RESULT_OK) {
            croppedPath = data?.getStringExtra("CroppedPath") ?: "Empty"
            val mc = MethodChannel(flutterEngine?.dartExecutor?.binaryMessenger, CHANNEL)

            Log.d("MainActivity", croppedPath)
            mc.invokeMethod("onCroppedPictureCreated", croppedPath)
        }
    }
}
