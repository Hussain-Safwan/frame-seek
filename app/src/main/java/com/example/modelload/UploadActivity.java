package com.example.modelload;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

public class UploadActivity extends AppCompatActivity {

    private LinearLayout uploadLayout, uploadingLayout, uploadCompletedLayout;
    private ProgressBar uploadProgress;
    private Button nextButton;
    private View loadingOverlay;
    private Uri uploadVideoUri;
    private TextView uploadingVideoFilename, uploadedVideoFilename;
    private Map<Long, float[]> frameEmbedall;
    private Map<String, Bitmap>bitmapList;
    private Module imageEncoder;
    private Module textEncoder;
    private DBHandler dbHandler;

    private BPEToken tokenizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);
        dbHandler = new DBHandler(UploadActivity.this);
        Log.d("video", "OnCreate UploadActivity");

        uploadLayout = findViewById(R.id.upload_video_layout);
        uploadingLayout = findViewById(R.id.uploading_layout);
        uploadCompletedLayout = findViewById(R.id.upload_complete_layout);
        nextButton = findViewById(R.id.next_button);
        loadingOverlay = findViewById(R.id.loading_overlay);
        uploadingVideoFilename = findViewById(R.id.uploading_video_filename);
        uploadedVideoFilename = findViewById(R.id.uploaded_video_filename);
        uploadProgress = findViewById(R.id.video_upload_progressbar);

        try {
            imageEncoder = Module.load(assetFilePath(this, "clip_image_encoder.pt"));
            Log.d("debug","model loaded successfully");
            Toast.makeText(this,"model loaded successfully",Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e("debug", "Error loading models", e);
        }

        uploadLayout.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("video/*");
            startActivityForResult(intent, 1);
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            // Enable Next button
            uploadVideoUri = data.getData();
            Log.d("VIDEO", String.valueOf(uploadVideoUri));
            SharedDataHolder.getInstance().setUploadedVideoUri(uploadVideoUri);
            startUpload(uploadVideoUri);
        }
    }

    private String assetFilePath(android.content.Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }

    public String getFileNameFromUri(Context context, Uri uri) {
        String result = null;

        // Handle content scheme
        if (uri.getScheme().equals("content")) {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }

        // Handle file scheme (e.g., from camera)
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }

        return result;
    }

    private void startUpload(Uri videoUri)
    {
//        String filename = new File(Objects.requireNonNull(videoUri.getPath())).getName();
        String filename = getFileNameFromUri(this, videoUri);
        uploadLayout.setVisibility(View.GONE);
        uploadingLayout.setVisibility(View.VISIBLE);
        uploadingVideoFilename.setText(filename);
        uploadProgress.setProgress(0);

        new Thread(() -> {
            for (int i = 0; i <= 100; i += 5) {
                int progress = i;
                runOnUiThread(() -> uploadProgress.setProgress(progress));
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            runOnUiThread(() -> showCompleteState(filename));
        }).start();
    }

    private void showCompleteState(String filename)
    {
        uploadingLayout.setVisibility(View.GONE);
        uploadCompletedLayout.setVisibility(View.VISIBLE);
        uploadedVideoFilename.setText(filename);
        nextButton.setEnabled(true);
    }
    public void onNextClick(View view) {
        try {
            Log.d("debug", "start embedding...");
            frameEmbedall = new HashMap<>();
            bitmapList = new HashMap<>();
            EmbeddingModel frameEmbedding = dbHandler.getSingleEmbedding(uploadVideoUri.toString());
            if (frameEmbedding != null) {
                for (int i=0; i<frameEmbedding.getTimestamps().length; i++) {
                    long timestamp = frameEmbedding.getTimestamps()[i];
                    float[] embedding = frameEmbedding.getEmbedding()[i];
                    frameEmbedall.put(timestamp, embedding);

                    Toast.makeText(this,"Proceeding with precomputed embeddings",Toast.LENGTH_SHORT).show();

                    runThread();
                }
            } else {
                extractFrames(uploadVideoUri);
                Toast.makeText(this,"Frames successfully embedded",Toast.LENGTH_SHORT).show();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void extractFrames(Uri videoUri) throws IOException {
        loadingOverlay.setVisibility(View.VISIBLE);
        nextButton.setEnabled(false);

        new Thread(() -> {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(this, videoUri);

                String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                long videoDurationMillis = Long.parseLong(time);

                long interval = 1000000;
                int index = 0;
                int frameCount = (int) ((videoDurationMillis*1000/interval)+1);
                long[] timestamps = new long[frameCount];
                float[][] embeddings = new float[frameCount][];

                for(long timstamp = 0; timstamp<videoDurationMillis*1000; timstamp+=interval)
                {
                    Bitmap bitmap = retriever.getFrameAtTime(timstamp, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    String filename = "frame_"+ (timstamp/1000000) +"s.jpg";
                    if(bitmap!= null)
                    {

                        float []embedding =encodeImage(bitmap);
                        frameEmbedall.put(timstamp/1000000, embedding);
                        timestamps[index] = timstamp/1000000;
                        embeddings[index] = embedding;
                        index++;
                    }
                    else Log.d("debug","bitmap got null for :"+filename);
                }
                Log.d("debug", "embedding size: "+embeddings.length+", "+embeddings[0].length);
                dbHandler.addNewEmbedding(
                        videoUri.toString(),
                        timestamps,
                        embeddings
                );

                try {
                    retriever.release();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                runThread();

        }).start();
    }

    private void runThread() {
        runOnUiThread(() -> {
            loadingOverlay.setVisibility(View.GONE);
            nextButton.setEnabled(true);

            SharedDataHolder.getInstance().setDataMap(frameEmbedall);
            // Navigate to video player
            Intent intent = new Intent(UploadActivity.this, VideoPlayerActivity.class);
            intent.putExtra("VIDEO_URI", uploadVideoUri.toString());
            //                intent.putExtra("TOKENIZER", (Serializable) tokenizer);
            //                intent.putExtra("TEXTENCODER", (Serializable) textEncoder);

            startActivity(intent);
        });
    }

    private float[] encodeImage(android.graphics.Bitmap bitmap) {
        // Resize to 224x224 as required by CLIP
        android.graphics.Bitmap resizedBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, 224, 224, true);

        org.pytorch.Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                resizedBitmap,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                TensorImageUtils.TORCHVISION_NORM_STD_RGB
        );

        IValue output = imageEncoder.forward(IValue.from(inputTensor));
        float[] features = output.toTensor().getDataAsFloatArray();

        // Normalize features
        float norm = 0;
        for (float f : features) {
            norm += f * f;
        }
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < features.length; i++) {
            features[i] /= norm;
        }

        return features;
    }

    public void resetState(View view) {
        uploadCompletedLayout.setVisibility(View.GONE);
        uploadLayout.setVisibility(View.VISIBLE);
        uploadProgress.setProgress(0);
        nextButton.setEnabled(false);
    }
}