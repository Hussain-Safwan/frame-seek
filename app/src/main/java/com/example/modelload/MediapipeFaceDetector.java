package com.example.modelload;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import androidx.exifinterface.media.ExifInterface;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.tasks.components.containers.Detection;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector;
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector.FaceDetectorOptions;
//import com.ml.shubham0204.facenet_android.domain.AppException;
//import com.ml.shubham0204.facenet_android.domain.ErrorCode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MediapipeFaceDetector {
    private static final String MODEL_NAME = "blaze_face_short_range.tflite";
    private static final String TAG = "debug";


    private final FaceDetector faceDetector;
    private final Context context;

    public MediapipeFaceDetector(Context context) {
        this.context = context;

        BaseOptions baseOptions = BaseOptions.builder().setModelAssetPath(MODEL_NAME).build();

        FaceDetectorOptions options = FaceDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .build();

        faceDetector = FaceDetector.createFromOptions(context, options);
    }

    public interface FaceCallback {
        void onSuccess(Bitmap croppedFace);
        void onFailure(Exception exception);
    }

    public Bitmap getCroppedFace(Uri imageUri) {
            try {
                InputStream imageInputStream = context.getContentResolver().openInputStream(imageUri);
                if (imageInputStream == null) {
                    Log.d(TAG, "imageInputStream is null");
                    return null;
                }
                Bitmap imageBitmap = BitmapFactory.decodeStream(imageInputStream);
                imageInputStream.close();

                // Open again for reading EXIF
                imageInputStream = context.getContentResolver().openInputStream(imageUri);
                if (imageInputStream == null) {
                    Log.d(TAG, "imageInputStream is null exif");
                    return null;
                }
                ExifInterface exif = new ExifInterface(imageInputStream);
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
                imageBitmap = rotateIfRequired(imageBitmap, orientation);
                imageInputStream.close();

                List<Detection> detections = faceDetector.detect(new BitmapImageBuilder(imageBitmap).build()).detections();
                Log.d(TAG, detections.toString());

                if (detections.size() > 1) {
                    Log.d(TAG, "Multiple face detected");
                    return null;
                } else if (detections.isEmpty()) {
                    Log.d(TAG, "No face detected");
                    return null;
                } else {
                    RectF rectF = detections.get(0).boundingBox();
                    Rect rect = new Rect();
                    rectF.round(rect);
                    if (validateRect(imageBitmap, rect)) {
                        Bitmap cropped = Bitmap.createBitmap(imageBitmap, rect.left, rect.top, rect.width(), rect.height());
                        Log.d(TAG, "detected face and cropped");
                        return cropped;
                    } else {
                        Log.d(TAG, "imageInputStream is null");
                        return null;
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "catch", e);

            }
        return null;
    }

    public List<Pair<Bitmap, Rect>> getAllCroppedFaces(Bitmap frameBitmap) {
        List<Pair<Bitmap, Rect>> result = new ArrayList<>();

        try {
            List<Detection> detections = faceDetector.detect(new BitmapImageBuilder(frameBitmap.copy(Bitmap.Config.ARGB_8888, true)).build()).detections();
            for (Detection detection : detections) {
                RectF rectF = detection.boundingBox();
                Rect rect = new Rect();
                rectF.round(rect);
                if (validateRect(frameBitmap, rect)) {
                    Bitmap cropped = Bitmap.createBitmap(frameBitmap, rect.left, rect.top, rect.width(), rect.height());
                    result.add(new Pair<>(cropped, rect));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    public void saveBitmap(Context context, Bitmap image, String name) {
        try {
            File file = new File(context.getFilesDir(), name + ".png");
            FileOutputStream fos = new FileOutputStream(file);
            image.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
        } catch (Exception ignored) {}
    }

    private Bitmap rotateIfRequired(Bitmap bitmap, int orientation) {
        float degrees = 0f;
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                degrees = 90f;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                degrees = 180f;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                degrees = 270f;
                break;
            default:
                return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
    }

    private boolean validateRect(Bitmap bitmap, Rect boundingBox) {
        return boundingBox.left >= 0 &&
                boundingBox.top >= 0 &&
                (boundingBox.left + boundingBox.width()) < bitmap.getWidth() &&
                (boundingBox.top + boundingBox.height()) < bitmap.getHeight();
    }

//    public static class Pair<F, S> {
//        public final F first;
//        public final S second;
//        public Pair(F first, S second) {
//            this.first = first;
//            this.second = second;
//        }
//    }
}
