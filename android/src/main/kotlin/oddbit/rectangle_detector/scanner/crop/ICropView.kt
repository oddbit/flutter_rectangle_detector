package oddbit.rectangle_detector.scanner.crop

import android.widget.ImageView
import oddbit.rectangle_detector.scanner.view.PaperRectangle

class ICropView {
    interface Proxy {
        fun getPaper(): ImageView
        fun getPaperRect(): PaperRectangle
        fun getCroppedPaper(): ImageView
        fun setCroppedResult(path: String)
    }
}