package com.example.modelload;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FrameExtractor {


    public static  void extractFrames(Context context, Uri videoUri, String videoBaseName) throws IOException {

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(context, videoUri);

        String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        long videoDurationMillis = Long.parseLong(time);

        long interval = 1000000;
        for(long timstamp = 0; timstamp<videoDurationMillis*1000;timstamp+=interval)
        {
            Bitmap bitmap = retriever.getFrameAtTime(timstamp, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if(bitmap!= null)
            {
                saveFrame(context,bitmap,timstamp,videoBaseName);
            }
        }
        retriever.release();
    }

    private static void saveFrame(Context context, Bitmap bitmap, long timstamp, String videoBaseName) {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), videoBaseName);
        if(!dir.exists())dir.mkdirs();

        String filename = "frame_"+ (timstamp/1000000) +"s.jpg";
        File file = new File(dir, filename);
        try(FileOutputStream out = new FileOutputStream(file)){
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        } catch (IOException e) {
            Log.d("[debug]", "fileerror: "+e.getMessage());

        }
    }
}
