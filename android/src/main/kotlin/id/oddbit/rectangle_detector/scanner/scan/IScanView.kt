package id.oddbit.rectangle_detector.scanner.scan

import android.hardware.Camera
import id.oddbit.rectangle_detector.scanner.processor.Corners

interface IScanView {
    fun onCornersDetected(corner: Corners)
    fun onCornersNotDetected()
    fun onPictureTake(data: ByteArray?, camera: Camera?)
}
