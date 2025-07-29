package com.example.modelload;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "[debug]";
    private Module imageEncoder;
    private Module textEncoder;
    private EditText queryEditText;
    private Button submitButton, pickVideoButton;
    private TextView resultTextView;
    private static final int CONTEXT_LENGTH = 77;
    private BPEToken tokenizer;
    private static final int SOT_TOKEN = 49406;
    private static final int EOT_TOKEN = 49407;
    private static final int REQUEST_CODE_PICK_VIDEO = 1001;
    private String videoBaseName = "";

    private Map<String, float[]>frameEmbedall;
    private Map<String, Bitmap>bitmapList;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        Log.d(TAG,"successed in launch");
        
        // Initialize UI elements
        queryEditText = findViewById(R.id.queryEditText);
        submitButton = findViewById(R.id.submitButton);
//        resultTextView = findViewById(R.id.resultTextView);
        pickVideoButton = findViewById(R.id.selectVideoButton);
        pickVideoButton.setOnClickListener(v->openVideoPicker());
        submitButton.setOnClickListener(v->processQuery_v2());

        // Load models
        try {
            tokenizer = new BPEToken(assetFilePath(this, "texttokenize.txt"));
            imageEncoder = Module.load(assetFilePath(this, "clip_image_encoder.pt"));
            textEncoder = Module.load(assetFilePath(this,"clip_text.pt"));
            Log.d(TAG,"model loaded successfully");
            Toast.makeText(this,"model loaded successfully",Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "Error loading models", e);
        }

//        try {
//            String videoPath = assetFilePath(this, "test.mp4");
//            Log.d(TAG, "file loaded from asstes folder: "+videoPath);
//            FrameExtractor.extractFrames(this, videoPath);
//            Log.d(TAG, "frames are saved successfully "+videoPath);
//
//        } catch (IOException e) {
//            Log.e(TAG, "eror: "+e.getMessage());
//        }


//
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
//        });
    }

    private void openVideoPicker() {
        Intent intent =new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_CODE_PICK_VIDEO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_VIDEO)
        {
            if(resultCode == RESULT_OK && data!= null && data.getData()!=null)
            {
                Uri videoUri = data.getData();
                Log.d(TAG, "video selected: "+videoUri.toString());

                videoBaseName = getFileNameFromUri(this, videoUri);
                if(videoBaseName.contains("."))
                {
                    videoBaseName = videoBaseName.substring(0, videoBaseName.lastIndexOf('.'));
                }

                Log.d("[debug]]","videobase name: "+videoBaseName);

                Toast.makeText(this,"video selected: "+videoUri.toString()+","+videoUri.getLastPathSegment(),Toast.LENGTH_SHORT).show();
                try {
                    frameEmbedall = new HashMap<>();
                    bitmapList = new HashMap<>();
                    extractFrames(videoUri,videoBaseName);
                    Toast.makeText(this,"frames embedding succeeded",Toast.LENGTH_SHORT).show();

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }
            else{
                Toast.makeText(this, "no video file selected",Toast.LENGTH_SHORT).show();
            }
        }
    }

    public String getFileNameFromUri(Context context, Uri uri)
    {
        String result = null;
        if(uri.getScheme().equals("content")){
            try(Cursor cursor=context.getContentResolver().query(uri, null, null,null,null))
            {
                if(cursor!=null && cursor.moveToFirst())
                {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if(nameIndex>=0)
                    {
                        result= cursor.getString(nameIndex);
                    }
                }
            }
        }
        if(result == null)
        {
            result=uri.getLastPathSegment();
        }
        return result;
    }

    public void extractFrames(Uri videoUri, String videoBaseName) throws IOException {
        Log.d(TAG, "extractFrames/main");
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(this, videoUri);

        String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        long videoDurationMillis = Long.parseLong(time);

        long interval = 1000000;
        Toast.makeText(this,"frame extracting and creating embedding process starts ",Toast.LENGTH_SHORT).show();
        for(long timstamp = 0; timstamp<videoDurationMillis*1000;timstamp+=interval)
        {
            Bitmap bitmap = retriever.getFrameAtTime(timstamp, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            String filename = "frame_"+ (timstamp/1000000) +"s.jpg";
            if(bitmap!= null)
            {

                float []embedding =encodeImage(bitmap);
                frameEmbedall.put(filename, embedding);
                bitmapList.put(filename,bitmap);
                Toast.makeText(this,"fram embed complete for: "+filename,Toast.LENGTH_SHORT).show();
            }
            else Log.d(TAG,"bitmap got null for :"+filename);
        }
        retriever.release();
    }

    public String findBestMatchingFrame(float[] textEmbeds)
    {
        float bestScpre = 0;
        String bestImgPath = null;
        for(Map.Entry<String, float[]>entry : frameEmbedall.entrySet())
        {
            float [] imgEmbd= entry.getValue();
            float score = cosineSimilarity(imgEmbd,textEmbeds);
            Log.d(TAG, "frame score for: "+entry.getKey()+","+ score);
            if(score>bestScpre)
            {
                bestScpre = score;
                bestImgPath = entry.getKey();
            }
        }
        Log.d(TAG, "Best frame: "+ bestImgPath+", score: "+bestScpre);
        Toast.makeText(this,"Best frame: "+ bestImgPath+", score: "+bestScpre,Toast.LENGTH_SHORT).show();

        return bestImgPath;
    }

    public void displayBestFrame(Context context, String bestImgPath)
    {
        if(bestImgPath!=null)
        {
            Bitmap bestBitmap = bitmapList.get(bestImgPath);
            if(bestBitmap!=null)
            {
                ImageView imageView = ((Activity)context).findViewById(R.id.imageVIew);
                imageView.setImageBitmap(bestBitmap);
                Log.d(TAG,"image displayed succesfully");
            }
        }
    }

    private void processQuery_v2() {
        String selectedQuery = queryEditText.getText().toString().trim().toLowerCase();
        Toast.makeText(this,"proces start for query: "+selectedQuery,Toast.LENGTH_SHORT).show();

        try {

            float[] textEmbedding = encodeText(selectedQuery);
            Log.d(TAG,"text embedding created for query: " + selectedQuery);
            Toast.makeText(this,"text embedding created for query: " + selectedQuery,Toast.LENGTH_SHORT).show();

            if(frameEmbedall.size()==0)
            {
                Log.d("TAG", "frame embedding got empty");
            }
            Log.d(TAG, "image embed size: "+frameEmbedall.entrySet().size());
            String bestImgPath = findBestMatchingFrame(textEmbedding);
            Log.d(TAG,"found best image path: "+bestImgPath);
            Toast.makeText(this,"found best image path: "+bestImgPath,Toast.LENGTH_SHORT).show();

            displayBestFrame(this,bestImgPath);
            Log.d(TAG, "best image displayed");
        } catch (Exception e) {
            Log.e(TAG, "Error processing query", e);
            resultTextView.setText("Error processing query: " + e.getMessage());
        }
    }

    private void processQuery() {
        String selectedQuery = queryEditText.getText().toString().trim().toLowerCase();
        
        // Validate input
        if (!selectedQuery.matches("cat|dog|flower|fruit|horse")) {
            resultTextView.setText("Please enter a valid query: cat, dog, flower, fruit, or horse");
            return;
        }

        try {
            // Load and process images
            android.graphics.Bitmap catBitmap = loadBitmapFromAssets("catte.jpg");
            android.graphics.Bitmap dogBitmap = loadBitmapFromAssets("doge.jpg");
//            android.graphics.Bitmap flowerBitmap = loadBitmapFromAssets("flower.jpg");
//            android.graphics.Bitmap fruitBitmap = loadBitmapFromAssets("fruit.jpg");
//            android.graphics.Bitmap horseBitmap = loadBitmapFromAssets("horse.jpg");

            float[] catEmbedding = encodeImage(catBitmap);
            float[] dogEmbedding = encodeImage(dogBitmap);
//            float[] flowerEmbedding = encodeImage(flowerBitmap);
//            float[] fruitEmbedding = encodeImage(fruitBitmap);
//            float[] horseEmbedding = encodeImage(horseBitmap);
            Log.d(TAG,"encoded all images");

            float[] textEmbedding = encodeText(selectedQuery);
            Log.d(TAG,"text embedding created for query: " + selectedQuery);

            float catScore = cosineSimilarity(catEmbedding, textEmbedding);
            float dogScore = cosineSimilarity(dogEmbedding, textEmbedding);
//            float flowerScore = cosineSimilarity(flowerEmbedding, textEmbedding);
//            float fruitScore = cosineSimilarity(fruitEmbedding, textEmbedding);
//            float horseScore = cosineSimilarity(horseEmbedding, textEmbedding);

            String result = "Query: " + selectedQuery + "\n\n" +
                    "Cat similarity: " + String.format("%.4f", catScore) + "\n" +
                    "Dog similarity: " + String.format("%.4f", dogScore) + "\n";
//                    "Flower similarity: " + String.format("%.4f", flowerScore) + "\n" +
//                    "Fruit similarity: " + String.format("%.4f", fruitScore) + "\n" +
//                    "Horse similarity: " + String.format("%.4f", horseScore);
            
            resultTextView.setText(result);
            Log.d(TAG, result);
        } catch (Exception e) {
            Log.e(TAG, "Error processing query", e);
            resultTextView.setText("Error processing query: " + e.getMessage());
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

    // Method to encode image
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

    // Method to encode text
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

    // Helper to load Bitmap from assets
    private android.graphics.Bitmap loadBitmapFromAssets(String fileName) throws IOException {
        try (InputStream is = getAssets().open(fileName)) {
            return android.graphics.BitmapFactory.decodeStream(is);
        }
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

}