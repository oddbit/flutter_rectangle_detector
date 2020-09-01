package id.oddbit.rectangle_detector.scanner.camera_preview;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import id.oddbit.rectangle_detector.scanner.utility.Utility;
import id.oddbit.rectangle_detector.scanner.view.PaperRectangle;
import static id.oddbit.rectangle_detector.scanner.constant.Constant.INPUT_HEIGHT;
import static id.oddbit.rectangle_detector.scanner.constant.Constant.INPUT_WIDTH;

public class CameraPreviewViewModel implements CameraPreviewInterface {

    private Camera mCamera;
    private String TAG = "FullScreenPreviewViewModel";
    private CameraPreview maPreview;
    private Context context;
    private FrameLayout flCameraPreview;
    private ImageView ivCapture;
    private PaperRectangle paper_rect;
    private Camera.PictureCallback pictureCallback;


    public CameraPreviewViewModel(Context context, FrameLayout flCameraPreview, ImageView ivCapture, PaperRectangle paper_rect, Camera mCamera, Camera.PictureCallback pictureCallback) {
        this.context = context;
        this.flCameraPreview = flCameraPreview;
        this.ivCapture = ivCapture;
        this.paper_rect = paper_rect;
        this.mCamera = mCamera;
        this.pictureCallback = pictureCallback;
    }

    public void startCameraPreview() {
        try {
            Bundle extras = ((Activity) context).getIntent().getExtras();
            long inputWidth = 0;
            long inputHeight = 0;
            if (extras != null) {
                inputWidth=extras.getInt(INPUT_WIDTH,0);
                inputHeight=extras.getInt(INPUT_HEIGHT,0);
            }

            maPreview = new CameraPreview(context, mCamera, this);
            flCameraPreview.addView(maPreview);

            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) maPreview.getLayoutParams();
            params.gravity = Gravity.CENTER;
            if(inputWidth>0&&inputHeight>0){
                setLayoutWH(inputWidth,inputHeight);
            }
        } catch (Exception exc) {
            Log.d(TAG, "startCameraPreview", exc);
        }
    }

    private void setLayoutWH( long inputWidth,long inputHeight) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int mScreenWidth = displayMetrics.widthPixels;
        int mScreenHeight = displayMetrics.heightPixels;
        int width = (int) (displayMetrics.widthPixels *((float)inputWidth/100));
        int height = (int) (displayMetrics.heightPixels * ((float)inputHeight/100));
        int optimalSize[] = Utility.getOptimalDimensions(mScreenWidth
                , mScreenHeight
                , width
                , height);
        flCameraPreview.getLayoutParams().width = optimalSize[0];
        flCameraPreview.getLayoutParams().height = optimalSize[1];
    }

    public void captureImage() {
        ivCapture.setOnClickListener((View view) -> {
            mCamera.takePicture(null, null, pictureCallback);
        });
    }

    @Override
    public void onCameraViewSizeChange(int width, int height) {
        paper_rect.getLayoutParams().width = width;
        paper_rect.getLayoutParams().height = height;
    }
}
