package id.oddbit.rectangle_detector.scanner.processor

import org.opencv.core.Point
import org.opencv.core.Size

data class Corners(val corners: List<Point?>, val size: Size)