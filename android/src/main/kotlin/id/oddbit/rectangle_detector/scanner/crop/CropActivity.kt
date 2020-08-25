package id.oddbit.rectangle_detector.scanner.crop

import android.app.Activity
import android.content.Intent
import android.widget.ImageView
import id.oddbit.rectangle_detector.scanner.base.BaseActivity
import id.oddbit.rectangle_detector.scanner.view.PaperRectangle
import kotlinx.android.synthetic.main.activity_crop.*
import oddbit.rectangle_detector.rectangle_detector.R

class CropActivity : BaseActivity(), ICropView.Proxy {

    private lateinit var mPresenter: CropPresenter

    override fun prepare() {
        crop.setOnClickListener { mPresenter.crop() }
        enhance.setOnClickListener { mPresenter.enhance() }
        save.setOnClickListener { mPresenter.save() }
    }

    override fun provideContentViewId(): Int = R.layout.activity_crop


    override fun initPresenter() {
        mPresenter = CropPresenter(this, this)
    }

    override fun getPaper(): ImageView = paper

    override fun getPaperRect(): PaperRectangle = paper_rect

    override fun getCroppedPaper(): ImageView = picture_cropped

    override fun setCroppedResult(path: String) {
        val resultIntent = Intent()

        resultIntent.putExtra("CroppedPath", path)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}