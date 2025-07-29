package com.example.modelload;

import android.annotation.SuppressLint;
import android.app.ComponentCaller;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ui.PlayerView;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VideoPlayerActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ExoPlayer exoPlayer;
    private EditText searchBox;
    private RecyclerView cardList;

    private ImageView submitQuery, imageSearch;
    private ProgressBar progressBar;

    private Map<Long, float[]> frameEmbedall;

    private static final int CONTEXT_LENGTH = 77;
    private static final int REQUEST_CODE_PICK_IMAGE = 101;
    private BPEToken tokenizer;
    private static final int SOT_TOKEN = 49406;
    private static final int EOT_TOKEN = 49407;

    private Module textEncoder, facenetModel, retinafaceModel;

    List<TimeFrame> timeFrames;
    TimeFrameAdapter adapter;
    Uri queryImageUri;
    private Bitmap queryImage;
    private Map<Long, float[]> knownEmbeddingsArr;

    private FaceNet faceNet;
    private MediapipeFaceDetector mediapipeFaceDetector;
    private float[] targetFaceEmbedding;
    private Bitmap croppedTargetFace;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        mediapipeFaceDetector = new MediapipeFaceDetector(this);
        try {
            faceNet = new FaceNet(this, true, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        playerView = findViewById(R.id.video_player);
        searchBox = findViewById(R.id.search_box);
        submitQuery = findViewById(R.id.submitQuery);
        cardList = findViewById(R.id.card_list);
        progressBar = findViewById(R.id.queryLoadingBar);
        imageSearch = findViewById(R.id.imageSearch);

        String videoUriString = getIntent().getStringExtra("VIDEO_URI");
        frameEmbedall = SharedDataHolder.getInstance().getDataMap();
        try {
            tokenizer = new BPEToken(assetFilePath(this, "texttokenize.txt"));
            textEncoder = Module.load(assetFilePath(this,"clip_text.pt"));
//            facenetModel = Module.load(assetFilePath(this,"facenet_scripted.pt"));
//            retinafaceModel = Module.load(assetFilePath(this, "retinaface_r34_traced.pt"));
            Log.d("debug", "Models loaded successfully from VideoPlayerActivity");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        if (videoUriString == null || videoUriString.isEmpty())
        {
            Toast.makeText(this, "No video uri found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Uri videoUri = Uri.parse(videoUriString);

        // Initialize ExoPlayer
        exoPlayer = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(exoPlayer);

        // Load a sample video URL (replace with your video URI)
        MediaItem mediaItem = MediaItem.fromUri(videoUri);
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
//        exoPlayer.play();

        // Configure RecyclerView
        cardList.setLayoutManager(new LinearLayoutManager(this));
        timeFrames = new ArrayList<>();
        adapter = new TimeFrameAdapter(timeFrames, exoPlayer);
        cardList.setAdapter(adapter);
        
        submitQuery.setOnClickListener(v -> {
            processQuery_v2();
        });

        imageSearch.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
        });

        // Handle search box click
//        searchBox.setOnEditorActionListener((v, actionId, event) -> {
//            String query = searchBox.getText().toString();
//            if (!query.isEmpty()) {
//                // Simulate API call (replace with actual API request)
////                simulateApiCall(query);
//                timeFrames.clear();
//                timeFrames.add(new TimeFrame("Label 1", 0));
//                timeFrames.add(new TimeFrame("Label 2", 300000));
//                timeFrames.add(new TimeFrame("Label 3", 600000));
//
//                adapter.notifyDataSetChanged();
//
//                Toast.makeText(this, "Search query: " + query, Toast.LENGTH_SHORT).show();
//            }
//            return true;
//        });


    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        targetFaceEmbedding = new float[0];
        croppedTargetFace = null;
        Log.d("debug", "onActivityResult");

        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null)
        {
            queryImageUri = data.getData();
            try {
                queryImage = queryImageUri != null ? MediaStore.Images.Media.getBitmap(this.getContentResolver(), queryImageUri) : null;
                Log.d("debug", "onActivityResult 2");
                croppedTargetFace = mediapipeFaceDetector.getCroppedFace(queryImageUri);
                targetFaceEmbedding = faceNet.getFaceEmbedding(croppedTargetFace);
            } catch (IOException e) {
                Log.d("debug", String.valueOf(e));
                throw new RuntimeException(e);
            }
            Log.d("debug", "image loaded successfully");
//            imageSearch.setVisibility(View.VISIBLE);
//            submitQuery.setVisibility(View.VISIBLE);
//            progressBar.setVisibility(View.GONE);
            if (queryImage != null)
            {
                extractFramesAndEmbed();
            }
        }
    }

    private void extractFramesAndEmbed()
    {
        knownEmbeddingsArr = new HashMap<>();
        timeFrames.clear();
        imageSearch.setVisibility(View.GONE);
        submitQuery.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        searchBox.setEnabled(false);
        new Thread(() -> {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            Uri videoUri = SharedDataHolder.getInstance().getUploadedVideoUri();
            retriever.setDataSource(this, videoUri);

            String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long videoDurationMillis = Long.parseLong(time);

            long interval = 1000000;
            for(long timstamp = 0; timstamp<videoDurationMillis*1000;timstamp+=interval)
            {
                Bitmap bitmap = retriever.getFrameAtTime(timstamp, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (bitmap != null)
                {
                    Log.d("debug", "onActivityResult 3");
                    List<Pair<Bitmap, Rect>> faceDetectionResult = mediapipeFaceDetector.getAllCroppedFaces(bitmap);
                    Log.d("debug", "onActivityResult 4");
                    for (Pair<Bitmap, Rect> result : faceDetectionResult)
                    {
                        Bitmap croppedBitmap = result.first.copy(Bitmap.Config.ARGB_8888, true);
                        Log.d("debug", "onActivityResult 5");
                        float[] embedding = faceNet.getFaceEmbedding(croppedBitmap);
                        float distance = cosineDistance(embedding, targetFaceEmbedding);
                        if (distance > 0.6)
                        {
//                            knownEmbeddingsArr.put(timstamp/1000000, embedding);
                            timeFrames.add(new TimeFrame("Face match", timstamp/1000000, queryImageUri));
                        }
                    }
//                    List<int[]> boxes = detectFacesWithRetinaFace(bitmap);
//                    if (boxes.size() > 0) {
//                        int[] box = boxes.get(0); // Use the first detected face
//                        Bitmap faceBitmap = Bitmap.createBitmap(bitmap, box[0], box[1], box[2] - box[0], box[3] - box[1]);
//                        Bitmap resized = Bitmap.createScaledBitmap(faceBitmap, 160, 160, true);
//                        float[] embedding = normalizeEmbedding(generateFaceEmbedding(resized));
//                        knownEmbeddingsArr.put(timstamp/1000000, embedding);
//                    } else {
//                        Log.w("debug", "No face detected ");
//                        knownEmbeddingsArr.put(timstamp/1000000, null);
//                    }
                }
                else{
//                    knownEmbeddingsArr.put(timstamp/1000000, null);
                    Log.d("debug", "Not match");
                }
            }
            try {
                retriever.release();
            } catch (IOException e) {
                Log.d("debug", String.valueOf(e));
                throw new RuntimeException(e);
            }

            runOnUiThread(() -> {
//                processingImageAndMatch(videoDurationMillis);
                adapter.notifyDataSetChanged();
                imageSearch.setVisibility(View.VISIBLE);
                submitQuery.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                searchBox.setEnabled(true);
            });
        }).start();
    }

    private void processingImageAndMatch(long videoFrames)
    {
        // Load the query image
        Log.d("debug", "Loading query image");
        if (queryImage == null) {
            Log.e("debug", "Failed to load query image");
            Toast.makeText(this, "Failed to load query image", Toast.LENGTH_SHORT).show();
            return;
        }

        // Detect face in query image
        Log.d("debug", "Detecting faces in query image");
        List<int[]> queryBoxes = detectFacesWithRetinaFace(queryImage);
        Log.d("debug", "Found " + queryBoxes.size() + " faces in query image");


        final float[][] queryEmbeddingArr = new float[1][];
        if (queryBoxes.size() > 0) {
            Log.d("debug", "Processing detected face in query image");
            int[] box = queryBoxes.get(0);
            Bitmap faceBitmap = Bitmap.createBitmap(queryImage, box[0], box[1], box[2] - box[0], box[3] - box[1]);
            Bitmap resized = Bitmap.createScaledBitmap(faceBitmap, 160, 160, true);
            queryEmbeddingArr[0] = normalizeEmbedding(generateFaceEmbedding(resized));
        } else {
            Log.w("debug", "No face detected in query image");
            queryEmbeddingArr[0] = null;
        }

        // Compare and show best match
        if (queryEmbeddingArr[0] != null) {
            Log.d("debug","now camparison starts");
            compareAndShowBestMatch(queryEmbeddingArr[0], knownEmbeddingsArr, videoFrames);
        } else {
            Toast.makeText(this, "No face detected in query image", Toast.LENGTH_SHORT).show();
        }
    }

    private List<int[]> detectFacesWithRetinaFace(Bitmap bitmap) {
        List<int[]> faceBoxes = new ArrayList<>();
        try {
            if (retinafaceModel == null) {
                Log.e("debug", "RetinaFace model not loaded");
                return faceBoxes;
            }

            // Preprocess: resize to 640x640, convert to tensor, normalize
            Bitmap resized = Bitmap.createScaledBitmap(bitmap, 640, 640, true);
            Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                    resized,
                    TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                    TensorImageUtils.TORCHVISION_NORM_STD_RGB
            );

            // Run model
            IValue output = retinafaceModel.forward(IValue.from(inputTensor));

            if (!output.isTuple() || output.toTuple().length < 2) {
                Log.e("debug", "Unexpected RetinaFace output");
                return faceBoxes;
            }

            IValue[] outTuple = output.toTuple();
            Tensor locTensor = outTuple[0].toTensor(); // locations
            Tensor confTensor = outTuple[1].toTensor(); // confidences

            // Get tensor shapes
            long[] locShape = locTensor.shape();
            long[] confShape = confTensor.shape();

            //Log.d("debug", "Location tensor shape: " + java.util.Arrays.toString(locShape));
            //Log.d("debug", "Confidence tensor shape: " + java.util.Arrays.toString(confShape));

            float[] loc = locTensor.getDataAsFloatArray();
            float[] conf = confTensor.getDataAsFloatArray();

            // Calculate number of detections
            int numDetections = (int) (loc.length / 4); // Each detection has 4 values (x1,y1,x2,y2)

            //Log.d("debug", "Number of potential detections: " + numDetections);
            //Log.d("debug", "Raw location values length: " + loc.length);
            //Log.d("debug", "Raw confidence values length: " + conf.length);

            // Process each detection
            for (int i = 0; i < numDetections && i < conf.length; i++) {
                if (conf[i] > 0.5f) { // Confidence threshold
                    // Get coordinates for this detection
                    int baseIdx = i * 4;
                    if (baseIdx + 3 >= loc.length) {
                        Log.e("debug", "Invalid location array index: " + baseIdx);
                        continue;
                    }

                    // Assuming loc array contains [x1, y1, x2, y2] for each detection
                    int x1 = (int) (loc[baseIdx] * bitmap.getWidth());
                    int y1 = (int) (loc[baseIdx + 1] * bitmap.getHeight());
                    int x2 = (int) (loc[baseIdx + 2] * bitmap.getWidth());
                    int y2 = (int) (loc[baseIdx + 3] * bitmap.getHeight());

                    // Ensure coordinates are within image bounds
                    x1 = Math.max(0, Math.min(x1, bitmap.getWidth() - 1));
                    y1 = Math.max(0, Math.min(y1, bitmap.getHeight() - 1));
                    x2 = Math.max(0, Math.min(x2, bitmap.getWidth() - 1));
                    y2 = Math.max(0, Math.min(y2, bitmap.getHeight() - 1));

                    // Only add if the box has valid dimensions
                    if (x2 > x1 && y2 > y1) {
                        faceBoxes.add(new int[]{x1, y1, x2, y2});
                        //Log.d("debug", "Face detected at: [" + x1 + "," + y1 + "," + x2 + "," + y2 + "] with confidence: " + conf[i]);
                    }
                }
            }

//            Log.d("debug", "Number of faces detected: " + faceBoxes.size());
            return faceBoxes;
        } catch (Exception e) {
            Log.e("debug", "Error in detectFacesWithRetinaFace", e);
            return faceBoxes;
        }
    }

    private float[] generateFaceEmbedding(Bitmap bitmap) {
        // Convert Bitmap to Tensor
        Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(bitmap,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                TensorImageUtils.TORCHVISION_NORM_STD_RGB);

        // Run the model
        Tensor outputTensor = facenetModel.forward(IValue.from(inputTensor)).toTensor();

        // Convert output Tensor to float array
        return outputTensor.getDataAsFloatArray();
    }

    private float computeL2(float[] a, float[] b) {
        float sum = 0f;
        for (int i = 0; i < a.length; i++) {
            float diff = a[i] - b[i];
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }

    private float cosineDistance(float[] x1, float[] x2) {
        float mag1 = 0f, mag2 = 0f, product = 0f;
        for (int i = 0; i < x1.length; i++) {
            mag1 += x1[i] * x1[i];
            mag2 += x2[i] * x2[i];
            product += x1[i] * x2[i];
        }
        mag1 = (float) Math.sqrt(mag1);
        mag2 = (float) Math.sqrt(mag2);
        return product / (mag1 * mag2);
    }

    private float[] normalizeEmbedding(float[]embed)
    {
        float norm=0f;
        for(float v:embed)norm+=v*v;
        norm=(float)Math.sqrt(norm);
        for(int i=0;i<embed.length;i++){
            embed[i]/=norm;
        }
        return embed;
    }

    private void compareAndShowBestMatch(float[] queryEmbedding, Map<Long, float[]> knownEmbeddings, long videoFrames) {
        Float minDistance = Float.MAX_VALUE;
        Map<Long, Float> distaceArray = new HashMap<>();
        long bestMatch = 0;

        try {
            if (knownEmbeddings.size() != 0) {
                for (Map.Entry<Long, float[]> entry : knownEmbeddings.entrySet()) {
                    float[] knownEmbedding = entry.getValue();
                    if (knownEmbedding == null || queryEmbedding == null) continue;
                    float distance = computeL2(queryEmbedding, knownEmbedding);
                    distaceArray.put(entry.getKey(), distance);
                    Log.d("debug", "distance :" + distance);
                    if (distance < minDistance) {
                        minDistance = distance;
                        bestMatch = entry.getKey();
                    }
                }
                Log.d("debug", "Distance calculation completed");
                timeFrames.clear();
                timeFrames.add(new TimeFrame("Face match", bestMatch, queryImageUri));
                adapter.notifyDataSetChanged();
//                adapter.notifyItemInserted(timeFrames.size() - 1);
            }
        }
        catch (Exception e) {
            Log.e("debug", "Error processing query image", e);
//            resultTextView.setText("Error processing query: " + e.getMessage());
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

    private void processQuery_v2() {
        String selectedQuery = searchBox.getText().toString().trim().toLowerCase();
//        Toast.makeText(this,"proces start for query: "+selectedQuery,Toast.LENGTH_SHORT).show();

        try {

            float[] textEmbedding = encodeText(selectedQuery);
            Log.d("debug","text embedding created for query: " + selectedQuery);
//            Toast.makeText(this,"text embedding created for query: " + selectedQuery,Toast.LENGTH_SHORT).show();

            if(frameEmbedall.size()==0)
            {
                Log.d("debug", "frame embedding got empty");
            }
            Log.d("debug", "image embed size: "+frameEmbedall.entrySet().size());
            long matchedTimeStamp = findBestMatchingFrame(textEmbedding);
            Log.d("debug","found best image path: "+matchedTimeStamp);
//            Toast.makeText(this,"found best image path: "+matchedTimeStamp,Toast.LENGTH_SHORT).show();

            timeFrames.clear();
            timeFrames.add(new TimeFrame(selectedQuery, matchedTimeStamp, null));
            adapter.notifyDataSetChanged();

//            displayBestFrame(this,bestImgPath);
            Log.d("debug", "best image displayed");
        } catch (Exception e) {
            Log.e("debug", "Error processing query", e);
//            resultTextView.setText("Error processing query: " + e.getMessage());
        }
    }

    public Long findBestMatchingFrame(float[] textEmbeds)
    {
        float bestScpre = 0;
        long matchedTimeStamp = 0;
        for(Map.Entry<Long, float[]>entry : frameEmbedall.entrySet())
        {
            float [] imgEmbd= entry.getValue();
            float score = cosineSimilarity(imgEmbd,textEmbeds);
            Log.d("debug", "frame score for: "+entry.getKey()+","+ score);
            if(score>bestScpre)
            {
                bestScpre = score;
                matchedTimeStamp = entry.getKey();
            }
        }
        Log.d("debug", "Best frame: "+ matchedTimeStamp+", score: "+bestScpre);
//        Toast.makeText(this,"Best frame: "+ matchedTimeStamp+", score: "+bestScpre,Toast.LENGTH_SHORT).show();

        return matchedTimeStamp;
    }

    private float[] encodeText(String query) {
        // Hardcoded token values for each query
        List<Integer> tokens = tokenize(query, false);
        Log.d("[debug]- for "+query, tokens.toString());
        long[] tokenValues = tokens.stream().mapToLong(Integer::longValue).toArray();

        // Create tensor from the hardcoded tokens
        org.pytorch.Tensor inputTensor = org.pytorch.Tensor.fromBlob(
                tokenValues,
                new long[]{1, 77}
        );

        IValue output = textEncoder.forward(IValue.from(inputTensor));
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

    // Method to compute cosine similarity
    private float cosineSimilarity(float[] a, float[] b) {
        float dotProduct = 0;
        float normA = 0;
        float normB = 0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        return dotProduct / ((float) Math.sqrt(normA) * (float) Math.sqrt(normB));
    }

    public List<Integer> tokenize(String text, boolean truncate) {
        // Encode the text
        List<Integer> tokens = new ArrayList<>();
        tokens.add(SOT_TOKEN);
        tokens.addAll(tokenizer.encode(text));
        tokens.add(EOT_TOKEN);

        // Handle truncation if needed
        if (tokens.size() > CONTEXT_LENGTH) {
            if (truncate) {
                tokens = tokens.subList(0, CONTEXT_LENGTH);
                tokens.set(CONTEXT_LENGTH - 1, EOT_TOKEN);
            } else {
                throw new RuntimeException("Input text is too long for context length " + CONTEXT_LENGTH);
            }
        }

        // Pad the sequence to CONTEXT_LENGTH if needed
        while (tokens.size() < CONTEXT_LENGTH) {
            tokens.add(0); // Pad with zeros
        }

        return tokens;
    }

    private void simulateApiCall(String query) {
        // Simulate API response with dummy data

    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        exoPlayer.release();
    }
}