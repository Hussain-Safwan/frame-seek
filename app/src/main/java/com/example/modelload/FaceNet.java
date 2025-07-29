package com.example.modelload;

import android.content.Context;
import android.graphics.Bitmap;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.Callable;


public class FaceNet {

    private static final int IMG_SIZE = 160;
    private static final int EMBEDDING_DIM = 512;

    private Interpreter interpreter;
    private final ImageProcessor imageTensorProcessor;

    public FaceNet(Context context, boolean useGpu, boolean useXNNPack) throws IOException {
        Interpreter.Options interpreterOptions = new Interpreter.Options();

        if (useGpu) {
            CompatibilityList compatList = new CompatibilityList();
            if (compatList.isDelegateSupportedOnThisDevice()) {
                GpuDelegate delegate = new GpuDelegate(compatList.getBestOptionsForThisDevice());
                interpreterOptions.addDelegate(delegate);
            }
        } else {
            interpreterOptions.setNumThreads(4);
        }

        interpreterOptions.setUseXNNPACK(useXNNPack);
        interpreterOptions.setUseNNAPI(true);

        interpreter = new Interpreter(FileUtil.loadMappedFile(context, "facenet_512.tflite"), interpreterOptions);

        imageTensorProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(IMG_SIZE, IMG_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(new StandardizeOp())
                .build();
    }

    public float[] getFaceEmbedding(Bitmap image) {
        ByteBuffer input;
        try {
            input = convertBitmapToBuffer(image);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return runFaceNet(input)[0];
    }

    private float[][] runFaceNet(Object input) {
        float[][] output = new float[1][EMBEDDING_DIM];
        interpreter.run(input, output);
        return output;
    }

    private ByteBuffer convertBitmapToBuffer(Bitmap bitmap) {
        TensorImage tensorImage = TensorImage.fromBitmap(bitmap);
        TensorImage processedImage = imageTensorProcessor.process(tensorImage);
        return processedImage.getBuffer();
    }

    public static class StandardizeOp implements TensorOperator {

        @Override
        public TensorBuffer apply(TensorBuffer input) {
            float[] pixels = input.getFloatArray();

            float mean = 0f;
            for (float pixel : pixels) mean += pixel;
            mean /= pixels.length;

            float variance = 0f;
            for (float pixel : pixels) {
                variance += (pixel - mean) * (pixel - mean);
            }
            float std = (float) Math.sqrt(variance / pixels.length);
            std = Math.max(std, 1f / (float) Math.sqrt(pixels.length));

            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = (pixels[i] - mean) / std;
            }

            TensorBuffer output = TensorBufferFloat.createFixedSize(input.getShape(), DataType.FLOAT32);
            output.loadArray(pixels);
            return output;
        }
    }
}