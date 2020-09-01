package id.oddbit.rectangle_detector.scanner.utility;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;

public class Utility {
    private static String TAG = "Utility";

    public static boolean isEmptyOrNull(String value) {
        return (value == null || value.isEmpty());
    }

    public static void showMessage(Context activity, String message) {
        Toast toast = Toast.makeText(activity, message, Toast.LENGTH_SHORT);
        View view = toast.getView();
        toast.show();
    }

    public static int[] getOptimalDimensions(float mediaWidth, float mediaHeight, int width, int height) {
        int layoutWidth = width;
        int layoutHeight = height;
        float ratioWidth = layoutWidth / mediaWidth;
        float ratioHeight = layoutHeight / mediaHeight;
        float aspectRatio = mediaWidth / mediaHeight;
        if (ratioWidth > ratioHeight) {
            layoutWidth = (int) (layoutHeight * aspectRatio);
        } else {
            layoutHeight = (int) (layoutWidth / aspectRatio);
        }
        Log.i(TAG, "layoutWidth: " + layoutWidth);
        Log.i(TAG, "layoutHeight: " + layoutHeight);
        Log.i(TAG, "aspectRatio: " + aspectRatio);
        return new int[]{layoutWidth, layoutHeight};
    }
    public static byte[] rotateImageData(Activity activity, byte[] data, int cameraId) throws Exception {
        Bitmap imageBitmap = null;
        // COnverting ByteArray to Bitmap - >Rotate and Convert back to Data
        if (data != null) {
            imageBitmap = BitmapFactory.decodeByteArray(data, 0, (data != null) ? data.length : 0);
            if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                Matrix mtx = new Matrix();
                int cameraEyeValue = setPhotoOrientation(activity, cameraId); // CameraID = 1 : front 0:back
                if (cameraId == 1) { // As Front camera is Mirrored so Fliping the Orientation
                    if (cameraEyeValue == 270) {
                        mtx.postRotate(90);
                    } else if (cameraEyeValue == 90) {
                        mtx.postRotate(270);
                    }
                } else {
                    mtx.postRotate(cameraEyeValue); // cameraEyeValue is default to Display Rotation
                }
                imageBitmap = Bitmap.createBitmap(imageBitmap, 0, 0, imageBitmap.getWidth(), imageBitmap.getHeight(), mtx, true);
            } else {// LANDSCAPE MODE
                //No need to reverse width and height
                Bitmap scaled = Bitmap.createScaledBitmap(imageBitmap, imageBitmap.getWidth(), imageBitmap.getHeight(), true);
                imageBitmap = scaled;
            }
        }
        // Converting the Die photo to Bitmap
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        return stream.toByteArray();
    }
    private static int setPhotoOrientation(Activity activity, int cameraId) {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        // do something for phones running an SDK before lollipop
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360; // compensate the mirror
        } else { // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }

        return result;
    }

    public static File createExternalDirectory(String folder) {
        String var1 = getAppDirectory() + "/" + folder;
        File var2 = new File(var1);
        if (!var2.exists()) {
            var2.mkdir();
        }

        return var2;
    }

    public static String getAppDirectory() {
        String var0 = Environment.getExternalStorageDirectory() + "/CameraPreviewExample";
        File var1 = new File(var0);
        if (!var1.exists()) {
            var1.mkdir();
        }

        return var1.toString();
    }
}
